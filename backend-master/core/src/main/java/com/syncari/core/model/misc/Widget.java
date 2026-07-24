package com.syncari.core.model.misc;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.annotation.Transient;

import com.syncari.utils.KeyValue;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class Widget implements Serializable {
    String id;
    String name;
    String title;
    WidgetLayout layout = new WidgetLayout();
    String loadingText;
    @Transient
    List<WidgetContent> contents = new ArrayList<>();

    public Widget() {
        this.id = UUID.randomUUID().toString();
    }

    public Widget addContent(WidgetContent content) {
        contents.add(content);
        return this;
    }

    public Optional<WidgetContent> getContent(String name) {
        return contents.stream().flatMap(c -> c.getContent(name).stream()).findFirst();
    }

    public void populateData(List<KeyValue> data) {
        contents.stream().findFirst().ifPresent(content -> content.setData(data));
    }

    public void populateData(String contentName, List data) {
        Optional<WidgetContent> widget = getContent(contentName);
        widget.ifPresent(content -> content.setData(data));
    }

}