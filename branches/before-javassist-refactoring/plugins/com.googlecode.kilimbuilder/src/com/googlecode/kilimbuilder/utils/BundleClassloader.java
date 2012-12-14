/*******************************************************************************
 * Copyright (c) 2003-2005 Ted Stockwell <emorning@yahoo.com>
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Ted Stockwell - initial API and implementation
 *******************************************************************************/
package com.googlecode.kilimbuilder.utils;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.core.internal.runtime.DevClassPathHelper;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.osgi.util.ManifestElement;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;

/**
 * A classloader that wraps a set of OSGi bundles.
 * 
 * Also provides correct list of URLs.
 * This is required when other compoenents, like a JSP compiler, use the classloader 
 * to get a list of URLs for compilation purposes.  
 * 
 * @author ted stockwell
 */
@SuppressWarnings("unchecked")
public class BundleClassloader extends URLClassLoader {
    private ArrayList<Bundle> _bundles= new ArrayList<Bundle>(); 
    private HashMap<String, Bundle> _bundlesByName= new HashMap<String, Bundle>();
    private HashMap<String, Bundle> _bundlesByPackage= new HashMap<String, Bundle>();
    private HashMap<String, Class> _classesByName= new HashMap<String, Class>();
    private HashMap<String, URL> _resourceByName= new HashMap<String, URL>();
    
    private URL[] _urls= new URL[0];

    public BundleClassloader() {
        super(new URL[0]);
    }

    @Override synchronized public Class<?> loadClass(String className) 
    throws ClassNotFoundException 
    {
        Class<?> c = _classesByName.get(className);
        if (c == null) {
        	String packageName= "";
        	int l= className.lastIndexOf('.');
        	if (0 <= l)
        		packageName= className.substring(0, l);
        	
        	Bundle bundle= _bundlesByPackage.get(packageName);
        	if (bundle != null) {
        		try {
        			c= bundle.loadClass(className);
                } 
        		catch (ClassNotFoundException e) {
                }
        	}
        	
            for (Bundle bundle2 : _bundles) {
                try {
                	c = bundle2.loadClass(className);
                } catch (Throwable e) {
                }
            }
    	        
            if (c == null)
                throw new ClassNotFoundException(className);
            
            _classesByName.put(className, c);
        }

        return c;
    }

    @Override synchronized public URL getResource(String resName) {
        URL url = _resourceByName.get(resName);
        
        if (url == null) {
            for (Iterator<Bundle> i= _bundles.iterator(); i.hasNext() && url == null;) {
            	Bundle bundle= i.next();
                url = bundle.getResource(resName);
            }
            
            for (Iterator<Bundle> i= _bundles.iterator(); i.hasNext() && url == null;) {
            	Bundle bundle= i.next();
                url = bundle.getEntry(resName);
            }
            
            if (url != null) {
            	try {
            		url= FileLocator.resolve(url);
            	}
            	catch (Throwable t) {            		
            	}
            	_resourceByName.put(resName, url);
            }
        }
        
        return url;
    }
    
    @Override
    public Enumeration<URL> getResources(String name) throws IOException
    {
    	ArrayList<URL> list= new ArrayList<URL>();
        for (Iterator<Bundle> i= _bundles.iterator(); i.hasNext();) {
        	Bundle bundle= i.next();
        	
        	{
        		Enumeration<URL> e = bundle.getResources(name);
	            if (e != null)
	            	while (e.hasMoreElements())
	            		list.add(FileLocator.resolve(e.nextElement()));
        	}
            
            {
            	Enumeration<String> e = bundle.getEntryPaths(name);
	            if (e != null)
	            	while (e.hasMoreElements()) {
	            		String path= e.nextElement();
	            		URL entry= bundle.getEntry(path);
	            		list.add(FileLocator.resolve(entry));
	            	}
            }
        }
        return Collections.enumeration(list);
    }
    
    public synchronized void addBundle(Bundle bundle) 
    throws BundleException 
    {
    	String bundleName= bundle.getSymbolicName();
    	if (!_bundlesByName.containsKey(bundleName)) {
    		
    		_bundles.add(bundle);
    		_bundlesByName.put(bundleName, bundle);
    		
    		List<ManifestElement> packages= OsgiUtils.getExportedPackages(bundle);
    		for (ManifestElement packageName : packages)
    			_bundlesByPackage.put(packageName.getValue(), bundle);
    	}
    	
    	addBundleURLs(bundle);
    }

	public boolean containsBundle(Bundle p_bundle) {
    	return _bundlesByName.containsKey(p_bundle.getSymbolicName());
	}

	public Set<ManifestElement> getAllDependentPackages(String packageName) 
	throws BundleException
	{
		Bundle bundle= getBundleThatExportsPackage(packageName);
		if (bundle == null) // will be null if package is not exported
			return Collections.EMPTY_SET;
		Set<ManifestElement> bundleDependencies= OsgiUtils.getAllDependencies(bundle);
		HashSet<ManifestElement> dependentPackages= new HashSet<ManifestElement>();
		for (ManifestElement bundleName : bundleDependencies) { 
			Bundle bundle2= _bundlesByName.get(bundleName.getValue());
			if (bundle2 != null)
				dependentPackages.addAll(OsgiUtils.getExportedPackages(bundle2));
		}
		return dependentPackages;
	}

	public Bundle getBundleThatExportsPackage(String packageName)
	{
		return _bundlesByPackage.get(packageName);
	}
	

	private void addBundleURLs(Bundle b)
	{
		try {
			Set<URL> urls = new HashSet<URL>();

			// declared classpath
			String headers = (String) b.getHeaders().get(Constants.BUNDLE_CLASSPATH);

			// TWS - OSGi spec says that if no classpath is specified then ./ is assumed
			if (headers == null || headers.length() <= 0) {
				headers="/";
			}

			ManifestElement[] paths = ManifestElement.parseHeader(
					Constants.BUNDLE_CLASSPATH, headers);

			if (paths != null) {
				for (int i = 0; i < paths.length; i++) {
					String path = paths[i].getValue();
					URL url = b.getEntry(path);
					if (url != null)
						try {
							URL url2= FileLocator.resolve(url);
							urls.add(url2);
						} catch (Throwable t) {
						}
				}
			}

			// dev classpath
			String[] devpaths = DevClassPathHelper.getDevClassPath(b.getSymbolicName());
			if (devpaths != null) {
				for (int i = 0; i < devpaths.length; i++) {
					URL url = b.getEntry(devpaths[i]);
					if (url != null)
							urls.add(FileLocator.resolve(url));
				}
			}

			URL[] urls2= new URL[urls.size()+_urls.length];
			URL[] urls3= urls.toArray(new URL[urls.size()]);
			System.arraycopy(_urls, 0, urls2, 0, _urls.length);
			System.arraycopy(urls3, 0, urls2, _urls.length, urls3.length);
			_urls= urls2;
		} 
		catch (IOException e)
		{
			throw new RuntimeException("Error creating classpath.", e);
		}
		catch (BundleException e)
		{
			throw new RuntimeException("Error creating classpath.", e);
		}
	}
	
	
	/**
	 * This is a workaround for clients that need to know the correct classpath.
	 */
	public URL[] getURLs() {
		return _urls;
	}
}
