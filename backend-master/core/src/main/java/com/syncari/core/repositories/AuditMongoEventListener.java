package com.syncari.core.repositories;

import com.syncari.core.SyncariContext;
import com.syncari.core.model.UUIDAuditModel;
import org.springframework.data.mongodb.core.mapping.event.AbstractMongoEventListener;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertEvent;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class AuditMongoEventListener extends AbstractMongoEventListener<UUIDAuditModel> {

    @Override
    public void onBeforeConvert(BeforeConvertEvent<UUIDAuditModel> event) {
        Date now = new Date();
        if (isNewEntity(event)) {
            event.getSource().setCreatedAt(now);
            if (SyncariContext.getUser() != null) {
                event.getSource().setCreatedBy(SyncariContext.getUser().getId());
            }
        }
        //Always set uodated at
        if (SyncariContext.getUser() != null) {
            event.getSource().setUpdatedBy(SyncariContext.getUser().getId());
        }

        event.getSource().setUpdatedAt(now);
        super.onBeforeConvert(event);
    }

    private boolean isNewEntity(BeforeConvertEvent<UUIDAuditModel> event) {
        return event.getSource().getId() == null;
    }
}
