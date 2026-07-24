package com.syncari.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.misc.Widget;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode
public class Dashboard extends UUIDAuditModel implements SyncariComparable<Dashboard> {
    String name;
    String title;
    String category;
    String entityId;
    String entityApiName;
    boolean seeded;
    List<Widget> widgets = new ArrayList<>();
    
    public Dashboard addWidget(Widget widget) {
        getWidget(widget.getName()).ifPresent(w -> {
            throw new SyncariValidationException("Widget with name "+widget.getName()+"already exists");
        });
        widgets.add(widget);
        return this;
    }
    
    public Optional<Widget> getWidget(String name) {
        return widgets.stream().filter(w -> name.equalsIgnoreCase(w.getName())).findFirst();
    }
}
