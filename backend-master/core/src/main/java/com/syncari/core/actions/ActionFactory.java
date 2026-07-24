package com.syncari.core.actions;

public interface ActionFactory {
    DefaultAction insertSyncariRecord();

    DefaultAction updateRecords();

    DefaultAction exportRecords();

    DefaultAction respondToWebhook();

    DefaultAction createFile();
}
