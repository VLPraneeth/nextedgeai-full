package com.syncari.core.model.misc;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import com.syncari.utils.KeyValue;

import lombok.Data;

@Data
public class WidgetContent {
    private String name;
    private WidgetType component;
    private List<KeyValue> config = new ArrayList<>();
    private List data = new ArrayList<>();
    private List<WidgetContent> contents = new ArrayList<>();

    public WidgetContent(WidgetType component) {
        this.component = component;
    }

    public WidgetContent(WidgetType component, String name) {
        this.component = component;
        this.name = name;
    }

    public void setData(List data) {
        this.data = data;
    }

    public Optional<WidgetContent> getContent(String name) {
        if (StringUtils.isBlank(name)) {
            return Optional.empty();
        }
        if (name.equalsIgnoreCase(this.name)) {
            return Optional.of(this);
        }
        return contents.stream().flatMap(c -> c.getContent(name).stream()).findFirst();
    }
}