package com.gabriel.ytaudio.ui;

import com.gabriel.ytaudio.service.CookieExporter;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.nio.file.Path;
import java.util.function.Consumer;

/** Lets the user log into their Google account through a real (JavaFX WebView) browser
 *  embedded in the app, then exports the resulting session cookies to a cookies.txt file
 *  yt-dlp can use — sidesteps yt-dlp's flaky --cookies-from-browser DPAPI/lock issues.
 *  Experimental: Google sometimes blocks sign-in from embedded browsers outright. */
public class GoogleLoginDialog {

    private static boolean cookieHandlerInstalled = false;

    private static synchronized void ensureCookieHandler() {
        if (!cookieHandlerInstalled) {
            CookieHandler.setDefault(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
            cookieHandlerInstalled = true;
        }
    }

    public static void show(Stage owner, Consumer<String> onCookiesSaved) {
        ensureCookieHandler();

        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Entrar com Google");

        WebView webView = new WebView();
        webView.getEngine().load("https://accounts.google.com/ServiceLogin?service=youtube&continue=https://www.youtube.com/");

        ProgressIndicator loadingIndicator = new ProgressIndicator();
        loadingIndicator.setMaxSize(48, 48);
        webView.getEngine().getLoadWorker().runningProperty().addListener((obs, was, running) ->
                loadingIndicator.setVisible(running));

        StackPane webStack = new StackPane(webView, loadingIndicator);
        StackPane.setAlignment(loadingIndicator, Pos.CENTER);

        Label hint = new Label("Faça login normalmente. Quando terminar (a página do YouTube carregar), clique em \"Concluí o login\".");
        hint.getStyleClass().add("app-subtitle");
        hint.setWrapText(true);
        hint.setMaxWidth(280);
        HBox.setHgrow(hint, Priority.ALWAYS);

        Button doneButton = new Button("Concluí o login");
        doneButton.getStyleClass().add("primary-button");
        Button cancelButton = new Button("Cancelar");
        cancelButton.getStyleClass().add("secondary-button");

        doneButton.setOnAction(e -> {
            try {
                CookieManager manager = (CookieManager) CookieHandler.getDefault();
                Path outFile = CookieExporter.defaultCookiesFile();
                CookieExporter.exportNetscape(manager.getCookieStore(), outFile);
                stage.close();
                onCookiesSaved.accept(outFile.toString());
            } catch (Exception ex) {
                Alert alert = new Alert(Alert.AlertType.ERROR,
                        "Não foi possível salvar os cookies: " + ex.getMessage(), ButtonType.OK);
                alert.showAndWait();
            }
        });
        cancelButton.setOnAction(e -> stage.close());

        HBox bottom = new HBox(10, hint, cancelButton, doneButton);
        bottom.setAlignment(Pos.CENTER_LEFT);
        bottom.setPadding(new Insets(14));
        bottom.getStyleClass().add("card");

        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");
        root.setPadding(new Insets(12));
        root.setCenter(webStack);
        root.setBottom(bottom);
        BorderPane.setMargin(bottom, new Insets(12, 0, 0, 0));

        Scene scene = new Scene(root, 480, 700);
        scene.getStylesheets().add(GoogleLoginDialog.class.getResource("/style.css").toExternalForm());
        stage.setScene(scene);
        stage.showAndWait();
    }
}
