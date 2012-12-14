package com.googlecode.kilimbuilder;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import kilim.KilimException;
import kilim.mirrors.Detector;
import kilim.mirrors.RuntimeClassMirrors;
import kilim.tools.Weaver;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.util.IClassFileReader;

import com.googlecode.kilimbuilder.utils.JDTUtils;
import com.googlecode.kilimbuilder.utils.LogUtils;

@SuppressWarnings("rawtypes")
public class KilimBuilder extends IncrementalProjectBuilder {
	
	public static final String CLASSNAME_ATTRIBUTE= KilimActivator.PLUGIN_ID+".classname";
	
	
	private static final Object __projectAccess= new Object();

	class WeavingVisitor implements IResourceDeltaVisitor, IResourceVisitor {
		
		IProgressMonitor _progressMonitor;
		IProject _project= getProject();
		IJavaProject _javaProject= JDTUtils.getJavaProject(_project);
		ClassLoader _projectClassLoader= JDTUtils.createProjectClassLoader(_javaProject);
		Detector _detector= new Detector(new RuntimeClassMirrors(_projectClassLoader));
		
		public WeavingVisitor(IProgressMonitor monitor) {
			_progressMonitor= monitor;
		}
		
		/*
		 * (non-Javadoc)
		 * 
		 * @see org.eclipse.core.resources.IResourceDeltaVisitor#visit(org.eclipse.core.resources.IResourceDelta)
		 */
		public boolean visit(IResourceDelta delta) throws CoreException {
			IResource resource = delta.getResource();
			switch (delta.getKind()) {
			case IResourceDelta.ADDED:
				// handle added resource
				weave(resource);
				break;
			case IResourceDelta.REMOVED:
				// handle removed resource
				clean(resource);
				break;
			case IResourceDelta.CHANGED:
				// handle changed resource
				weave(resource);
				break;
			}
			//return true to continue visiting children.
			return true;
		}
		
		public boolean visit(IResource resource) {
			weave(resource);
			//return true to continue visiting children.
			return true;
		}
		

		void weave(IResource resource) {
			if (_progressMonitor.isCanceled())
				return;
			
			if (!(resource instanceof IFile) ||  !resource.getName().endsWith(".class")) 
				return; // do nothing, kilim weaving only applies to .class files

			IFile classfile = (IFile) resource;
			
			IResource sourceFile= null;
			String className= null;
			try {
				// get class name
				IClassFile classFile= (IClassFile)JavaCore.create(classfile);
				IClassFileReader classFileReader= ToolFactory.createDefaultClassFileReader(classFile, IClassFileReader.CLASSFILE_ATTRIBUTES);
				className= new String(classFileReader.getClassName()).replace('/', '.');
				_progressMonitor.subTask("Weaving "+className);
				
				/**
				 * Determine output location.
				 * Create folder for holding instrumented classes.
				 */
				IContainer classContainer= classfile.getParent();
				{ 
					for (int i= className.split(Pattern.quote(".")).length; 1 < i--;) {
						classContainer= classContainer.getParent();
					}
				}
				IPath rawClassLocation= classContainer.getRawLocation();
				// ignore woven classes
				if (rawClassLocation.lastSegment().equals("kilim"))
					return;
				IPath outputPath= rawClassLocation.removeLastSegments(1).append("/instrumented").append("/kilim");
				File outputFolder= outputPath.toFile();
				if (outputFolder.mkdirs())
					classContainer.refreshLocal(IResource.DEPTH_INFINITE, null/*no monitor*/);
				
				/*
				 * Because it is not possible, without analyzing source code, to 
				 * accurately locate and mark errors in anonymous types when the 
				 * containing type has compilation errors, we do not run the Kilim weaver until 
				 * the top-level type is free of compile errors.  
				 */
				String containingType= JDTUtils.isAnonymousTypeOrHasAnonymousContainingType(className);
				if (containingType != null) {
					IType type= JDTUtils.findType(_javaProject, containingType);
					if (type == null || !type.exists()) {
//						copyToOutputFolder(outputFolder, className, classFile);
						return; // could not find top level type, do nothing
					}
					IMarker[] errorMarkers= JDTUtils.findJavaErrorMarkers(_project, type.getResource());
					if (0 < errorMarkers.length) {
//						copyToOutputFolder(outputFolder, className, classFile);
						return; // containing type has errors, skip for now
					}
				}
				
				// find source file if we can
				IType type= null;
				synchronized (__projectAccess) { // dont know why, but this prevents Eclipse from locking up
					type= JDTUtils.findType(_javaProject, className);
					if (type != null && !type.isBinary())
						sourceFile= type.getResource();
				}
				
				if (sourceFile != null)
					deleteMarkers(className, sourceFile);
				
				// get path to class to weave
				InputStream classContents= new BufferedInputStream(classfile.getContents());
				try {
					Weaver.weaveFile(className, classContents, _detector, outputFolder.getCanonicalPath());
				}
				finally {
					try { classContents.close(); } catch (Throwable t) { }
				}
				
				// refresh so that Eclipse sees any new files
				//classfile.getParent().refreshLocal(IResource.DEPTH_ZERO, null);
			} 
			catch (Throwable e) {
				if (sourceFile != null) {
					addMarker(className, sourceFile, e);
				}
				else
					LogUtils.logError("Kilim Problem", e);
			}
			return;
		}
		
	}
	
