package com.grubeast;

/**
 * Plain main class. JavaFX apps started straight from a class that extends
 * Application can fail with "JavaFX runtime components are missing" when run from a
 * jar or from VS Code, so everything starts here instead.
 */
public class Launcher {
    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true"); // we only use AWT to resize photos
        App.main(args);
    }
}
