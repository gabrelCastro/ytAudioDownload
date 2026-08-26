package com.gabriel.ytaudio;

import com.gabriel.ytaudio.ui.MainView;
import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.stage.Screen;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage primaryStage) {
        MainView view = new MainView();

        ScrollPane scrollPane = new ScrollPane(view);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("app-scroll");
        view.minHeightProperty().bind(scrollPane.heightProperty());

        Scene scene = new Scene(scrollPane, 1200, 860);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());

        primaryStage.setTitle("YT Audio Downloader");
        for (String size : new String[]{"256", "128", "64", "48", "32", "16"}) {
            primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("/icons/icon-" + size + ".png")));
        }
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(620);

        // O estado "maximizado" nativo (setMaximized) não é aplicado de forma
        // confiável em algumas integrações X11 (ex: WSLg) quando a janela já está
        // visível. Em vez disso, redimensionamos/reposicionamos manualmente para
        // preencher a tela — um resize comum, sem a transição visual do hide()/show().
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.F11) {
                toggleMaximized(primaryStage);
            }
        });

        primaryStage.show();

        primaryStage.setOnCloseRequest(e -> view.shutdown());
    }

    private static double[] restoreBounds;

    /** Manually resizes/repositions the stage to fill the screen (and back), instead of
     *  relying on the native maximized state — see the comment in start() for why. */
    public static boolean toggleMaximized(Stage stage) {
        if (restoreBounds != null) {
            stage.setX(restoreBounds[0]);
            stage.setY(restoreBounds[1]);
            stage.setWidth(restoreBounds[2]);
            stage.setHeight(restoreBounds[3]);
            restoreBounds = null;
            return false;
        }
        restoreBounds = new double[]{stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight()};
        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        stage.setX(bounds.getMinX());
        stage.setY(bounds.getMinY());
        stage.setWidth(bounds.getWidth());
        stage.setHeight(bounds.getHeight());
        return true;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
