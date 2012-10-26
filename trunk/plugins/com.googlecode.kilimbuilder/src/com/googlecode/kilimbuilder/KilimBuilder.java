package com.googlecode.kilimbuilder;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import kilim.KilimClassLoader;
import kilim.KilimException;
import kilim.mirrors.Detector;
import kilim.mirrors.RuntimeClassMirrors;
import kilim.tools.Weaver;

import org.eclipse.core.resources.IContainer;
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
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.util.IClassFileReader;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

public class KilimBuilder extends IncrementalProjectBuilder {
	
	
	class KilimWeavingJob extends WorkspaceJob {
		IFile _classfile;
		public KilimWeavingJob(IFile classfile) {
			super("Weaving "+classfile.getName());
			this._classfile= classfile;
		}
		public IStatus runInWorkspace(IProgressMonitor monitor) {
			IResource sourceFile= null;
			try {
				deleteMarkers(_classfile);
				
				// get class name
				IClassFile classFile= (IClassFile)JavaCore.create(_classfile);
				IClassFileReader classFileReader= ToolFactory.createDefaultClassFileReader(classFile, IClassFileReader.CLASSFILE_ATTRIBUTES);
				String className= new String(classFileReader.getClassName()).replace('/', '.');
				IJavaProject project= classFile.getJavaProject();
				
				// find source file if we can
				String primaryName= className;
				int i= primaryName.indexOf('$');
				if (0 < i)
					primaryName= primaryName.substring(0, i);
				IType type= project.findType(primaryName);
				if (type != null)
					sourceFile= type.getResource();
				
				// get path to class to weave
				IContainer classContainer= _classfile.getParent();
				String outputDirectory= classContainer.getLocation().toString(); 
				
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
					// try to extract kilim message
					String msg= e.getMessage();
					HashSet<Throwable> seen= new HashSet<Throwable>();
					seen.add(e);
					Throwable c= e.getCause();
					while (c != null && !seen.contains(c)) {
						if (c instanceof KilimException) {
							msg= c.getMessage();
							break;
						}
						seen.add(c);
					}
					
					try {
						IMarker marker = sourceFile.createMarker(IMarker.PROBLEM);
						marker.setAttribute(IMarker.MESSAGE, msg);
						marker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_ERROR);
						marker.setAttribute(IMarker.SOURCE_ID, KilimActivator.PLUGIN_ID);
					}
					catch (CoreException x) { // ignore 
					}
				}
				LogUtils.logError("Error during Kilim weaving", e);
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

	class XMLErrorHandler extends DefaultHandler {
		
		private IFile file;

		public XMLErrorHandler(IFile file) {
			this.file = file;
		}

		private void addMarker(SAXParseException e, int severity) {
			KilimBuilder.addMarker(file, e.getMessage(), e
					.getLineNumber(), severity);
		}

		public void error(SAXParseException exception) throws SAXException {
			addMarker(exception, IMarker.SEVERITY_ERROR);
		}

		public void fatalError(SAXParseException exception) throws SAXException {
			addMarker(exception, IMarker.SEVERITY_ERROR);
		}

		public void warning(SAXParseException exception) throws SAXException {
			addMarker(exception, IMarker.SEVERITY_WARNING);
		}
	}

	public static final String BUILDER_ID = "com.googlecode.kilimbuilder.kilimBuilder";

	private static final String KILIM_MARKER_TYPE = "com.googlecode.kilimbuilder.kilimProblem";

	private SAXParserFactory parserFactory;

	static void addMarker(IFile file, String message, int lineNumber, int severity) {
		try {
			IMarker marker = file.createMarker(KILIM_MARKER_TYPE);
			marker.setAttribute(IMarker.MESSAGE, message);
			marker.setAttribute(IMarker.SEVERITY, severity);
//			if (lineNumber == -1) {
//				lineNumber = 1;
//			}
//			marker.setAttribute(IMarker.LINE_NUMBER, lineNumber);
		} catch (CoreException e) {
		}
	}

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

	private void deleteMarkers(IFile file) {
		try {
			for (IMarker marker:file.findMarkers(null, true, IResource.DEPTH_INFINITE)) {
				if (marker.getAttribute(IMarker.SOURCE_ID).equals(KilimActivator.PLUGIN_ID)) {
					marker.delete();
				}
			}
		} catch (CoreException ce) {
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
