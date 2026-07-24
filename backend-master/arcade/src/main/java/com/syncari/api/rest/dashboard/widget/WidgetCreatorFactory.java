package com.syncari.api.rest.dashboard.widget;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class WidgetCreatorFactory {
    @Autowired
    RecordsSyncedWidget recordsSynced;
    @Autowired
    SynapsesWidget synapses;
    @Autowired
    DailyApiCallLimitWidget apiCall;
    @Autowired
    ChangesByEntityWidget changesByEntity;
    @Autowired
    GettingStartedWidget gettingStarted;
    @Autowired
    AddUserWidget user;
    @Autowired
    CreateSynapseWidget createSynapse;
    @Autowired
    WhatsNewWidget whatsNew;
    @Autowired
    DedupedWidget deduped;
    @Autowired
    EnrichContactWidget enriched;
    @Autowired
    EmailValidationWidget emailValidation;

    public WidgetCreator getWidgetCreator(String widgetApiName) {
        switch (widgetApiName) {
        case "recordsSynced":
            return recordsSynced;
        case "synapses":
            return synapses;
        case "dailyApiCallLimit":
            return apiCall;
        case "changesByEntity":
            return changesByEntity;
        case "gettingStartedWithSyncari":
            return gettingStarted;
        case "addYourTeam":
            return user;
        case "createYourFirstSynapse":
            return createSynapse;
        case "whatsNew":
            return whatsNew;
        case "deduped":
            return deduped;
        case "enrichContact":
            return enriched;
        case "contactEmailQuality":
            return emailValidation;

        default:
            throw new RuntimeException(String.format("Unknown widget name: %s", widgetApiName));
        }
    }

    public List<WidgetCreator> getAllWidgetCreator() {
        return List.of(gettingStarted, createSynapse, user, recordsSynced, synapses, apiCall, changesByEntity, deduped,
                enriched, emailValidation, whatsNew);
    }

    public List<WidgetCreator> getWidgetCreatorsFor(List<String> apiNames) {
        List<WidgetCreator> result = new ArrayList<>();
        apiNames.forEach(apiName -> {
            result.add(getWidgetCreator(apiName));
        });
        return result;
    }

    public List<WidgetCreator> defaultWidgets() {
        return List.of(gettingStarted, createSynapse, user);
    }
}
