package com.googlecode.kilimbuilder.utils;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.osgi.util.ManifestElement;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;

@SuppressWarnings("unchecked")
public class OsgiUtils
{
	final static private Map<Bundle, Set<ManifestElement>> __dependenciesByBundle= new WeakHashMap<Bundle, Set<ManifestElement>>();
	final static private Map<Bundle, List<ManifestElement>> __requiredBundles= new WeakHashMap<Bundle, List<ManifestElement>>();
	final static private Map<Bundle, List<ManifestElement>> __exportedPackages= new WeakHashMap<Bundle, List<ManifestElement>>();
	final static private Map<Bundle, List<ManifestElement>> __importedPackages= new WeakHashMap<Bundle, List<ManifestElement>>();
	

	/**
	 * sorts bundles by dependencies such that is bundle A is dependent 
	 * on bundle B then bundle B appears in the array before bundle A.
	 */
	public static Bundle[] sortBundlesByDependencies(Bundle[] bundles) 
	{
		final Map<String, Bundle> bundleRegistry= new HashMap<String, Bundle>();
		for (Bundle bundle : bundles)
			bundleRegistry.put(bundle.getSymbolicName(), bundle);
		
		/*
		 * not any sort will work, gotta be sure that all elements will be compared.
		 * I think that maybe this is because the dependency relation between 
		 * bundles is not transitive.
		 */
		bubbleSort(bundles, new Comparator<Bundle>() {
			/**
			 * Returns -1 if bundle1 is dependent on bundle2.
			 * Returns 1 if bundle2 is dependent on bundle1.
			 * Returns 0 otherwise;
			 */
			@Override public int compare(Bundle b1, Bundle b2) 
			{
				try 
				{
					String name2= b2.getSymbolicName();
					for (ManifestElement element : getAllDependencies(b1, bundleRegistry))
					{
						if (name2.equals(element.getValue()))
							return 1;
					}

					String name1= b1.getSymbolicName();
					for (ManifestElement element : getAllDependencies(b2, bundleRegistry))
					{
						if (name1.equals(element.getValue()))
							return -1;
					}
				} 
				catch (BundleException e) 
				{
					e.printStackTrace();
				}
				return 0;
			}
		});
		
		return bundles;
	}
	
	public static <T> void bubbleSort(T[] items, Comparator<T> comparator) 
	{
		int n= items.length;
		boolean swapped;
		do 
		{
			swapped= false;
			n--;
			for (int i= 0; i < n; i++) 
			{
				if (0 <= comparator.compare(items[i], items[i+1])) 
				{
					T t= items[i];
					items[i]= items[i+1];
					items[i+1]= t;
					swapped= true;
				}
			}
		} while (swapped);
	}
	

	/**
	 * Returns names of bundles on which the given Bundle is dependent.
	 * Is recursive, so it returns all dependencies, not just those listed by the bundle itself.
	 * @throws BundleException
	 */
	public static Set<ManifestElement> getAllDependencies(Bundle bundle) throws BundleException {
		Set<ManifestElement> dependencies= __dependenciesByBundle.get(bundle);
		if (dependencies == null) {
			final Map<String, Bundle> bundleRegistry= new HashMap<String, Bundle>();
			BundleContext bundleContext= bundle.getBundleContext();
			if (bundleContext != null) {
				for (Bundle b : bundleContext.getBundles())
					bundleRegistry.put(b.getSymbolicName(), b);
				dependencies= getAllDependencies(bundle, bundleRegistry);
				__dependenciesByBundle.put(bundle,dependencies);
			}
			else
				dependencies= Collections.EMPTY_SET;
		}
		

		return dependencies;
	}
	private static Set<ManifestElement> getAllDependencies(Bundle bundle, Map<String, Bundle> bundleRegistry) 
	throws BundleException 
	{
		Set<ManifestElement> dependencies= __dependenciesByBundle.get(bundle);
		if (dependencies == null) {
			dependencies= new HashSet<ManifestElement>();
			
			List<ManifestElement> requiredBundles= getRequiredBundles(bundle);
			for (ManifestElement bundleName : requiredBundles) {
				Bundle bundle2= bundleRegistry.get(bundleName.getValue());
				if (bundle2 != null) {
					dependencies.add(bundleName);
					Set<ManifestElement> dependencies2= getAllDependencies(bundle2, bundleRegistry);
					dependencies.addAll(dependencies2);
				}
			}
			
			List<ManifestElement> importedPackages= getImportedPackages(bundle);
			for (ManifestElement importedPackage : importedPackages) {
				Bundle bundle2= getBundleThatExportsPackage(importedPackage, bundleRegistry);
				if (bundle2 != null) {
					dependencies.add(importedPackage);
					Set<ManifestElement> dependencies2= getAllDependencies(bundle2, bundleRegistry);
					dependencies.addAll(dependencies2);
				}
			}
			
			__dependenciesByBundle.put(bundle,dependencies);
		}
		

		return dependencies;
	}
	
