package com.googlecode.kilimbuilder;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaModelMarker;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IParent;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

public class PluginUtils
{
	public static List<IJavaProject> getAllProjects() throws Exception
	{
		List<IJavaProject> javaProjects = new ArrayList<IJavaProject>();
		IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
		for (IProject project : projects) {
			project.open(null /* IProgressMonitor */);
			IJavaProject javaProject = JavaCore.create(project);
			javaProjects.add(javaProject);
		}
		return javaProjects;
	}

	public static Object getInstance(String fqClassname) throws Exception
	{
		List<URLClassLoader> loaders = new ArrayList<URLClassLoader>();
		List<IJavaProject> javaProjects = getAllProjects();
		for (IJavaProject proj : javaProjects) {
			URL urls[] = getClasspathAsURLArray(proj);
			loaders.add(new URLClassLoader(urls));
		}

		for (URLClassLoader loader : loaders) {
			try {
				Class<?> clazz = loader.loadClass(fqClassname);
				if (!clazz.isInterface()  && !clazz.isEnum()) {
					return clazz.newInstance();
				} else {
					return clazz;
				}
			} catch (ClassNotFoundException e) {
				LogUtils.logError("No class found for " + fqClassname, e);
			}
		}
		return null;
	}

	public static ClassLoader createProjectClassLoader(IJavaProject javaProject) {
		return new URLClassLoader(getClasspathAsURLArray(javaProject));
	}

	public static URL[] getClasspathAsURLArray(IJavaProject javaProject)
	{
		if (javaProject == null)
			return null;
		Set<IJavaProject> visited = new HashSet<IJavaProject>();
		List<URL> urls = new ArrayList<URL>(20);
		collectClasspathURLs(javaProject, urls, visited, true);
		URL[] result = new URL[urls.size()];
		urls.toArray(result);
		return result;
	}

	private static void collectClasspathURLs(IJavaProject javaProject, List<URL> urls, Set<IJavaProject> visited,
			boolean isFirstProject)
	{
		if (visited.contains(javaProject))
			return;
		visited.add(javaProject);
		IPath outPath = getJavaProjectOutputAbsoluteLocation(javaProject.getProject());
		outPath = outPath.addTrailingSeparator();
		URL out = createFileURL(outPath);
		urls.add(out);
		IClasspathEntry[] entries = null;
		try {
			entries = javaProject.getResolvedClasspath(true);
		} catch (JavaModelException e) {
			return;
		}
		IClasspathEntry entry;
		for (int i = 0; i < entries.length; i++) {
			entry = entries[i];
			switch (entry.getEntryKind()) {
			case IClasspathEntry.CPE_LIBRARY:
			case IClasspathEntry.CPE_CONTAINER:
			case IClasspathEntry.CPE_VARIABLE:
				collectClasspathEntryURL(entry, urls);
				break;
			case IClasspathEntry.CPE_PROJECT: {
				if (isFirstProject || entry.isExported())

					collectClasspathURLs(getJavaProject(entry), urls, visited, false);

				break;
			}
			}
		}
	}

	private static URL createFileURL(IPath path)
	{
		URL url = null;
		try {
			url= path.toFile().toURL();
			//url = new URL("file://" + path.toOSString());
		} catch (MalformedURLException e) {
			LogUtils.logError(e);
		}
		return url;
	}

	private static void collectClasspathEntryURL(IClasspathEntry entry, List<URL> urls)
	{
		URL url = createFileURL(entry.getPath());
		if (url != null)
			urls.add(url);
	}

	private static IJavaProject getJavaProject(IClasspathEntry entry)
	{
		IProject proj = ResourcesPlugin.getWorkspace().getRoot().getProject(entry.getPath().segment(0));
		if (proj != null)
			return getJavaProject(proj);
		return null;
	}

	public static IJavaProject getJavaProject(IProject p)
	{
		try {
			return (IJavaProject) p.getNature(JavaCore.NATURE_ID);
		} catch (CoreException ignore) {
			return null;
		}
	}

	/**
	 * Get the binary output absolute (local file system) path.
	 * 
	 * @param p
	 *            project
	 * @return project's output path or <code>null</code> if not java project or
	 *         some other error.
	 * 
	 * @since 1.0.0
	 */
	public static IPath getJavaProjectOutputAbsoluteLocation(IProject p)
	{
		IContainer container = getJavaProjectOutputContainer(p);
		if (container != null)
			return container.getLocation();
		return null;
	}

	/**
	 * Get the project's binary output container.
	 * 
	 * @param p
	 *            project
	 * @return project's output container or <code>null</code> if not java project or some other error.
	 * 
	 * @since 1.0.0
	 */
	public static IContainer getJavaProjectOutputContainer(IProject p) {
		IPath path = getJavaProjectOutputLocation(p);
		if (path == null)
			return null;
		if (path.segmentCount() == 1)
			return p;
		return p.getFolder(path.removeFirstSegments(1));
	}

	/**
	 * Return the location of the binary output files for the JavaProject.
	 * 
	 * @param p
	 *            project
	 * @return path to binary output folder or <code>null</code> if not java project or other problem.
	 * 
	 * @since 1.0.0
	 */
	public static IPath getJavaProjectOutputLocation(IProject p) {
		try {
			IJavaProject javaProj = getJavaProject(p);
			if (javaProj == null)
				return null;
			if (!javaProj.isOpen())
				javaProj.open(null);
			return javaProj.getOutputLocation();
		} catch (JavaModelException e) {
			return null;
		}
	}	

