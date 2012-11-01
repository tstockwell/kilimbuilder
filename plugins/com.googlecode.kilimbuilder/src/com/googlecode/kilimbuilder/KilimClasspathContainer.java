package com.googlecode.kilimbuilder;

import java.io.IOException;
import java.net.URL;
import java.util.List;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.packageadmin.PackageAdmin;
import org.osgi.service.packageadmin.RequiredBundle;

import com.googlecode.kilimbuilder.utils.OsgiUtils;

/**  
 * This classpath container adds kilim libraries to the classpath as CPE_LIBRARY entries.
 * The library will include the classpath entries from the kilim bundle installed 
 * with the kilim builder. 
 */
@SuppressWarnings("deprecation")
public class KilimClasspathContainer implements IClasspathContainer {
    public final static Path CONTAINER_PATH = new Path("com.googlecode.kilimbuilder.KILIM_CONTAINER");
    
    // path string that uniquely identifies this container instance
    private IPath _path;
    private IClasspathEntry[] _classpathEntries;
  
   
    public KilimClasspathContainer(IPath path, IJavaProject project) throws IOException, BundleException {
        _path = path;
        
    	Bundle kilimBuilderBundle= KilimActivator.getDefault().getBundle();
    	BundleContext bundleContext= kilimBuilderBundle.getBundleContext();
    	
    	// Get package admin service.
        ServiceReference<PackageAdmin> ref = bundleContext.getServiceReference(PackageAdmin.class);
        PackageAdmin packageAdmin= bundleContext.getService(ref);
        RequiredBundle[] requiredBundles= packageAdmin.getRequiredBundles(kilimBuilderBundle.getSymbolicName());
        Bundle kilimBundle= null;
        for (RequiredBundle requiredBundle:requiredBundles) {
        	if (requiredBundle.getSymbolicName().equals("kilim")) {
        		kilimBundle= requiredBundle.getBundle();
        	}
        }
        if (kilimBundle == null)
        	throw new RuntimeException("Failed to find kilim bundle");
        
        
        List<URL> urls= OsgiUtils.getBundleClasspathURLs(kilimBundle);
        IClasspathEntry[] entryArray = new IClasspathEntry[urls.size()];
        int i= 0;
        for (URL url:urls) {
            IPath containerPath= new Path(Platform.asLocalURL(url).getPath());
            IClasspathEntry entry= JavaCore.newContainerEntry(containerPath);
            entryArray[i++]= entry;
        }
        _classpathEntries= entryArray;
    }
    
    public boolean isValid() {
    	return true;
    }
    
    /** 
     * This method cal 
     * that conform to the configured set of file extensions and attaches a source 
     * archive to the libraries entries if a file with same name ending with 
     * -src is found in the directory. 
     * 
     * @see org.eclipse.jdt.core.IClasspathContainer#getClasspathEntries()
     */
    public IClasspathEntry[] getClasspathEntries() {
        return _classpathEntries;
    }
    
    public String getDescription() {
        return Messages.LibraryLabel;
    }
    
    public int getKind() {
        return IClasspathContainer.K_APPLICATION;
    }    
    
    public IPath getPath() {
        return _path;
    }
    
}