	private static Bundle getBundleThatExportsPackage(ManifestElement importedPackage, Map<String, Bundle> bundleRegistry) 
	throws BundleException 
	{
		String packageName= importedPackage.getValue();
		for (Bundle bundle : bundleRegistry.values())
		{
			List<ManifestElement> exportedPackages= getExportedPackages(bundle);
			for (ManifestElement element : exportedPackages)
			{
				if (element.getValue().equals(packageName))
					return bundle;
			}
		}
		return null;
	}

	public static final List<ManifestElement> getRequiredBundles(Bundle bundle) 
	throws BundleException 
	{
		List<ManifestElement> results= __requiredBundles.get(bundle);
		if (results == null) {
			
			String header= (String)bundle.getHeaders().get(Constants.REQUIRE_BUNDLE);
			if (header == null)
				return Collections.EMPTY_LIST;
			ManifestElement[] exportedPackages = 
				ManifestElement.parseHeader(Constants.REQUIRE_BUNDLE, header);
			if (exportedPackages == null) 
				return Collections.EMPTY_LIST;
			results= new ArrayList<ManifestElement>();
			for (ManifestElement element : exportedPackages)
				results.add(element);
			
			__requiredBundles.put(bundle, results);
		}
		
		return results;
	}
	
	
	public static final List<ManifestElement> getExportedPackages(Bundle bundle) 
	throws BundleException 
	{
		List<ManifestElement> results= __exportedPackages.get(bundle);
		if (results == null) {
			String header= (String)bundle.getHeaders().get(Constants.EXPORT_PACKAGE);
			if (header == null)
				return Collections.EMPTY_LIST;
			ManifestElement[] exportedPackages = 
				ManifestElement.parseHeader(Constants.EXPORT_PACKAGE, header);
			if (exportedPackages == null) 
				return Collections.EMPTY_LIST;
			results= new ArrayList<ManifestElement>();
			for (ManifestElement element : exportedPackages)
				results.add(element);
			__exportedPackages.put(bundle, results);
		}
		return results;
	}
	
	
	public static final List<ManifestElement> getImportedPackages(Bundle bundle) 
	throws BundleException 
	{
		List<ManifestElement> results= __importedPackages.get(bundle);
		if (results == null) {
			String header= (String)bundle.getHeaders().get(Constants.IMPORT_PACKAGE);
			if (header == null)
				return Collections.EMPTY_LIST;
			ManifestElement[] exportedPackages = 
				ManifestElement.parseHeader(Constants.IMPORT_PACKAGE, header);
			if (exportedPackages == null) 
				return Collections.EMPTY_LIST;
			results= new ArrayList<ManifestElement>();
			for (ManifestElement element : exportedPackages)
				results.add(element);
			__importedPackages.put(bundle, results);
		}
		return results;
	}
	
	public static final List<URL> getBundleClasspathURLs(Bundle bundle) 
	throws BundleException 
	{
		List<URL> urls = new ArrayList<URL>();

		// declared classpath
		String headers = (String) bundle.getHeaders().get(Constants.BUNDLE_CLASSPATH);

		// TWS - OSGi spec says that if no classpath is specified then ./ is assumed
		if (headers == null || headers.length() <= 0) {
			headers="/";
		}

		ManifestElement[] paths = 
				ManifestElement.parseHeader(Constants.BUNDLE_CLASSPATH, headers);

		if (paths != null) {
			for (int i = 0; i < paths.length; i++) {
				String path = paths[i].getValue();
				URL url = bundle.getEntry(path);
				if (url != null)
					try {
						URL url2= FileLocator.resolve(url);
						if (!urls.contains(url2))
							urls.add(url2);
					} catch (Throwable t) {
					}
			}
		}
		
		return urls;
	}
	
	
}
