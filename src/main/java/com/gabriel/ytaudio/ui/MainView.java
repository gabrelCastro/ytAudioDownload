package com.gabriel.ytaudio.ui;

import com.gabriel.ytaudio.model.VideoItem;
import com.gabriel.ytaudio.service.AppSettings;
import com.gabriel.ytaudio.service.ToolLocator;
import com.gabriel.ytaudio.service.YtDlpService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.*;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class MainView extends BorderPane {

    private final AppSettings settings = new AppSettings();
    private final ObservableList<VideoItem> items =
            FXCollections.observableArrayList(item -> new javafx.beans.Observable[]{item.selectedProperty()});
    private final java.util.Map<String, javafx.scene.image.Image> thumbnailCache = new java.util.HashMap<>();
    private final javafx.beans.property.BooleanProperty downloading = new javafx.beans.property.SimpleBooleanProperty(false);

    private final TextArea urlField = new TextArea();
    private final Button fetchButton = new Button("Buscar");
    private final Button maximizeButton = new Button("Maximizar");
    private final Label engineStatusLabel = new Label();

    private final TableView<VideoItem> table = new TableView<>();
    private final Label outputDirLabel = new Label();
    private final ComboBox<String> formatCombo = new ComboBox<>();
    private final ComboBox<String> bitrateCombo = new ComboBox<>();
    private final Button downloadButton = new Button("Baixar selecionados");
    private final ProgressBar overallProgress = new ProgressBar(0);
    private final Label overallLabel = new Label("Pronto.");
    private final TextArea logArea = new TextArea();
    private final TitledPane logPane = new TitledPane("Logs", logArea);

    private ExecutorService downloadPool;
    private YtDlpService ytDlpService;

    public MainView() {
        rebuildService();
        getStyleClass().add("root");
        setPadding(new Insets(24));

        Node header = buildHeader();
        Node browserPanel = buildBrowserPanel();
        Node table = buildTable();
        Node footer = buildFooter();

        VBox topSection = new VBox(20, header, browserPanel);
        BorderPane.setMargin(topSection, new Insets(0, 0, 20, 0));
        BorderPane.setMargin(table, new Insets(0, 0, 20, 0));

        setTop(topSection);
        setCenter(table);
        setBottom(footer);
        refreshEngineStatus();
    }

    // ---------------------------------------------------------------- header

    private Node buildHeader() {
        Label title = new Label("YT Audio Downloader");
        title.getStyleClass().add("app-title");

        Label subtitle = new Label("Baixe vídeos ou playlists inteiras do YouTube já convertidos em áudio");
        subtitle.getStyleClass().add("app-subtitle");

        VBox titleBox = new VBox(4, title, subtitle);

        maximizeButton.getStyleClass().add("secondary-button");
        maximizeButton.setOnAction(e -> {
            boolean nowMaximized = com.gabriel.ytaudio.App.toggleMaximized((Stage) getScene().getWindow());
            setMaximizedButtonLabel(nowMaximized);
        });

        Button settingsButton = new Button("⚙  Configurações");
        settingsButton.getStyleClass().add("secondary-button");
        settingsButton.setOnAction(e -> openSettingsDialog());

        engineStatusLabel.getStyleClass().add("engine-status");

        HBox topRow = new HBox(14, titleBox, spacer(), engineStatusLabel, maximizeButton, settingsButton);
        topRow.setAlignment(Pos.CENTER_LEFT);

        urlField.setPromptText("Cole URLs (vídeos ou playlists) ou digite termos de busca — um por linha...");
        urlField.getStyleClass().add("url-field");
        urlField.setWrapText(true);
        urlField.setPrefRowCount(3);
        HBox.setHgrow(urlField, Priority.ALWAYS);
        urlField.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER && e.isShortcutDown() && !fetchButton.isDisabled()) {
                onFetch();
                e.consume();
            }
        });

        fetchButton.setText("Buscar");
        fetchButton.getStyleClass().add("primary-button");
        fetchButton.setOnAction(e -> onFetch());
        fetchButton.setMaxWidth(Double.MAX_VALUE);

        Label hint = new Label("Ctrl+Enter busca tudo de uma vez. Texto sem URL vira uma busca no YouTube.");
        hint.getStyleClass().add("app-subtitle");
        hint.setWrapText(true);
        hint.setMaxWidth(160);
        VBox fetchBox = new VBox(10, fetchButton, hint);
        fetchBox.setAlignment(Pos.TOP_CENTER);
        fetchBox.setPrefWidth(160);

        HBox searchRow = new HBox(14, urlField, fetchBox);
        searchRow.setAlignment(Pos.CENTER_LEFT);

        VBox header = new VBox(20, topRow, searchRow);
        header.getStyleClass().add("card");
        header.setPadding(new Insets(22));
        return header;
    }

    // ---------------------------------------------------------------- browser

    private static final java.util.regex.Pattern WATCH_ID_PATTERN = java.util.regex.Pattern.compile(
            "(?i)(?:youtube\\.com/watch\\?(?:[^#]*&)?v=|youtu\\.be/|youtube\\.com/shorts/)([\\w-]{6,})");

    private Node buildBrowserPanel() {
        TitledPane pane = new TitledPane("Navegar no YouTube", null);
        pane.setExpanded(false);
        pane.getStyleClass().add("log-pane");
        pane.expandedProperty().addListener((obs, was, expanded) -> {
            if (expanded) {
                pane.setContent(buildBrowserContent());
            } else {
                // Descarta a WebView ao recolher: sem isso, a página do YouTube (JS, timers,
                // rede) continua rodando em segundo plano mesmo com o painel fechado.
                pane.setContent(null);
            }
        });
        return pane;
    }

    private static final String NO_ANIMATIONS_CSS =
            "data:text/css," + java.net.URLEncoder.encode(
                    "*, *::before, *::after { transition: none !important; animation: none !important; "
                            + "scroll-behavior: auto !important; }",
                    java.nio.charset.StandardCharsets.UTF_8);

    private Node buildBrowserContent() {
        WebView webView = new WebView();
        webView.setPrefHeight(480);
        webView.setContextMenuEnabled(false);
        WebEngine engine = webView.getEngine();
        // Página mobile é bem mais leve que a versão desktop (sem preview de vídeo em
        // hover, sem tantas animações), e a folha de estilo abaixo corta o resto das
        // transições/animações que sobram — reduz bastante o custo de repaint por frame.
        engine.setUserStyleSheetLocation(NO_ANIMATIONS_CSS);
        engine.load("https://m.youtube.com");

        Button homeButton = new Button("YouTube");
        homeButton.getStyleClass().add("secondary-button");
        homeButton.setOnAction(e -> engine.load("https://m.youtube.com"));

        Label statusLabel = new Label("Navegue até um vídeo para adicioná-lo à lista.");
        statusLabel.getStyleClass().add("app-subtitle");
        statusLabel.setWrapText(true);
        HBox.setHgrow(statusLabel, Priority.ALWAYS);

        Button addVideoButton = new Button("+  Adicionar vídeo");
        addVideoButton.getStyleClass().add("primary-button");
        addVideoButton.setDisable(true);

        String[] currentVideoId = new String[1];

        engine.locationProperty().addListener((obs, oldLoc, newLoc) -> {
            java.util.regex.Matcher matcher = newLoc == null ? null : WATCH_ID_PATTERN.matcher(newLoc);
            if (matcher != null && matcher.find()) {
                currentVideoId[0] = matcher.group(1);
                addVideoButton.setDisable(false);
                statusLabel.setText("Pronto para adicionar este vídeo.");
            } else {
                currentVideoId[0] = null;
                addVideoButton.setDisable(true);
                statusLabel.setText("Navegue até um vídeo para adicioná-lo à lista.");
            }
        });

        addVideoButton.setOnAction(e -> {
            String videoId = currentVideoId[0];
            if (videoId == null) {
                return;
            }
            String url = "https://www.youtube.com/watch?v=" + videoId;
            addVideoButton.setDisable(true);
            statusLabel.setText("Adicionando...");
            Thread thread = new Thread(() -> {
                try {
                    List<VideoItem> found = ytDlpService.fetchEntries(url);
                    Platform.runLater(() -> {
                        boolean addedAny = false;
                        for (VideoItem candidate : found) {
                            boolean duplicate = items.stream()
                                    .anyMatch(i -> java.util.Objects.equals(i.getId(), candidate.getId()));
                            if (!duplicate) {
                                items.add(candidate);
                                addedAny = true;
                            }
                        }
                        statusLabel.setText(addedAny ? "Adicionado à lista." : "Esse vídeo já estava na lista.");
                        addVideoButton.setDisable(currentVideoId[0] == null);
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        statusLabel.setText("Erro ao adicionar: " + ex.getMessage());
                        addVideoButton.setDisable(currentVideoId[0] == null);
                    });
                }
            });
            thread.setDaemon(true);
            thread.start();
        });

        HBox toolbar = new HBox(10, homeButton, statusLabel, addVideoButton);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(0, 4, 10, 4));

        BorderPane root = new BorderPane();
        root.setTop(toolbar);
        root.setCenter(webView);
        return root;
    }

    // ----------------------------------------------------------------- table

    @SuppressWarnings("unchecked")
    private Node buildTable() {
        table.setItems(items);
        table.setEditable(true);
        table.setFixedCellSize(62);
        table.setPrefHeight(620);
        table.setPlaceholder(new Label("Cole uma ou mais URLs acima (uma por linha) e clique em \"Buscar\" para listar os vídeos."));
        table.getStyleClass().add("video-table");

        TableColumn<VideoItem, Boolean> selectCol = new TableColumn<>();
        selectCol.setCellValueFactory(c -> c.getValue().selectedProperty());
        selectCol.setCellFactory(CheckBoxTableCell.forTableColumn(selectCol));
        selectCol.setEditable(true);
        selectCol.setMinWidth(36);
        selectCol.setMaxWidth(36);
        selectCol.setResizable(false);

        TableColumn<VideoItem, String> thumbCol = new TableColumn<>("Miniatura");
        thumbCol.setCellValueFactory(c -> c.getValue().thumbnailUrlProperty());
        thumbCol.setMinWidth(96);
        thumbCol.setMaxWidth(96);
        thumbCol.setResizable(false);
        thumbCol.setSortable(false);
        thumbCol.setCellFactory(col -> new TableCell<>() {
            private final javafx.scene.image.ImageView imageView = new javafx.scene.image.ImageView();
            {
                imageView.setFitWidth(84);
                imageView.setFitHeight(47);
                imageView.setPreserveRatio(true);
                imageView.setSmooth(true);
                javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle(84, 47);
                clip.setArcWidth(10);
                clip.setArcHeight(10);
                imageView.setClip(clip);
            }
            @Override
            protected void updateItem(String url, boolean empty) {
                super.updateItem(url, empty);
                if (empty || url == null || url.isBlank()) {
                    setGraphic(null);
                } else {
                    imageView.setImage(thumbnailCache.computeIfAbsent(url,
                            u -> new javafx.scene.image.Image(u, 84, 47, true, true, true)));
                    setGraphic(imageView);
                }
            }
        });

        TableColumn<VideoItem, String> titleCol = new TableColumn<>("Título");
        titleCol.setCellValueFactory(c -> c.getValue().titleProperty());
        titleCol.setMinWidth(320);

        TableColumn<VideoItem, String> durationCol = new TableColumn<>("Duração");
        durationCol.setCellValueFactory(c -> c.getValue().durationProperty());
        durationCol.setMinWidth(90);
        durationCol.setMaxWidth(110);

        TableColumn<VideoItem, Double> progressCol = new TableColumn<>("Progresso");
        progressCol.setCellValueFactory(c -> c.getValue().progressProperty().asObject());
        progressCol.setMinWidth(160);
        progressCol.setCellFactory(col -> new TableCell<>() {
            private final ProgressBar bar = new ProgressBar(0);
            {
                bar.setMaxWidth(Double.MAX_VALUE);
            }
            @Override
            protected void updateItem(Double value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setGraphic(null);
                } else {
                    bar.setProgress(value);
                    setGraphic(bar);
                }
            }
        });

        TableColumn<VideoItem, VideoItem.Status> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(c -> c.getValue().statusProperty());
        statusCol.setMinWidth(110);
        statusCol.setMaxWidth(130);
        statusCol.setCellFactory(col -> new TableCell<>() {
            private final Label pill = new Label();
            {
                pill.getStyleClass().add("status-pill");
            }
            @Override
            protected void updateItem(VideoItem.Status value, boolean empty) {
                super.updateItem(value, empty);
                pill.getStyleClass().removeAll("status-ok", "status-error", "status-progress", "status-pending");
                if (empty || value == null) {
                    setGraphic(null);
                    return;
                }
                switch (value) {
                    case PENDENTE -> { pill.setText("Pendente"); pill.getStyleClass().add("status-pending"); }
                    case BAIXANDO -> { pill.setText("Baixando..."); pill.getStyleClass().add("status-progress"); }
                    case CONCLUIDO -> { pill.setText("Concluído ✓"); pill.getStyleClass().add("status-ok"); }
                    case ERRO -> { pill.setText("Erro ✗"); pill.getStyleClass().add("status-error"); }
                }
                setGraphic(pill);
            }
        });

        table.setRowFactory(tv -> {
            TableRow<VideoItem> row = new TableRow<>();
            MenuItem openItem = new MenuItem("Abrir no navegador");
            openItem.setOnAction(e -> openInBrowser(row.getItem()));
            MenuItem copyItem = new MenuItem("Copiar link");
            copyItem.setOnAction(e -> copyLink(row.getItem()));
            ContextMenu menu = new ContextMenu(openItem, copyItem);
            row.contextMenuProperty().bind(
                    javafx.beans.binding.Bindings.when(row.emptyProperty())
                            .then((ContextMenu) null)
                            .otherwise(menu));
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    openInBrowser(row.getItem());
                }
            });
            return row;
        });

        table.getColumns().addAll(selectCol, thumbCol, titleCol, durationCol, progressCol, statusCol);
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        titleCol.prefWidthProperty().bind(table.widthProperty()
                .subtract(selectCol.widthProperty())
                .subtract(thumbCol.widthProperty())
                .subtract(durationCol.widthProperty())
                .subtract(progressCol.widthProperty())
                .subtract(statusCol.widthProperty())
                .subtract(18));
        VBox.setVgrow(table, Priority.ALWAYS);

        VBox box = new VBox(16, buildSelectionRow(), table);
        box.getStyleClass().add("card");
        box.setPadding(new Insets(20));
        return box;
    }

    private Node buildSelectionRow() {
        Label sectionTitle = new Label("RESULTADOS");
        sectionTitle.getStyleClass().add("section-title");

        Label countLabel = new Label();
        countLabel.getStyleClass().add("app-subtitle");
        countLabel.textProperty().bind(javafx.beans.binding.Bindings.createStringBinding(() -> {
            long selectedCount = items.stream().filter(VideoItem::isSelected).count();
            return items.size() + " itens encontrados"
                    + (items.isEmpty() ? "" : " · " + selectedCount + " selecionado(s)");
        }, items));

        Button selectAll = new Button("Selecionar todos");
        selectAll.getStyleClass().add("link-button");
        selectAll.setOnAction(e -> items.forEach(i -> i.setSelected(true)));

        Button selectNone = new Button("Selecionar nenhum");
        selectNone.getStyleClass().add("link-button");
        selectNone.setOnAction(e -> items.forEach(i -> i.setSelected(false)));

        HBox row = new HBox(16, sectionTitle, countLabel, spacer(), selectAll, selectNone);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    // ---------------------------------------------------------------- footer

    private Node buildFooter() {
        outputDirLabel.setText(settings.getOutputDir());
        outputDirLabel.getStyleClass().add("path-label");
        Button chooseDirButton = new Button("Escolher pasta");
        chooseDirButton.getStyleClass().add("secondary-button");
        chooseDirButton.setOnAction(e -> chooseOutputDir());

        formatCombo.getItems().addAll("mp3", "m4a", "opus", "flac", "wav");
        formatCombo.setValue(settings.getFormat());
        formatCombo.valueProperty().addListener((obs, old, val) -> settings.setFormat(val));

        bitrateCombo.getItems().addAll("320K", "256K", "192K", "128K", "0");
        bitrateCombo.setValue(settings.getBitrate());
        bitrateCombo.valueProperty().addListener((obs, old, val) -> settings.setBitrate(val));

        HBox optionsRow = new HBox(24,
                labeled("PASTA DE SAÍDA", new HBox(8, outputDirLabel, chooseDirButton)),
                labeled("FORMATO", formatCombo),
                labeled("QUALIDADE", bitrateCombo));
        optionsRow.setAlignment(Pos.CENTER_LEFT);

        downloadButton.setText("⬇  Baixar selecionados");
        downloadButton.getStyleClass().add("primary-button");
        downloadButton.setOnAction(e -> onDownload());
        javafx.beans.binding.BooleanBinding noneSelected = javafx.beans.binding.Bindings.createBooleanBinding(
                () -> items.stream().noneMatch(VideoItem::isSelected), items);
        downloadButton.disableProperty().bind(downloading.or(noneSelected));

        overallProgress.setPrefWidth(220);
        overallLabel.getStyleClass().add("app-subtitle");
        HBox progressRow = new HBox(14, overallProgress, overallLabel, spacer(), downloadButton);
        progressRow.setAlignment(Pos.CENTER_LEFT);

        VBox controlsCard = new VBox(18, optionsRow, new Separator(), progressRow);
        controlsCard.getStyleClass().add("card");
        controlsCard.setPadding(new Insets(20));

        logArea.setEditable(false);
        logArea.setPrefRowCount(6);
        logArea.getStyleClass().add("log-area");
        logPane.setExpanded(false);
        logPane.getStyleClass().add("log-pane");

        VBox footer = new VBox(16, controlsCard, logPane);
        return footer;
    }

    private Node labeled(String label, Node control) {
        Label l = new Label(label);
        l.getStyleClass().add("field-label");
        VBox box = new VBox(4, l, control);
        return box;
    }

    private Node spacer() {
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }

    // ------------------------------------------------------------- actions

    private void rebuildService() {
        String ytDlp = ToolLocator.locate(settings.getYtDlpPath(), "yt-dlp");
        String ffmpeg = ToolLocator.locate(settings.getFfmpegPath(), "ffmpeg");
        ytDlpService = new YtDlpService(ytDlp, ffmpeg, settings.getCookiesBrowser(), settings.getCookiesFile());
        if (downloadPool != null) {
            downloadPool.shutdownNow();
        }
        downloadPool = Executors.newFixedThreadPool(Math.max(1, settings.getConcurrency()));
    }

    private void refreshEngineStatus() {
        Thread thread = new Thread(() -> {
            String ytDlpPath = ToolLocator.locate(settings.getYtDlpPath(), "yt-dlp");
            String ffmpegPath = ToolLocator.locate(settings.getFfmpegPath(), "ffmpeg");
            boolean ytDlpOk = ToolLocator.isAvailable(ytDlpPath);
            boolean ffmpegOk = ToolLocator.isAvailable(ffmpegPath);
            Platform.runLater(() -> {
                if (ytDlpOk && ffmpegOk) {
                    engineStatusLabel.setText("● yt-dlp e ffmpeg prontos");
                    engineStatusLabel.getStyleClass().setAll("engine-status", "engine-ok");
                } else {
                    StringBuilder missing = new StringBuilder("● Faltando: ");
                    if (!ytDlpOk) missing.append("yt-dlp ");
                    if (!ffmpegOk) missing.append("ffmpeg");
                    engineStatusLabel.setText(missing.toString());
                    engineStatusLabel.getStyleClass().setAll("engine-status", "engine-error");
                }
            });
        });
        thread.setDaemon(true);
        thread.start();
    }

    private static final int SEARCH_RESULT_COUNT = 8;
    private static final java.util.regex.Pattern URL_PATTERN =
            java.util.regex.Pattern.compile("(?i)^https?://.+");

    private void onFetch() {
        List<String> urls = (urlField.getText() == null ? "" : urlField.getText()).lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .distinct()
                .map(line -> URL_PATTERN.matcher(line).matches()
                        ? line
                        : "ytsearch" + SEARCH_RESULT_COUNT + ":" + line)
                .toList();
        if (urls.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Cole ao menos uma URL de vídeo ou playlist antes de buscar.");
            return;
        }
        fetchButton.setDisable(true);
        items.clear();

        Thread thread = new Thread(() -> {
            int total = urls.size();
            List<String> failures = new java.util.ArrayList<>();
            for (int i = 0; i < total; i++) {
                String url = urls.get(i);
                int index = i + 1;
                Platform.runLater(() -> overallLabel.setText("Buscando link " + index + "/" + total + "..."));
                try {
                    List<VideoItem> found = ytDlpService.fetchEntries(url);
                    Platform.runLater(() -> items.addAll(found));
                } catch (Exception ex) {
                    failures.add(url + " (" + ex.getMessage() + ")");
                }
            }
            Platform.runLater(() -> {
                fetchButton.setDisable(false);
                if (items.isEmpty()) {
                    overallLabel.setText("Nenhum vídeo encontrado.");
                } else {
                    overallLabel.setText("Pronto: " + items.size() + " vídeo(s) de " + total + " link(s).");
                }
                if (!failures.isEmpty()) {
                    showAlert(Alert.AlertType.WARNING,
                            "Não foi possível buscar " + failures.size() + " de " + total + " link(s):\n\n"
                                    + String.join("\n", failures));
                }
            });
        });
        thread.setDaemon(true);
        thread.start();
    }

    private void onDownload() {
        List<VideoItem> selected = items.stream().filter(VideoItem::isSelected).toList();
        if (selected.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Selecione ao menos um vídeo para baixar.");
            return;
        }
        String outputDir = settings.getOutputDir();
        String format = formatCombo.getValue();
        String bitrate = bitrateCombo.getValue();

        downloading.set(true);
        overallProgress.setProgress(0);
        AtomicInteger completed = new AtomicInteger(0);
        int total = selected.size();
        overallLabel.setText("Baixando 0/" + total + "...");

        for (VideoItem item : selected) {
            downloadPool.submit(() -> {
                Platform.runLater(() -> item.setStatus(VideoItem.Status.BAIXANDO));
                try {
                    ytDlpService.downloadAudio(item, outputDir, format, bitrate,
                            progress -> Platform.runLater(() -> item.setProgress(progress)),
                            line -> Platform.runLater(() -> appendLog(item.getTitle() + ": " + line)));
                    Platform.runLater(() -> item.setStatus(VideoItem.Status.CONCLUIDO));
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        item.setStatus(VideoItem.Status.ERRO);
                        appendLog("ERRO em \"" + item.getTitle() + "\": " + ex.getMessage());
                    });
                } finally {
                    int done = completed.incrementAndGet();
                    Platform.runLater(() -> {
                        overallProgress.setProgress((double) done / total);
                        overallLabel.setText("Baixando " + done + "/" + total + "...");
                        if (done == total) {
                            overallLabel.setText("Concluído: " + total + " item(ns).");
                            downloading.set(false);
                        }
                    });
                }
            });
        }
    }

    private void openInBrowser(VideoItem item) {
        if (item == null || item.getUrl() == null || item.getUrl().isBlank()) {
            return;
        }
        try {
            java.awt.Desktop.getDesktop().browse(java.net.URI.create(item.getUrl()));
        } catch (Exception ex) {
            showAlert(Alert.AlertType.WARNING, "Não foi possível abrir o navegador: " + ex.getMessage());
        }
    }

    private void copyLink(VideoItem item) {
        if (item == null || item.getUrl() == null) {
            return;
        }
        javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
        content.putString(item.getUrl());
        javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
    }

    private void chooseOutputDir() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Escolher pasta de saída");
        File initial = new File(settings.getOutputDir());
        if (initial.isDirectory()) {
            chooser.setInitialDirectory(initial);
        }
        File chosen = chooser.showDialog(getScene().getWindow());
        if (chosen != null) {
            settings.setOutputDir(chosen.getAbsolutePath());
            outputDirLabel.setText(chosen.getAbsolutePath());
        }
    }

    private void openSettingsDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Configurações");
        dialog.getDialogPane().getStylesheets().addAll(getScene().getStylesheets());

        TextField ytDlpField = new TextField(settings.getYtDlpPath());
        ytDlpField.setPromptText("caminho para yt-dlp (vazio = detectar automaticamente)");
        TextField ffmpegField = new TextField(settings.getFfmpegPath());
        ffmpegField.setPromptText("caminho para ffmpeg (vazio = detectar automaticamente)");

        Spinner<Integer> concurrencySpinner = new Spinner<>(1, 4, settings.getConcurrency());

        ComboBox<String> cookiesBrowserCombo = new ComboBox<>();
        cookiesBrowserCombo.getItems().addAll("", "chrome", "chromium", "firefox", "edge", "brave", "opera", "vivaldi", "safari");
        cookiesBrowserCombo.setValue(settings.getCookiesBrowser());
        cookiesBrowserCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(String s) { return (s == null || s.isBlank()) ? "Nenhum" : s; }
            @Override public String fromString(String s) { return s; }
        });

        TextField cookiesFileField = new TextField(settings.getCookiesFile());
        cookiesFileField.setPromptText("caminho para cookies.txt (opcional, tem prioridade sobre o navegador)");
        Button chooseCookiesFileButton = new Button("Escolher arquivo");
        chooseCookiesFileButton.getStyleClass().add("secondary-button");
        chooseCookiesFileButton.setOnAction(e -> {
            javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
            chooser.setTitle("Escolher arquivo cookies.txt");
            File chosen = chooser.showOpenDialog(dialog.getOwner());
            if (chosen != null) {
                cookiesFileField.setText(chosen.getAbsolutePath());
            }
        });
        Button googleLoginButton = new Button("Entrar com Google");
        googleLoginButton.getStyleClass().add("secondary-button");
        googleLoginButton.setOnAction(e -> {
            Stage owner = (Stage) dialog.getDialogPane().getScene().getWindow();
            GoogleLoginDialog.show(owner, cookiesPath -> {
                cookiesFileField.setText(cookiesPath);
                cookiesBrowserCombo.setValue("");
            });
        });

        HBox cookiesFileRow = new HBox(8, cookiesFileField, chooseCookiesFileButton, googleLoginButton);
        HBox.setHgrow(cookiesFileField, Priority.ALWAYS);

        Label cookiesHint = new Label(
                "Use se vídeos falharem com \"Sign in to confirm you're not a bot\". \"Entrar com Google\" " +
                "abre um login dentro do app e gera o cookies.txt sozinho (experimental: o Google às vezes " +
                "bloqueia login em navegador embutido). Alternativa manual: exporte um cookies.txt de um " +
                "navegador de verdade (extensão \"Get cookies.txt\") e aponte o arquivo aqui.");
        cookiesHint.setWrapText(true);
        cookiesHint.setMaxWidth(360);
        cookiesHint.getStyleClass().add("app-subtitle");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.setPadding(new Insets(16));
        grid.addRow(0, new Label("yt-dlp:"), ytDlpField);
        grid.addRow(1, new Label("ffmpeg:"), ffmpegField);
        grid.addRow(2, new Label("Downloads simultâneos:"), concurrencySpinner);
        grid.addRow(3, new Label("Cookies do navegador:"), cookiesBrowserCombo);
        grid.addRow(4, new Label("Arquivo cookies.txt:"), cookiesFileRow);
        grid.add(cookiesHint, 1, 5);
        GridPane.setHgrow(ytDlpField, Priority.ALWAYS);
        GridPane.setHgrow(ffmpegField, Priority.ALWAYS);
        ytDlpField.setPrefWidth(320);
        ffmpegField.setPrefWidth(320);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(button -> {
            if (button == ButtonType.OK) {
                settings.setYtDlpPath(ytDlpField.getText().trim());
                settings.setFfmpegPath(ffmpegField.getText().trim());
                settings.setConcurrency(concurrencySpinner.getValue());
                settings.setCookiesBrowser(cookiesBrowserCombo.getValue() == null ? "" : cookiesBrowserCombo.getValue().trim());
                settings.setCookiesFile(cookiesFileField.getText().trim());
                rebuildService();
                refreshEngineStatus();
            }
            return button;
        });
        dialog.showAndWait();
    }

    private void appendLog(String line) {
        logArea.appendText(line + "\n");
    }

    private void showAlert(Alert.AlertType type, String message) {
        Alert alert = new Alert(type, message, ButtonType.OK);
        alert.getDialogPane().getStylesheets().addAll(getScene().getStylesheets());
        alert.showAndWait();
    }

    public void shutdown() {
        if (downloadPool != null) {
            downloadPool.shutdownNow();
        }
    }

    public void setMaximizedButtonLabel(boolean maximized) {
        maximizeButton.setText(maximized ? "Restaurar" : "Maximizar");
    }
}
