package com.syncari.core.model.misc;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Transient;

import com.syncari.utils.KeyValue;

import lombok.Data;

@Data
public class WidgetProperties {
    @Transient
    public List<KeyValue> data = new ArrayList<>();

}