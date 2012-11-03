package com.googlecode.kilimbuilder;

import org.eclipse.ui.IStartup;

/**
 * Merely specifying a startup class causes Kilim to be activated at startup.
 * I want the Kilim Nature toogle action to be dynamic, it is necessary to activate 
 * the plugin for that to happen.
 */
public class KilimStartup implements IStartup {

	@Override
	public void earlyStartup() {
	}

}
