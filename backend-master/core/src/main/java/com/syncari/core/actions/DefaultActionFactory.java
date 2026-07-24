package com.syncari.core.actions;

import com.syncari.core.functions.CreateFileAction;
import com.syncari.core.functions.ExportSyncariRecordsAction;
import com.syncari.core.functions.InsertSyncariRecordAction;
import com.syncari.core.functions.UpdateRecordsAction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component("defaultActions")
public class DefaultActionFactory implements ActionFactory {
    private final ApplicationContext applicationContext;

    @Autowired
    public DefaultActionFactory(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public InsertSyncariRecordAction insertSyncariRecord() {
        return applicationContext.getBean(InsertSyncariRecordAction.class);
    }

    @Override
    public UpdateRecordsAction updateRecords() {
        return applicationContext.getBean(UpdateRecordsAction.class);
    }

    @Override
    public ExportSyncariRecordsAction exportRecords() {
        return applicationContext.getBean(ExportSyncariRecordsAction.class);
    }

    @Override
    public RespondToWebhookAction respondToWebhook() {
        return applicationContext.getBean(RespondToWebhookAction.class);
    }

    @Override
    public CreateFileAction createFile() {
        return applicationContext.getBean(CreateFileAction.class);
    }

}
