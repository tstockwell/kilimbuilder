package com.googlecode.kilimbuilder;

import java.util.ArrayList;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectNature;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;

import com.googlecode.kilimbuilder.utils.JDTUtils;

public class KilimNature implements IProjectNature {

	public static final String KILIM_NATURE_ID = "com.googlecode.kilimbuilder.kilimNature";
	public static final String KILIM_CLASSPATH_ATTRIBUTE = "com.googlecode.kilimbuilder.path";
	public static final String KILIM_OUTPUT_PATH = "/instrumented/kilim";

	private IProject project;
	
	public void configure() throws CoreException {
        IJavaProject javaProject = (IJavaProject)project.getNature(JavaCore.NATURE_ID);
        
		JDTUtils.addBuilderToProject(project, KilimBuilder.BUILDER_ID);
		
        IClasspathEntry kilimEntry = JavaCore.newContainerEntry(new Path(KilimClasspathContainer.CONTAINER_PATH));
		JDTUtils.addClasspathEntryToProject(javaProject, kilimEntry);

		addOutputClasspathEntries();
	}

	public void deconfigure() throws CoreException {
        IJavaProject javaProject = (IJavaProject)project.getNature(JavaCore.NATURE_ID);

        JDTUtils.removeBuilderFromProject(project, KilimBuilder.BUILDER_ID);
		JDTUtils.removeClasspathEntryFromProject(javaProject, KilimClasspathContainer.CONTAINER_PATH);
		
		removeOutputClasspathEntries();
	}

	private void removeOutputClasspathEntries() throws CoreException {
        IJavaProject javaProject = (IJavaProject)project.getNature(JavaCore.NATURE_ID);
        IClasspathEntry[] rawClasspath = javaProject.getRawClasspath();
        ArrayList<IClasspathEntry> newClassPath= new ArrayList<IClasspathEntry>();
		for (int i = 0; i < rawClasspath.length; ++i) {
			boolean remove= false;
			IClasspathEntry classpathEntry= rawClasspath[i];
			IClasspathAttribute[] attributes= classpathEntry.getExtraAttributes();
			if (attributes != null) {
				for (IClasspathAttribute attribute:attributes) {
					if (KILIM_CLASSPATH_ATTRIBUTE.equals(attribute.getName())) {
						remove= true;
						break;
					}
				}
			}
			if (!remove)
				newClassPath.add(classpathEntry);
		}
		if (newClassPath.size() < rawClasspath.length) {
			javaProject.setRawClasspath(newClassPath.toArray(new IClasspathEntry[newClassPath.size()]), null);			
		}
	}

	public IProject getProject() {
		return project;
	}

	public void setProject(IProject project) {
			this.project = project;
	}
	
	void addOutputClasspathEntries() throws CoreException {
        IJavaProject javaProject = (IJavaProject)project.getNature(JavaCore.NATURE_ID);
        
        ArrayList<IClasspathEntry> newClasspath= new ArrayList<IClasspathEntry>();
        IClasspathEntry[] rawClasspath = javaProject.getRawClasspath();
        for (IClasspathEntry entry:rawClasspath)
        	newClasspath.add(entry);
        boolean addedDefault= false;
        for (int i= 0; i < newClasspath.size(); i++) {
        	IClasspathEntry entry= newClasspath.get(i);
        	
        	
//        	if (entry.getPath().equals(outputLocationPath)) { // default output location
//            	IFile outputLocation= javaProject.getProject().getFile(outputLocationPath);
//            	IPath instrumentedPath= outputLocation.getParent().getLocation().append(KILIM_OUTPUT_PATH);
//            	IClasspathAttribute[] attributes= new IClasspathAttribute[] { 
//            			JavaCore.newClasspathAttribute(KILIM_CLASSPATH_ATTRIBUTE, "true")
//            	};
//            	IClasspathEntry instrmentedOutputEntry = JavaCore.newLibraryEntry(instrumentedPath, null, null, null, attributes, false);
//        		newClasspath.add(instrmentedOutputEntry); // add kilim entry immediately before output location 
//        	}
        	
    		if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
    	        IPath outputLocationPath= entry.getOutputLocation();
    	        if (outputLocationPath == null && !addedDefault) {
    	        	outputLocationPath= javaProject.getOutputLocation();
    	        	addedDefault= true;
    	        }
    	        if (outputLocationPath != null) {
    	        	IPath instrumentedPath= outputLocationPath.removeLastSegments(1);
    	        	instrumentedPath= instrumentedPath.append(KILIM_OUTPUT_PATH);
    	        	IClasspathAttribute[] attributes= new IClasspathAttribute[] { 
    	        			JavaCore.newClasspathAttribute(KILIM_CLASSPATH_ATTRIBUTE, "true")
    	        	};
    	        	IClasspathEntry instrmentedOutputEntry = JavaCore.newLibraryEntry(instrumentedPath, null, null, null, attributes, false);
            		newClasspath.add(i++, instrmentedOutputEntry); // add kilim entry immediately before output location
            		
            		// create the folder if it doesnt exist
            		{
	                	IFile file= project.getWorkspace().getRoot().getFile(instrumentedPath);
	                	IPath path= file.getProjectRelativePath();
	                	IFolder outputLocation= project.getFolder(path);
	            		if (!outputLocation.exists())
	            			JDTUtils.createFolder(project, outputLocation);
            		}
    	        }
    		}
        }
        
        if (rawClasspath.length < newClasspath.size()) {
			javaProject.setRawClasspath(newClasspath.toArray(new IClasspathEntry[newClasspath.size()]), null);			
        }
	}

}
