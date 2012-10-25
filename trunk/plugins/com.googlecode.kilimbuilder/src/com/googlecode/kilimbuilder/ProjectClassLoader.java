package com.googlecode.kilimbuilder;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaModelException;

/**
 * Creates a normal ClassLoader for an Eclipse Java Project
 * Code from the book 'Eclipse Plugins': http://www.qualityeclipse.com/
 * Modified for use in Kilim Builder
 */
public class ProjectClassLoader extends ClassLoader
{
    private IJavaProject project;

    public ProjectClassLoader(IJavaProject project) {
       if (project == null || !project.exists() || !project.isOpen())
          throw new IllegalArgumentException("Invalid project");
       this.project = project;
    }

    protected Class<?> findClass(String name)
        throws ClassNotFoundException
    {
        byte[] buf = readBytes(name);
        if (buf == null)
           throw new ClassNotFoundException(name);
        return defineClass(name, buf, 0, buf.length);
    }

    private byte[] readBytes(String name) {
       IPath rootLoc = ResourcesPlugin
          .getWorkspace().getRoot().getLocation();
       Path relativePathToClassFile =
          new Path(name.replace(".","/") + ".class");
       IClasspathEntry[] entries;
       IPath outputLocation;
       try {
          entries = project.getResolvedClasspath(true);
          outputLocation =
             rootLoc.append(project.getOutputLocation());
       }
       catch (JavaModelException e) {
          LogUtils.logError(e);
          return null;
       }
       for (int i = 0; i < entries.length; i++) {
          IClasspathEntry entry = entries[i];
          switch (entry.getEntryKind()) {

             case IClasspathEntry.CPE_SOURCE : {
                IPath path = entry.getOutputLocation();
                if (path != null)
                   path = rootLoc.append(path);
                else
                   path = outputLocation;
                path = path.append(relativePathToClassFile);
                byte[] buf = readBytes(path.toFile());
                if (buf != null)
                   return buf;
             } break;
             case IClasspathEntry.CPE_LIBRARY: {
                 IPath path = entry.getOutputLocation();
                 if (path != null)
                    path = rootLoc.append(path);
                 else
                    path = outputLocation;
                 path = path.append(relativePathToClassFile);
                 byte[] buf = readBytes(path.toFile());
                 if (buf != null)
                    return buf;
             } break;
             case IClasspathEntry.CPE_PROJECT:
                // Handle other entry types here.
                break;

             default :
                break;
          }
       }
       return null;
   }

   private static byte[] readBytes(File file) {
      if (file == null || !file.exists())
         return null;
      InputStream stream = null;
      try {
         stream =
            new BufferedInputStream(
               new FileInputStream(file));
         int size = 0;
         byte[] buf = new byte[10];
         while (true) {
            int count =
               stream.read(buf, size, buf.length - size);
            if (count < 0)
               break;
            size += count;
            if (size < buf.length)
               break;
            byte[] newBuf = new byte[size + 10];
            System.arraycopy(buf, 0, newBuf, 0, size);
            buf = newBuf;
         }
         byte[] result = new byte[size];
         System.arraycopy(buf, 0, result, 0, size);
         return result;
      }
      catch (Exception e) {
    	  LogUtils.logError(e);
         return null;
      }
      finally {
         try {
            if (stream != null)
               stream.close();
         }
         catch (IOException e) {
        	 LogUtils.logError(e);
            return null;
         }
      }
   }
}