	class CopyingVisitor implements IResourceDeltaVisitor, IResourceVisitor {
		
		IProgressMonitor _progressMonitor;
		IProject _project= getProject();
		IJavaProject _javaProject= JDTUtils.getJavaProject(_project);
		List<IClasspathEntry> _sourcePaths;
		IWorkspaceRoot _workspaceRoot= _project.getWorkspace().getRoot();
		
		public CopyingVisitor(IProgressMonitor monitor) throws JavaModelException {
			_progressMonitor= monitor;
			_sourcePaths= JDTUtils.getSourcePaths(_javaProject);
		}
		
		/*
		 * (non-Javadoc)
		 * 
		 * @see org.eclipse.core.resources.IResourceDeltaVisitor#visit(org.eclipse.core.resources.IResourceDelta)
		 */
		public boolean visit(IResourceDelta delta) throws CoreException {
			IResource resource = delta.getResource();
			switch (delta.getKind()) {
			case IResourceDelta.ADDED:
				// handle added resource
				copy(resource);
				break;
			case IResourceDelta.REMOVED:
				// handle removed resource
				break;
			case IResourceDelta.CHANGED:
				// handle changed resource
				copy(resource);
				break;
			}
			//return true to continue visiting children.
			return true;
		}
		
		public boolean visit(IResource resource) {
			copy(resource);
			//return true to continue visiting children.
			return true;
		}
		

		void copy(IResource resource)  {
			try {
				if (_progressMonitor.isCanceled())
					return;
				
				if (!(resource instanceof IFile) ||  !resource.getName().endsWith(".class")) 
					return; // do nothing, kilim weaving only applies to .class files

				IPath resourceFolder= resource.getParent().getLocation();
				IPath resourceFile= resource.getLocation();
				for (IClasspathEntry classpathEntry:_sourcePaths) {
					IPath outputLocation= classpathEntry.getOutputLocation();
					if (outputLocation == null)
						outputLocation= _javaProject.getOutputLocation();
					outputLocation= _workspaceRoot.getFolder(outputLocation).getLocation();
					if (outputLocation.isPrefixOf(resourceFolder)) {
						IPath copyFolderPath= outputLocation.removeLastSegments(1).append("/instrumented").append("/kilim");
						
						IPath relativeResourcePath= resourceFile.makeRelativeTo(outputLocation);
						IPath destinationPath= copyFolderPath.append(relativeResourcePath);
						
						IFile destinationFile= _workspaceRoot.getFileForLocation(destinationPath);
						if (destinationFile.exists())
							destinationFile.delete(true, null);
						
						IContainer destinationFolder= destinationFile.getParent();
						if (!destinationFolder.exists())
							JDTUtils.create(destinationFolder, null);
						
						IPath relativeDestination= destinationPath.makeRelativeTo(resourceFolder);
						resource.copy(relativeDestination, true, null);
						break;
					}
				}
			} catch (CoreException e) {
				LogUtils.logError("failed to copy .class file", e);
			}
		}
		
	}

	public static final String BUILDER_ID = "com.googlecode.kilimbuilder.kilimBuilder";

