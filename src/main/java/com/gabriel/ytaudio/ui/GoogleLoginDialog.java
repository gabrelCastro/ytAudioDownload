package com.gabriel.ytaudio.ui;

import com.gabriel.ytaudio.service.CookieExporter;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
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

        Label hint = new Label("Faça login normalmente. Quando terminar (a página do YouTube carregar), clique em \"Concluí o login\".");
        hint.setWrapText(true);
        hint.setMaxWidth(320);

        Button doneButton = new Button("Concluí o login");
        Button cancelButton = new Button("Cancelar");

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
        bottom.setPadding(new Insets(10));

        BorderPane root = new BorderPane();
        root.setCenter(webView);
        root.setBottom(bottom);

        stage.setScene(new Scene(root, 480, 680));
        stage.showAndWait();
    }
}