	/**
	 * Return an IType (Source type, not Binary) for the given class name.
	 * 
	 * @return null if no such class can be found.
	 * @throws JavaModelException
	 */
	public static IType findType(IJavaProject javaProject, final String className) throws JavaModelException {
		
		String primaryName= className;
		int occurence= 0;
		IType primaryType= null;
		int i= primaryName.lastIndexOf('$');
		if (0 < i) {
			try {
				occurence= Integer.parseInt(primaryName.substring(i+1));
				primaryName= primaryName.substring(0, primaryName.indexOf('$'));
			}
			catch (NumberFormatException x) {
			}
		}

		
		/*
		 * IJavaProject.findType works for top level classes and named inner
		 * classes, but not for anonymous inner classes.  
		 */
		primaryType= javaProject.findType(primaryName);
		
		if (!primaryType.exists())
			return null; // we failed to find the containing type
		
		if (occurence <= 0) // if not anonymous then we done
			return primaryType;

		/*
		 * If we're looking for an anonymous inner class then we need to look 
		 * through the primary type for it. 
		 */
		LinkedList<IJavaElement> todo= new LinkedList<IJavaElement>();
		todo.add(primaryType);
		IType innerType= null;
		while (!todo.isEmpty()) {
			IJavaElement element= todo.removeFirst();

			if (element instanceof IType) {
				IType type= (IType)element;
				String name= type.getFullyQualifiedName();
				if (name.equals(className)) {
					innerType= type;
					break;
				}
			}

			if (element instanceof IParent) {
				for (IJavaElement child:((IParent)element).getChildren()) {
					todo.add(child);

				}
			}
		}

		return innerType;
	}

	public static IMethod findMethod(IType type, String methodSig) throws JavaModelException {
		String methodName= methodSig.substring(0, methodSig.indexOf('(')); 
		methodName= methodName.substring(methodName.lastIndexOf('.')+1); 
		
		String[] params= JDTUtils.parseForParameterTypes(methodSig);
		IMethod[] methods= type.getMethods();
		IMethod matchingMethod= null;
		for (IMethod method:methods) {
			if (methodName.equals(method.getElementName())) {
				String[] parameterTypes= method.getParameterTypes();
				if (parameterTypes.length == params.length) {
					boolean allParametersMatch= true;
					for (int i= 0; allParametersMatch && i < params.length; i++) {
						String p= params[i];
						String q= parameterTypes[i];
						if (!p.equals(q)) {
							if ((p.startsWith("L") || p.startsWith("Q")) && (q.startsWith("L") || q.startsWith("Q"))) {
								p= p.substring(1);
								q= q.substring(1);
								
								// remove type parameters
								while (p.contains("<")) {
									p= p.substring(0, p.indexOf("<"))+p.substring(p.indexOf(">")+1);
								}
								while (q.contains("<")) {
									q= q.substring(0, q.indexOf("<"))+q.substring(q.indexOf(">")+1);
								}
								
								if (!p.equals(q) && !(p.endsWith(q) || q.endsWith(p)))
									allParametersMatch= false;
							}
							else
								allParametersMatch= false;
						}
					}
					if (allParametersMatch) {
						return method;
					}
				}
			}
		}
		return matchingMethod;
	}
	
	/*
	 * @return true if the given class name denotes an anonymous class.
	 */
	public static boolean isAnonymousType(String className) {
		int i= className.lastIndexOf('$');
		if (0 < i) {
			try {
				Integer.parseInt(className.substring(i+1));
				return true;
			}
			catch (NumberFormatException x) {
			}
		}
		
		return false;
	}	
	
	/*
	 * If the given class name denotes an inner class then this method returns 
	 * the containing type.
	 * If the name denotes an anonymous type then the containg top-level type 
	 * or a named inner type is returned.  
	 */
	public static String findOuterType(String className) {
		String containingType= className;
		int i= containingType.lastIndexOf('$');
		if (i <= 0)
			return className; // not an inner type

		containingType= containingType.substring(0, i);
		int occurence= 0;
		try {
			occurence= Integer.parseInt(containingType.substring(i+1));
		}
		catch (NumberFormatException x) {
		}
		
		if (occurence <= 0)
			return containingType; // containingType type is not anonymous, we can return it.
		
		return findOuterType(containingType);
	}
	

	public static IMarker[] findJavaErrorMarkers(IJavaProject javaProject, IType type) throws CoreException {
		IProject project= javaProject.getProject();
		ArrayList<IMarker> list= new ArrayList<IMarker>();
		IMarker [] markers = project.findMarkers(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER, false, IResource.DEPTH_INFINITE);
		for (int a = 0; a < markers.length; a++)
		{
			IMarker marker = markers[a];
			Object severity = marker.getAttribute(IMarker.SEVERITY);
			if (((Integer) severity).intValue() == IMarker.SEVERITY_ERROR)
			{
				list.add(marker);
			}
		}
		return list.toArray(new IMarker[list.size()]);
	}

	/**
	 * @return if the given type name is an anonymous type or has an anonymous 
	 * containing type then the innermost containing type which has no 
	 * anonymous containing types is returned. 
	 * A null is returned if the given type is not anonymous and has no 
	 * anonymous containing types.
	 */
	public static String isAnonymousTypeOrHasAnonymousContainingType(String className) {
		for (int i= 0; i < className.length(); i++) {
			char c= className.charAt(i);
			if (Character.isDigit(c) && className.charAt(i-1) == '$') {
				String containingType= className.substring(0, i-1);
				return containingType;
			}
		}
		return null;
	}	
}