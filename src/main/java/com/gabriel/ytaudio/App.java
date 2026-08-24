package com.gabriel.ytaudio;

import com.gabriel.ytaudio.ui.MainView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage primaryStage) {
        MainView view = new MainView();

        ScrollPane scrollPane = new ScrollPane(view);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("app-scroll");

        Scene scene = new Scene(scrollPane, 1200, 860);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());

        primaryStage.setTitle("YT Audio Downloader");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(620);
        primaryStage.show();

        primaryStage.setOnCloseRequest(e -> view.shutdown());
    }

    public static void main(String[] args) {
        launch(args);
    }
}
