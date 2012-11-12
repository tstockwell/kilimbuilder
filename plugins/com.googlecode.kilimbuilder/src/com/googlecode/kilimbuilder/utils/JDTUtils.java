package com.googlecode.kilimbuilder.utils;

import java.io.ByteArrayInputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.ICommand;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IJavaModelMarker;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IParent;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import com.googlecode.kilimbuilder.KilimBuilder;
import com.googlecode.kilimbuilder.KilimNature;


public class JDTUtils
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
		
		if (primaryType == null || !primaryType.exists())
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
		
		String[] params= parseForParameterTypes(methodSig);
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

	public static IJavaProject getJavaProject(IResource appJar) {
	    if (appJar == null) {
	      throw new IllegalArgumentException("appJar is null");
	    }
	    String projectName = appJar.getProject().getName();
	    return getJavaProject(projectName);
	  }

	public static IJavaProject getJavaProject(String projectName) {
	    if (projectName == null) {
	      throw new IllegalArgumentException("null projectName");
	    }
	    IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();
	    IJavaModel javaModel = JavaCore.create(workspaceRoot);
	    IJavaProject javaProject = javaModel.getJavaProject(projectName);
	    return javaProject;
	  }

	public static int getLineNumber(ICompilationUnit cUnit, int offSet) {
		  return getLineNumFromOffset(cUnit, offSet);
	  }

	/**
	   * Get the line number for the given offset in the given ICompilationUnit
	   *
	   * @param ICompilationUnit
	   * @param int offSet
	   *
	   * @return int lineNumber
	   */
	  private static int getLineNumFromOffset(ICompilationUnit cUnit, int offSet){
		  try {
			  String source = cUnit.getSource();
			  IType type = cUnit.findPrimaryType();
			  if(type != null) {
				  String sourcetodeclaration = source.substring(0, offSet);
				  int lines = 0;
				  char[] chars = new char[sourcetodeclaration.length()];
				  sourcetodeclaration.getChars(
						  0,
						  sourcetodeclaration.length(),
						  chars,
						  0);
				  for (int i = 0; i < chars.length; i++) {
					  if (chars[i] == '\n') {
						  lines++;
					  }
				  }
				  return lines + 1;
			  }
		  } catch (JavaModelException jme) {
			  LogUtils.logError(jme);
		  }
		  return 0;      
	  }

	public static String parseForName(String selector, IType type) {
	    if (selector == null) {
	      throw new IllegalArgumentException("selector is null");
	    }
	    try {
	      String result = selector.substring(0, selector.indexOf('('));
	      if (result.equals("<init>")) {
	        return type.getElementName();
	      } else {
	        return result;
	      }
	    } catch (StringIndexOutOfBoundsException e) {
	      throw new IllegalArgumentException("invalid selector: " + selector);
	    }
	  }

	public static final String[] parseForParameterTypes(String selector) throws IllegalArgumentException {
	
	    try {
	      if (selector == null) {
	        throw new IllegalArgumentException("selector is null");
	      }
	      String d = selector.substring(selector.indexOf('('));
	      if (d.length() <= 2) {
	        throw new IllegalArgumentException("invalid descriptor: " + d);
	
	      }
	      if (d.charAt(0) != '(') {
	        throw new IllegalArgumentException("invalid descriptor: " + d);
	      }
	
	      ArrayList<String> sigs = new ArrayList<String>(10);
	
	      int i = 1;
	      while (true) {
	        switch (d.charAt(i++)) {
	        case VoidTypeCode:
	          sigs.add(VoidName);
	          continue;
	        case BooleanTypeCode:
	          sigs.add(BooleanName);
	          continue;
	        case ByteTypeCode:
	          sigs.add(ByteName);
	          continue;
	        case ShortTypeCode:
	          sigs.add(ShortName);
	          continue;
	        case IntTypeCode:
	          sigs.add(IntName);
	          continue;
	        case LongTypeCode:
	          sigs.add(LongName);
	          continue;
	        case FloatTypeCode:
	          sigs.add(FloatName);
	          continue;
	        case DoubleTypeCode:
	          sigs.add(DoubleName);
	          continue;
	        case CharTypeCode:
	          sigs.add(CharName);
	          continue;
	        case ArrayTypeCode: {
	          int off = i - 1;
	          while (d.charAt(i) == ArrayTypeCode) {
	            ++i;
	          }
	          if (d.charAt(i++) == ClassTypeCode) {
	            while (d.charAt(i++) != ';')
	              ;
	            sigs.add(d.substring(off, i).replaceAll("/", "."));
	          } else {
	            sigs.add(d.substring(off, i));
	          }
	          continue;
	        }
	        case (byte) ')': // end of parameter list
	          return toArray(sigs);
	        default: {
	          // a class
	          int off = i - 1;
	          char c;
	          do {
	            c = d.charAt(i++);
	          } while (c != ',' && c != ')' && c != ';');
	          sigs.add(d.substring(off, i));
	
	          if (c == ')') {
	            return toArray(sigs);
	          }
	
	          continue;
	        }
	        }
	      }
	    } catch (StringIndexOutOfBoundsException e) {
	      throw new IllegalArgumentException("error parsing selector " + selector);
	    }
	  }

	public final static byte ArrayTypeCode = '[';
	/*********************************************************************************************************************
	   * Primitive Dispatch *
	   ********************************************************************************************************************/
	
	  public final static String BooleanName = Boolean.TYPE.getName();
	public final static byte BooleanTypeCode = 'Z';
	public final static String ByteName = Byte.TYPE.getName();
	public final static byte ByteTypeCode = 'B';
	public final static String CharName = Character.TYPE.getName();
	public final static byte CharTypeCode = 'C';
	public final static byte ClassTypeCode = 'L';
	public final static String DoubleName = Double.TYPE.getName();
	public final static byte DoubleTypeCode = 'D';
	public final static String FloatName = Float.TYPE.getName();
	public final static byte FloatTypeCode = 'F';
	public final static String IntName = Integer.TYPE.getName();
	public final static byte IntTypeCode = 'I';
	public final static String LongName = Long.TYPE.getName();
	public final static byte LongTypeCode = 'J';
	public final static byte OtherPrimitiveTypeCode = 'P';
	public final static String ShortName = Short.TYPE.getName();
	public final static byte ShortTypeCode = 'S';
	public final static String VoidName = Void.TYPE.getName();
	public final static byte VoidTypeCode = 'V';
	private static String[] toArray(ArrayList<String> sigs) {
	    int size = sigs.size();
	    if (size == 0) {
	      return new String[0];
	    }
	    Iterator<String> it = sigs.iterator();
	    String[] result = new String[size];
	    for (int j = 0; j < size; j++) {
	      result[j] = it.next();
	    }
	    return result;
	  }

	public static boolean projectContainsNature(IProject project, String natureId) 
	throws CoreException 
	{
		IProjectDescription description = project.getDescription();
		String[] natures = description.getNatureIds();

		for (int i = 0; i < natures.length; ++i) {
			if (KilimNature.KILIM_NATURE_ID.equals(natures[i])) {
				return true;
			}
		}
		return false;
	}

	public static void toggleNature(IProject project, String natureId) 
	throws CoreException 
	{
		if (projectContainsNature(project, natureId)) {
			removeNatureFromProject(project, natureId);
		}
		else
			addNatureToProject(project, natureId);
	}

	public static void addNatureToProject(IProject project, String natureId) throws CoreException {
		IProjectDescription description = project.getDescription();
		String[] natures = description.getNatureIds();

		for (int i = 0; i < natures.length; ++i) {
			if (natureId.equals(natures[i])) {
				return; // project already has nature 
			}
		}

		// Add the nature
		String[] newNatures = new String[natures.length + 1];
		System.arraycopy(natures, 0, newNatures, 0, natures.length);
		newNatures[natures.length] = KilimNature.KILIM_NATURE_ID;
		description.setNatureIds(newNatures);
		project.setDescription(description, null);
	}

	public static void removeNatureFromProject(IProject project, String natureId) throws CoreException {
		IProjectDescription description = project.getDescription();
		String[] natures = description.getNatureIds();

		for (int i = 0; i < natures.length; ++i) {
			if (natureId.equals(natures[i])) {
				// Remove the nature
				String[] newNatures = new String[natures.length - 1];
				System.arraycopy(natures, 0, newNatures, 0, i);
				System.arraycopy(natures, i + 1, newNatures, i,
						natures.length - i - 1);
				description.setNatureIds(newNatures);
				project.setDescription(description, null);
				return;
			}
		}
	}
	

	public static void addClasspathEntryToProject(IJavaProject javaProject, IClasspathEntry classpathEntry) 
	throws CoreException, JavaModelException 
	{
        IClasspathEntry[] rawClasspath = javaProject.getRawClasspath();
		for (int i = 0; i < rawClasspath.length; ++i) {
			if (classpathEntry.getPath().equals(rawClasspath[i].getPath())) {
				return; //already configured;
			}
		}
		
        IClasspathEntry[] newClasspath = new IClasspathEntry[rawClasspath.length+1];
		System.arraycopy(rawClasspath, 0, newClasspath, 0, rawClasspath.length);
        newClasspath[newClasspath.length - 1] = classpathEntry;
        javaProject.setRawClasspath(newClasspath,null);
	}

	public static void addBuilderToProject(IProject project, String builderId) throws CoreException {
		IProjectDescription projectDescription= project.getDescription();
		ICommand[] commands = projectDescription.getBuildSpec();
		for (int i = 0; i < commands.length; ++i) {
			if (commands[i].getBuilderName().equals(KilimBuilder.BUILDER_ID)) {
				return; // already configured
			}
		}
		
		ICommand[] newCommands = new ICommand[commands.length + 1];
		System.arraycopy(commands, 0, newCommands, 0, commands.length);
		ICommand command = projectDescription.newCommand();
		command.setBuilderName(builderId);
		newCommands[newCommands.length - 1] = command;
		projectDescription.setBuildSpec(newCommands);
		project.setDescription(projectDescription, null);
	}
	
	public static void removeBuilderFromProject(IProject project, String builderId) throws CoreException {
		IProjectDescription description = project.getDescription();
		ICommand[] commands = description.getBuildSpec();
		for (int i = 0; i < commands.length; ++i) {
			if (commands[i].getBuilderName().equals(builderId)) {
				ICommand[] newCommands = new ICommand[commands.length - 1];
				System.arraycopy(commands, 0, newCommands, 0, i);
				System.arraycopy(commands, i + 1, newCommands, i,
						commands.length - i - 1);
				description.setBuildSpec(newCommands);
				project.setDescription(description, null);			
				break;
			}
		}
	}
	
	public static void removeClasspathEntryFromProject(IJavaProject javaProject, String classpathEntryPath) 
	throws CoreException, JavaModelException 
	{
        IClasspathEntry[] rawClasspath = javaProject.getRawClasspath();
		for (int i = 0; i < rawClasspath.length; ++i) {
			IClasspathEntry entry= rawClasspath[i];
			IPath path= entry.getPath();
			if (classpathEntryPath.equals(path.toString())) {
		        IClasspathEntry[] newClasspath = new IClasspathEntry[rawClasspath.length-1];
				System.arraycopy(rawClasspath, 0, newClasspath, 0, i);
				System.arraycopy(rawClasspath, i + 1, newClasspath, i, rawClasspath.length - i - 1);
				javaProject.setRawClasspath(newClasspath, null);			
				break;
			}
		}
	}

	public static List<IClasspathEntry> getSourcePaths(IJavaProject javaProject) throws JavaModelException {
        IClasspathEntry[] rawClasspath = javaProject.getRawClasspath();
        ArrayList<IClasspathEntry> paths= new ArrayList<IClasspathEntry>();
		for (int i = 0; i < rawClasspath.length; ++i) {
			IClasspathEntry classpathEntry= rawClasspath[i];
			if (classpathEntry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
				paths.add(classpathEntry);
			}
		}
		return paths;
	}

//	public static void createFolder(IProject project, IFolder outputLocation) throws CoreException {
//		if (outputLocation.exists())
//			return;
//		IContainer container= outputLocation.getParent();
//		if (container != null && !container.exists())
//			createFolder(project, project.getFolder(container.getProjectRelativePath()));
//		outputLocation.create(true, true, null);
//	}

	public static String getLastSegment(IPath projectRelativePath) {
		String[] segments= projectRelativePath.segments();
		if (segments == null || segments.length <= 0)
			return "";
		return segments[segments.length-1];
	}
	

	public static void create(final IResource resource, IProgressMonitor monitor) throws CoreException {
		if (resource == null || resource.exists())
			return;
		if (!resource.getParent().exists())
			create(resource.getParent(), monitor);
		switch (resource.getType()) {
		case IResource.FILE :
			((IFile) resource).create(new ByteArrayInputStream(new byte[0]), true, monitor);
			break;
		case IResource.FOLDER :
			((IFolder) resource).create(IResource.NONE, true, monitor);
			break;
		case IResource.PROJECT :
			((IProject) resource).create(monitor);
			((IProject) resource).open(monitor);
			break;
		}
	}	
}