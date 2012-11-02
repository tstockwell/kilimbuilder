package com.googlecode.kilimbuilder;

import org.eclipse.core.resources.ICommand;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IProjectNature;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

public class KilimNature implements IProjectNature {

	/**
	 * CONTAINER_PATH of this project nature
	 */
	public static final String KILIM_NATURE_ID = "com.googlecode.kilimbuilder.kilimNature";

	private IProject project;
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.core.resources.IProjectNature#configure()
	 */
	public void configure() throws CoreException {
		addBuilderToProject();
		
		addClassEntryEntryToProject();
		
	}

	// add Kilim library to classpath
	private void addClassEntryEntryToProject() throws CoreException, JavaModelException {
        IJavaProject javaProject = (IJavaProject)project.getNature(JavaCore.NATURE_ID);
        IClasspathEntry[] rawClasspath = javaProject.getRawClasspath();
		boolean alreadyConfigured= false;
		for (int i = 0; i < rawClasspath.length; ++i) {
			if (KilimClasspathContainer.CONTAINER_PATH.equals(rawClasspath[i].getPath())) {
				alreadyConfigured= true;
				break;
			}
		}
		if (!alreadyConfigured) {
	        IClasspathEntry[] newClasspath = new IClasspathEntry[rawClasspath.length+1];
			System.arraycopy(rawClasspath, 0, newClasspath, 0, rawClasspath.length);
            IClasspathEntry kilimEntry = JavaCore.newContainerEntry(KilimClasspathContainer.CONTAINER_PATH);
            newClasspath[newClasspath.length - 1] = kilimEntry;
	        javaProject.setRawClasspath(newClasspath,null);
		}
	}

	// add Kilim builder to build specification
	private void addBuilderToProject() throws CoreException {
		IProjectDescription projectDescription= project.getDescription();
		ICommand[] commands = projectDescription.getBuildSpec();
		boolean alreadyConfigured= false;
		for (int i = 0; i < commands.length; ++i) {
			if (commands[i].getBuilderName().equals(KilimBuilder.BUILDER_ID)) {
				alreadyConfigured= true;
				break;
			}
		}
		if (!alreadyConfigured) {
			ICommand[] newCommands = new ICommand[commands.length + 1];
			System.arraycopy(commands, 0, newCommands, 0, commands.length);
			ICommand command = projectDescription.newCommand();
			command.setBuilderName(KilimBuilder.BUILDER_ID);
			newCommands[newCommands.length - 1] = command;
			projectDescription.setBuildSpec(newCommands);
			project.setDescription(projectDescription, null);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.core.resources.IProjectNature#deconfigure()
	 */
	public void deconfigure() throws CoreException {
		IProjectDescription description = getProject().getDescription();
		ICommand[] commands = description.getBuildSpec();
		for (int i = 0; i < commands.length; ++i) {
			if (commands[i].getBuilderName().equals(KilimBuilder.BUILDER_ID)) {
				ICommand[] newCommands = new ICommand[commands.length - 1];
				System.arraycopy(commands, 0, newCommands, 0, i);
				System.arraycopy(commands, i + 1, newCommands, i,
						commands.length - i - 1);
				description.setBuildSpec(newCommands);
				project.setDescription(description, null);			
				break;
			}
		}
		
        IJavaProject javaProject = (IJavaProject)project.getNature(JavaCore.NATURE_ID);
        IClasspathEntry[] rawClasspath = javaProject.getRawClasspath();
		for (int i = 0; i < rawClasspath.length; ++i) {
			if (KilimClasspathContainer.CONTAINER_PATH.equals(rawClasspath[i].getPath())) {
		        IClasspathEntry[] newClasspath = new IClasspathEntry[rawClasspath.length-1];
				System.arraycopy(rawClasspath, 0, newClasspath, 0, i);
				System.arraycopy(rawClasspath, i + 1, newClasspath, i, rawClasspath.length - i - 1);
				javaProject.setRawClasspath(newClasspath, null);			
				break;
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.core.resources.IProjectNature#getProject()
	 */
	public IProject getProject() {
		return project;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.core.resources.IProjectNature#setProject(org.eclipse.core.resources.IProject)
	 */
	public void setProject(IProject project) {
			this.project = project;
	}

}