	private static final String KILIM_MARKER_TYPE = "com.googlecode.kilimbuilder.kilimProblem";

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.core.internal.events.InternalBuilder#build(int,
	 *      java.util.Map, org.eclipse.core.runtime.IProgressMonitor)
	 */
	protected IProject[] build(int kind, Map args, IProgressMonitor monitor) throws CoreException {
		if (kind == FULL_BUILD) {
			fullBuild(monitor);
		} else {
			IResourceDelta delta = getDelta(getProject());
			if (delta == null) {
				fullBuild(monitor);
			} else {
				incrementalBuild(delta, monitor);
			}
		}
		return null;
	}
	void clean(IResource resource) {
		
		String className= null;
		if (resource instanceof IFile &&  resource.getName().endsWith(".class")) {
			IFile classfile = (IFile) resource;
			try {
				// get class name
				IClassFile classFile= (IClassFile)JavaCore.create(classfile);
				IClassFileReader classFileReader= ToolFactory.createDefaultClassFileReader(classFile, IClassFileReader.CLASSFILE_ATTRIBUTES);
				className= new String(classFileReader.getClassName()).replace('/', '.');
				
				// Delete any associated instrumented class if we can find one
				IContainer classContainer= classfile.getParent();
				{ 
					for (int i= className.split(Pattern.quote(".")).length; 1 < i--;) {
						classContainer= classContainer.getParent();
					}
				}
				IPath outputLocation= classContainer.getRawLocation();
				IPath resourceFile= resource.getLocation();
				// ignore woven classes
				if (!outputLocation.lastSegment().equals("kilim")) {
					
					IPath copyFolderPath= outputLocation.removeLastSegments(1).append("/instrumented").append("/kilim");
					
					IPath relativeResourcePath= resourceFile.makeRelativeTo(outputLocation);
					IPath destinationPath= copyFolderPath.append(relativeResourcePath);
					
					IFile destinationFile= resource.getWorkspace().getRoot().getFileForLocation(destinationPath);
					if (destinationFile.exists())
						destinationFile.delete(true, null);
				}
			}
			catch (Throwable t) { 
			}
		}

		deleteMarkers(className, resource);
	}

	private void deleteMarkers(String classname, IResource file) {
		try {
			for (IMarker marker:file.findMarkers(null, true, IResource.DEPTH_INFINITE)) {
				Object sourceId= marker.getAttribute(IMarker.SOURCE_ID);
				if (KilimActivator.PLUGIN_ID.equals(sourceId)) {
					if (classname == null) {
						marker.delete();
					}
					else {
						Object markedClass= marker.getAttribute(CLASSNAME_ATTRIBUTE);
						if (markedClass == null) {
							marker.delete();
						}
						else if (classname.equals(markedClass)) {
							marker.delete();
						}
					}
				}
			}
		} catch (CoreException ce) {
		}
	}
	private void addMarker(String className, IResource sourceFile, Throwable e) {
		try {
			IJavaProject project= JDTUtils.getJavaProject(sourceFile);

			// try to extract kilim message
			int line= -1;
			String msg= e.getMessage();
			String txt= e.getMessage();
			HashSet<Throwable> seen= new HashSet<Throwable>();
			seen.add(e);
			Throwable c= e.getCause();
			String methodSig= null;
			while (c != null && !seen.contains(c)) {
				if (c instanceof KilimException) {
					msg= c.getMessage();
					if (msg.contains("Base class method is pausable, derived class is not")) {
						String methodMarker= "Method = ";
						methodSig= msg.substring(msg.indexOf(methodMarker)+methodMarker.length());
						if (0 < methodSig.indexOf("---"))
							methodSig= methodSig.substring(0, methodSig.indexOf("---"));
						methodSig= methodSig.trim();
						msg= "Method throws Pausable in the base class but not in subclass";
						
						IType type= JDTUtils.findType(project, className);
						if (type != null) {
							IMethod method= JDTUtils.findMethod(type, methodSig);
							if (method != null) {
								ICompilationUnit compilationUnit= type.getCompilationUnit();
								if (compilationUnit != null)
									line= JDTUtils.getLineNumber(compilationUnit, method.getSourceRange().getOffset());
							}
						}
					}
					else if (msg.contains("should be marked pausable. It calls pausable methods")) {
						methodSig= msg.substring(0, msg.indexOf("should be marked pausable. It calls pausable methods"));
						methodSig= methodSig.trim();
						methodSig= methodSig.replaceAll("/", ".");
						String methodName= methodSig.substring(0, methodSig.indexOf('(')); 
						methodName= methodName.substring(methodName.lastIndexOf('.')+1); 
						msg= "Method should be marked pausable. It calls pausable methods";
						
						IType type= JDTUtils.findType(project, className);
						if (type != null) {
							IMethod method= JDTUtils.findMethod(type, methodSig);
							if (method != null) {
								ICompilationUnit compilationUnit= type.getCompilationUnit();
								if (compilationUnit != null)
									line= JDTUtils.getLineNumber(compilationUnit, method.getSourceRange().getOffset());
							}
						}
					}
					else if (msg.contains("from within a synchronized block")) {
						String methodMarker= "Caller: ";
						methodSig= msg.substring(msg.indexOf(methodMarker)+methodMarker.length());
						methodSig= methodSig.substring(0, methodSig.indexOf("Callee:"));
						methodSig= methodSig.trim();
						if (methodSig.endsWith(";"))
							methodSig= methodSig.substring(0, methodSig.length()-1);
						String methodName= methodSig.substring(0, methodSig.indexOf('(')); 
						methodName= methodName.substring(methodName.lastIndexOf('.')+1); 
						msg= "Method calls Pausable method from within a synchronized block";
						
						IType type= JDTUtils.findType(project, className);
						if (type != null) {
							IMethod method= JDTUtils.findMethod(type, methodSig);
							if (method != null) {
								ICompilationUnit compilationUnit= type.getCompilationUnit();
								if (compilationUnit != null)
									line= JDTUtils.getLineNumber(compilationUnit, method.getSourceRange().getOffset());
							}
						}
					}
					break;
				}
				seen.add(c);
			}
			
			// if we could not figure out what line is associated with the error 
			// then append the method signature to the marker text so the user 
			// has some clue to where the error occurred.
			if (line < 0 && methodSig != null)
				msg+= " : "+methodSig;

			IMarker marker = sourceFile.createMarker(KILIM_MARKER_TYPE);
			marker.setAttribute(IMarker.MESSAGE, msg);
			marker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_ERROR);
			marker.setAttribute(IMarker.SOURCE_ID, KilimActivator.PLUGIN_ID);
			marker.setAttribute(IMarker.PRIORITY, IMarker.PRIORITY_HIGH);
			marker.setAttribute(IMarker.TEXT, txt);
			if (0 <= line)
				marker.setAttribute(IMarker.LINE_NUMBER, line);
			if (className != null)
				marker.setAttribute(CLASSNAME_ATTRIBUTE, className);
		}
		catch (CoreException x) { 
			LogUtils.logError("Error while adding Kilim problem marker for "+className, x);
		}
	}
	
	@Override
	protected void clean(IProgressMonitor monitor) throws CoreException {
		try {
			IProject project= getProject();
			IJavaProject javaProject= JDTUtils.getJavaProject(project);
			List<IClasspathEntry> sourcePaths= JDTUtils.getSourcePaths(javaProject);
			IWorkspaceRoot workspaceRoot= project.getWorkspace().getRoot();
			
			// delete all folders with instrumented class files.
			for (IClasspathEntry classpathEntry:sourcePaths) {
				IPath outputLocation= classpathEntry.getOutputLocation();
				if (outputLocation == null)
					outputLocation= javaProject.getOutputLocation();
				//outputLocation= workspaceRoot.getFolder(outputLocation).getLocation();
				IPath instrumentLocation= outputLocation.removeLastSegments(1).append("/instrumented").append("/kilim");
				IFolder instrumentFolder= workspaceRoot.getFolder(instrumentLocation);
				if (instrumentFolder.exists()) {
					
					instrumentFolder.delete(true, null);
					
					// recreate the folder
					instrumentFolder= workspaceRoot.getFolder(instrumentLocation);
					instrumentFolder.create(true, true, null);
				}
			}
			
			
//			getProject().accept(new IResourceVisitor() {
//				public boolean visit(IResource resource) throws CoreException {
//					clean(resource);
//					//return true to continue visiting children.
//					return true;
//				}
//			});
		} catch (CoreException e) {
		}
	}
	

	protected void fullBuild(final IProgressMonitor monitor) throws CoreException {
		try {
			IProject project= getProject();
			project.accept(new CopyingVisitor(monitor)); // copy all the .class files before weaving
			project.accept(new WeavingVisitor(monitor));
			//project.refreshLocal(IResource.DEPTH_INFINITE, monitor);
		} catch (CoreException e) {
		}
	}


	protected void incrementalBuild(IResourceDelta delta, IProgressMonitor monitor) throws CoreException {
		//IProject project= getProject();
		delta.accept(new CopyingVisitor(monitor)); // copy all the .class files before weaving
		delta.accept(new WeavingVisitor(monitor));
		//project.refreshLocal(IResource.DEPTH_INFINITE, monitor);
	}
}
