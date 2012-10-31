package com.googlecode.kilimbuilder;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Map;

import kilim.KilimException;
import kilim.mirrors.Detector;
import kilim.mirrors.RuntimeClassMirrors;
import kilim.tools.Weaver;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.util.IClassFileReader;

@SuppressWarnings("rawtypes")
public class KilimBuilder extends IncrementalProjectBuilder {
	
	
	private static final Object __projectAccess= new Object();


	class KilimWeavingJob extends WorkspaceJob {
		IFile _classfile;
		public KilimWeavingJob(IFile classfile) {
			super("Weaving "+classfile.getName());
			this._classfile= classfile;
		}
		public IStatus runInWorkspace(IProgressMonitor monitor) {
			IResource sourceFile= null;
			String className= null;
			try {
				// get class name
				IClassFile classFile= (IClassFile)JavaCore.create(_classfile);
				IClassFileReader classFileReader= ToolFactory.createDefaultClassFileReader(classFile, IClassFileReader.CLASSFILE_ATTRIBUTES);
				className= new String(classFileReader.getClassName()).replace('/', '.');
				IJavaProject project= classFile.getJavaProject();
				
				// find source file if we can
				IType type= null;
				synchronized (__projectAccess) { // dont know why, but this prevents Eclipse from locking up
					type= PluginUtils.findType(project, className);
					if (type != null)
						sourceFile= type.getResource();
				}
				
				if (sourceFile != null)
					deleteMarkers(sourceFile);
				
				
				// get path to class to weave
				ClassLoader projectClassLoader= PluginUtils.createProjectClassLoader(project);
				Detector detector= new Detector(new RuntimeClassMirrors(projectClassLoader));
				InputStream classContents= new BufferedInputStream(_classfile.getContents());
				try {
					Weaver.weaveFile(className, classContents, detector);
				}
				finally {
					try { classContents.close(); } catch (Throwable t) { }
				}
				
				// refresh so that Eclipse sees any new files
				_classfile.getParent().refreshLocal(IResource.DEPTH_ZERO, null);
			} 
			catch (Throwable e) {
				if (sourceFile != null) {
					addMarker(className, sourceFile, e);
				}
				else
					LogUtils.logError("Kilim Problem", e);
			}
			return Status.OK_STATUS;
		}
	}

	class KilimDeltaVisitor implements IResourceDeltaVisitor {
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
				break;
			case IResourceDelta.CHANGED:
				// handle changed resource
				weave(resource);
				break;
			}
			//return true to continue visiting children.
			return true;
		}
	}

	class KilimResourceVisitor implements IResourceVisitor {
		public boolean visit(IResource resource) {
			weave(resource);
			//return true to continue visiting children.
			return true;
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

	void weave(IResource resource) {
		if (!(resource instanceof IFile) ||  !resource.getName().endsWith(".class")) 
			return; // do nothing, kilim weaving only applies to .class files

		IFile file = (IFile) resource;
		KilimWeavingJob job= new KilimWeavingJob(file);
		job.setPriority(Job.BUILD);
		job.schedule();
	}
	void clean(IResource resource) {
		deleteMarkers(resource);
	}

	private void deleteMarkers(IResource file) {
		try {
			for (IMarker marker:file.findMarkers(null, true, IResource.DEPTH_INFINITE)) {
				if (marker.getAttribute(IMarker.SOURCE_ID).equals(KilimActivator.PLUGIN_ID)) {
					marker.delete();
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
			while (c != null && !seen.contains(c)) {
				if (c instanceof KilimException) {
					msg= c.getMessage();
					if (msg.contains("Base class method is pausable, derived class is not")) {
						String methodMarker= "Method = ";
						String methodSig= msg.substring(msg.indexOf(methodMarker)+methodMarker.length());
						methodSig.trim();
						String methodName= methodSig.substring(0, methodSig.indexOf('(')); 
						msg= methodName+ " method throws Pausable in the base class but not in subclass";
						
						IType type= PluginUtils.findType(project, className);
						if (type != null) {
							String[] params= JDTUtils.parseForParameterTypes(methodSig);
							IMethod method= type.getMethod(methodName, params);
							line= JDTUtils.getLineNumber(type.getCompilationUnit(), method.getSourceRange().getOffset());
						}
					}
					else if (msg.contains("should be marked pausable. It calls pausable methods")) {
						String methodSig= msg.substring(msg.indexOf("should be marked pausable. It calls pausable methods"));
						methodSig.trim();
						String methodName= methodSig.substring(0, methodSig.indexOf('(')); 
						msg= methodName+ " method should be marked pausable. It calls pausable methods";
						
						IType type= PluginUtils.findType(project, className);
						if (type != null) {
							String[] params= JDTUtils.parseForParameterTypes(methodSig);
							IMethod method= type.getMethod(methodName, params);
							line= JDTUtils.getLineNumber(type.getCompilationUnit(), method.getSourceRange().getOffset());
						}
					}
					else if (msg.contains("from within a synchronized block")) {
						String methodSig= msg.substring(msg.indexOf("should be marked pausable. It calls pausable methods"));
						methodSig.trim();
						String methodName= methodSig.substring(0, methodSig.indexOf('(')); 
						msg= methodName+ "Cannot call pausable methods from within a synchronized block";
						
						IType type= PluginUtils.findType(project, className);
						if (type != null) {
							String[] params= JDTUtils.parseForParameterTypes(methodSig);
							IMethod method= type.getMethod(methodName, params);
							line= JDTUtils.getLineNumber(type.getCompilationUnit(), method.getSourceRange().getOffset());
						}
					}
					break;
				}
				seen.add(c);
			}

			IMarker marker = sourceFile.createMarker(KILIM_MARKER_TYPE);
			marker.setAttribute(IMarker.MESSAGE, msg);
			marker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_ERROR);
			marker.setAttribute(IMarker.SOURCE_ID, KilimActivator.PLUGIN_ID);
			marker.setAttribute(IMarker.PRIORITY, IMarker.PRIORITY_HIGH);
			marker.setAttribute(IMarker.TEXT, txt);
			if (0 <= line)
				marker.setAttribute(IMarker.LINE_NUMBER, line);
		}
		catch (CoreException x) { 
			LogUtils.logError("Error while adding Kilim problem marker", e);
		}
	}
	
	@Override
	protected void clean(IProgressMonitor monitor) throws CoreException {
		try {
			getProject().accept(new IResourceVisitor() {
				@Override
				public boolean visit(IResource resource) throws CoreException {
					clean(resource);
					//return true to continue visiting children.
					return true;
				}
			});
		} catch (CoreException e) {
		}
	}
	

	protected void fullBuild(final IProgressMonitor monitor) throws CoreException {
		try {
			getProject().accept(new KilimResourceVisitor());
		} catch (CoreException e) {
		}
	}


	protected void incrementalBuild(IResourceDelta delta, IProgressMonitor monitor) throws CoreException {
		// the visitor does the work.
		delta.accept(new KilimDeltaVisitor());
	}
}
