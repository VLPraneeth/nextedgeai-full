package com.syncari.api.rest.controllers.data.studio;

import java.util.ArrayList;
import java.util.List;

import com.syncari.utils.KeyValue;

import lombok.Data;

@Data
public class MetaFields {
    List<KeyValue> fields = new ArrayList<>();
    boolean readOnly;
    
    public void addField(KeyValue field) {
        this.fields.add(field);
    }
}