Kilim is a framework for Java that provides ultra-lightweight threads and facilities for sending messages between these threads.
Kilim uses bytecode instrumentation to weave thread management into your code.  This Eclipse plugin makes it easy to add Kilim support to your Eclipse Java projects.


Just install this plugin and then add the Kilim 'nature' to your Eclipse Java project.
This plugin automatically adds a Kilim library to your project, and it adds an Eclipse builder that automagically instruments your Java classes to work with Kilim.

See http://www.malhar.net/sriram/kilim/ for more information about Kilim.

## Kilim Builder Update Site ##
http://kilimbuilder.googlecode.com/svn/trunk/plugins/com.googlecode.kilimbuilder.updatesite/

## Requirements ##
  * Eclipse 4.2.1
    * Kilim Builder probably works with Eclipse versions 4.0 and higher but has only been tested with Eclipse 4.2.1
    * Kilim Builder is known to NOT WORK with Eclipse version prior to version 4.0
  * Java 1.6
    * Kilim is known to NOT WORK with Java 1.7 and Java versions prior to 1.6.

## Features ##
  * Automatically generates classes with Kilim bytecode instrumentation
  * Marks Kilim problems in Eclipse
> > http://kilimbuilder.googlecode.com/svn/wiki/kilim-error.PNG

## Usage ##
  * Kilim Builder creates a folder named 'instrumented/kilim' in the folder that contains your project's output folder, usually your project's root folder.
  * Easy to run OSGi plugin-based applications with instrumentation - just add instrumented/kilim/ to the Bundle-ClassPath header in your plugins' manifest.mf file.
  * Other types of Eclipse launch configurations require manually adjusting the classpath to pick up instrumented classes.

## Discuss ##
  * Issues regarding the Kilim Builder plugin may be discussed on the kilimthreads Google Group: https://groups.google.com/group/kilimthreads?hl=en