package com.gabriel.ytaudio.model;

import javafx.beans.property.*;

public class VideoItem {

    public enum Status { PENDENTE, BAIXANDO, CONCLUIDO, ERRO }

    private final StringProperty id = new SimpleStringProperty();
    private final StringProperty title = new SimpleStringProperty();
    private final StringProperty url = new SimpleStringProperty();
    private final StringProperty duration = new SimpleStringProperty("--");
    private final StringProperty thumbnailUrl = new SimpleStringProperty("");
    private final BooleanProperty selected = new SimpleBooleanProperty(false);
    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private final ObjectProperty<Status> status = new SimpleObjectProperty<>(Status.PENDENTE);

    public VideoItem(String id, String title, String url, String duration, String thumbnailUrl) {
        this.id.set(id);
        this.title.set(title);
        this.url.set(url);
        this.duration.set(duration);
        this.thumbnailUrl.set(thumbnailUrl);
    }

    public String getId() { return id.get(); }
    public StringProperty idProperty() { return id; }

    public String getTitle() { return title.get(); }
    public StringProperty titleProperty() { return title; }

    public String getUrl() { return url.get(); }
    public StringProperty urlProperty() { return url; }

    public String getDuration() { return duration.get(); }
    public StringProperty durationProperty() { return duration; }

    public String getThumbnailUrl() { return thumbnailUrl.get(); }
    public StringProperty thumbnailUrlProperty() { return thumbnailUrl; }

    public boolean isSelected() { return selected.get(); }
    public void setSelected(boolean value) { selected.set(value); }
    public BooleanProperty selectedProperty() { return selected; }

    public double getProgress() { return progress.get(); }
    public void setProgress(double value) { progress.set(value); }
    public DoubleProperty progressProperty() { return progress; }

    public Status getStatus() { return status.get(); }
    public void setStatus(Status value) { status.set(value); }
    public ObjectProperty<Status> statusProperty() { return status; }
}
