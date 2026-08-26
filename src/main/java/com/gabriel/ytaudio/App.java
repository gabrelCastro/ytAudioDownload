package com.gabriel.ytaudio;

import com.gabriel.ytaudio.service.AppSettings;
import com.gabriel.ytaudio.service.ToolLocator;
import com.gabriel.ytaudio.ui.MainView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class App extends Application {

    @Override
    public void start(Stage primaryStage) {
        Stage splash = buildSplash();
        splash.show();

        // Localizar (e, na build do Windows, extrair) yt-dlp/ffmpeg pode levar um tempo
        // real na primeira execução — faz isso em background com a splash visível, em vez
        // de deixar a janela principal travada/sem aparecer nesse meio tempo.
        Thread warmup = new Thread(() -> {
            try {
                AppSettings settings = new AppSettings();
                ToolLocator.locate(settings.getYtDlpPath(), "yt-dlp");
                ToolLocator.locate(settings.getFfmpegPath(), "ffmpeg");
            } catch (Exception ignored) {
                // MainView refaz essa checagem normalmente; falhas aqui não são fatais.
            }
            Platform.runLater(() -> {
                showMainWindow(primaryStage);
                splash.close();
            });
        });
        warmup.setDaemon(true);
        warmup.start();
    }

    private Stage buildSplash() {
        Stage splash = new Stage(StageStyle.TRANSPARENT);
        splash.setAlwaysOnTop(true);

        ImageView icon = new ImageView(new Image(getClass().getResourceAsStream("/icons/icon-128.png")));
        icon.setFitWidth(72);
        icon.setFitHeight(72);

        Label title = new Label("YT Audio Downloader");
        title.getStyleClass().add("app-title");

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setMaxSize(36, 36);

        Label status = new Label("Carregando...");
        status.getStyleClass().add("app-subtitle");

        VBox box = new VBox(16, icon, title, spinner, status);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(36));
        box.getStyleClass().add("card");

        Scene scene = new Scene(box, 320, 220, Color.TRANSPARENT);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        splash.setScene(scene);
        splash.centerOnScreen();
        return splash;
    }

    private void showMainWindow(Stage primaryStage) {
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
        view.setMaximizedButtonLabel(toggleMaximized(primaryStage));

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
