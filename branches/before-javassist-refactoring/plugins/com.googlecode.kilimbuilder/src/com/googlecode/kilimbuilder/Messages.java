package com.googlecode.kilimbuilder;

import org.eclipse.osgi.util.NLS;

public class Messages extends NLS {
    private static final String BUNDLE_NAME = Messages.class.getName().toLowerCase();
    
    public static String LibraryLabel;

    public static String Browse;
    
    public static String DirErr;
    
    public static String DirLabel;
    
    public static String DirSelect;
    
    public static String ExtErr;
    
    public static String ExtLabel;
    
    public static String InvalidContainer;
    
    public static String PageDesc;
    
    public static String PageName;

    public static String PageTitle;
    
        

    static {
        // initialize resource bundle
        NLS.initializeMessages(BUNDLE_NAME, Messages.class);
    }

    private Messages() {
    }
}
