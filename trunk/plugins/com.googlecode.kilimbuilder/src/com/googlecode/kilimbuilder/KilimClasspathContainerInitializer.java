package com.googlecode.kilimbuilder;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.ClasspathContainerInitializer;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;

import com.googlecode.kilimbuilder.utils.LogUtils;

public class KilimClasspathContainerInitializer extends ClasspathContainerInitializer {

	@Override
	public void initialize(IPath containerPath, IJavaProject project) 
	throws CoreException 
	{
		KilimClasspathContainer container = new KilimClasspathContainer( containerPath, project );
		if(container.isValid()) {
			JavaCore.setClasspathContainer(containerPath, new IJavaProject[] {project}, new IClasspathContainer[] {container}, null);             
		} else {
			LogUtils.logWarning(Messages.InvalidContainer + containerPath);
		}
	}

	@Override
	public boolean canUpdateClasspathContainer(IPath containerPath, IJavaProject project) 
	{
		return true;
	}

	@Override
	public void requestClasspathContainerUpdate(IPath containerPath, IJavaProject project, IClasspathContainer containerSuggestion) 
	throws CoreException 
	{
		JavaCore.setClasspathContainer(containerPath, new IJavaProject[] { project },   new IClasspathContainer[] { containerSuggestion }, null);
	}



}
