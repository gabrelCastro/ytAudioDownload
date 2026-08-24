package com.gabriel.ytaudio;

/**
 * java -jar refuses to launch a class extending javafx.application.Application
 * directly unless the JavaFX modules are on the module-path. Routing through a
 * plain main class here sidesteps that check, letting the fat jar run standalone.
 */
public class Launcher {
    public static void main(String[] args) {
        App.main(args);
    }
}
