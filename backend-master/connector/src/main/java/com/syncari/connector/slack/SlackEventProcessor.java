package com.syncari.connector.slack;

import com.syncari.connector.ConnectorHelper;
import com.syncari.connector.EntityData;
import com.syncari.connector.Operation;
import com.syncari.connector.data.EventData;
import com.syncari.connector.data.WebhookRequest;

import java.util.*;

public class SlackEventProcessor {

    private static Map<String, String> SUPPORTED_BLOCK_ACTIONS = Map.of("users_select", "selected_user",
            "static_select", "selected_option", "overflow", "selected_option", "multi_static_select-action", "selected_options",
            "conversations_select", "selected_conversation", "datepicker", "selected_date", "button", "value",
            "checkboxes", "selected_options", "radio_buttons", "selected_option", "timepicker", "selected_time");

    public static List<EventData> processEvent(Map<String, Object> event, WebhookRequest request) {
        List<EventData> eventData = new ArrayList<>();
        String eventType = String.valueOf(event.get("type"));
        String eventSubType = event.containsKey("subtype") ? String.valueOf(event.get("subtype")) : "";
        switch (eventType) {
            case "member_joined_channel": case "member_left_channel":
                eventData.add(processChannelMemberUpdate(event, request));
                break;
            case "channel_created":
                eventData.add(processChannelCreated(event, request));
                break;
            case "channel_deleted":
                eventData.add(processChannelDeleted(event, request));
                break;
            case "channel_rename":
                eventData.add(processChannelRename(event, request));
                break;
            case "channel_archive": case "channel_unarchive":
                eventData.add(processChannelArchive(event, request));
                break;
            case "user_change": case "team_join":
                eventData.add(processUserChange(event, request));
                break;
            case "reaction_added": case "reaction_removed":
                processReactions(event, request).ifPresent(e -> {
                    eventData.add(e);
                });
                break;
            case "message":
                switch (eventSubType) {
                    case "channel_topic":
                        eventData.add(processChannelTopic(event, request));
                        break;
                    case "channel_purpose":
                        eventData.add(processChannelPurpose(event, request));
                        break;
                    case "message_changed":
                        eventData.add(processMessageUpdate(event, request));
                        break;
                    case "message_deleted":
                        eventData.add(processMessageDelete(event, request));
                        break;
                    default:
                        eventData.addAll(processNewMessage(event, request));
                        break;
                }
                break;
            default:
                break;
        }
        return eventData;
    }

    private static EventData processChannelMemberUpdate(Map<String, Object> event, WebhookRequest request) {
        EventData eventData = new EventData();
        Optional<EntityData> data = ConnectorHelper.withBackoff(() -> {
            SlackRestClient restClient = new SlackRestClient();
            List<String> ids = List.of(String.valueOf(event.get("channel")));
            String url = SlackService.BASE_URL + SlackService.getByIdEndpointMap.get(SlackSeed.CHANNEL);
            List<EntityData> results = restClient.getByIds(ids, request.getConfig().getAuthConfig(),
                    SlackSeed.CHANNEL, SlackSeed.getChannelSchema(), url);
            if(!results.isEmpty()) {
                return Optional.of(results.get(0));
            }
            return Optional.empty();
        });
        if(data.isPresent()) {
            EntityData entityData = data.get().setConnectorId(request.getConfig().getId());
            entityData.setId(String.valueOf(event.get("channel")));
            entityData.setName("channel");
            entityData.setLastModified(SlackRestClient.convertFromMicroTimestamp(event.get("event_ts")));
            eventData.setData(entityData).setOperation(Operation.update);
        }
        return eventData;
    }

    private static EventData processChannelTopic(Map<String, Object> event, WebhookRequest request) {
        EventData eventData = new EventData();
        EntityData entityData = new EntityData();
        entityData.setConnectorId(request.getConfig().getId());
        entityData.setId(String.valueOf(event.get("channel")));
        entityData.setName("channel");
        entityData.addValue("topic_value", event.get("topic"));
        entityData.setLastModified(SlackRestClient.convertFromMicroTimestamp(event.get("event_ts")));
        eventData.setData(entityData).setOperation(Operation.update);
        return eventData;
    }

    private static EventData processChannelPurpose(Map<String, Object> event, WebhookRequest request) {
        EventData eventData = new EventData();
        EntityData entityData = new EntityData();
        entityData.setConnectorId(request.getConfig().getId());
        entityData.setId(String.valueOf(event.get("channel")));
        entityData.setName("channel");
        entityData.addValue("purpose_value", event.get("purpose"));
        entityData.setLastModified(SlackRestClient.convertFromMicroTimestamp(event.get("event_ts")));
        eventData.setData(entityData).setOperation(Operation.update);
        return eventData;
    }

    private static EventData processChannelCreated(Map<String, Object> event, WebhookRequest request) {
        EventData eventData = new EventData();
        Map<String, Object> channelMap = (Map<String, Object>) event.get("channel");
        EntityData entityData = SlackRestClient.processResponseRow(channelMap, SlackSeed.getChannelSchema(), Optional.empty(), "");
        entityData.setConnectorId(request.getConfig().getId());
        eventData.setData(entityData).setOperation(Operation.create);
        return eventData;
    }

    private static EventData processChannelDeleted(Map<String, Object> event, WebhookRequest request) {
        EventData eventData = new EventData();
        EntityData entityData = new EntityData();
        entityData.setConnectorId(request.getConfig().getId());
        entityData.setId(String.valueOf(event.get("channel")));
        entityData.setName(SlackSeed.CHANNEL);
        entityData.setDeleted(true);
        entityData.setLastModified(SlackRestClient.convertFromMicroTimestamp(event.get("event_ts")));
        eventData.setData(entityData).setOperation(Operation.delete);
        return eventData;
    }

    private static EventData processChannelRename(Map<String, Object> event, WebhookRequest request) {
        EventData eventData = new EventData();
        Map<String, Object> channelMap = (Map<String, Object>) event.get("channel");
        EntityData entityData = SlackRestClient.processResponseRow(channelMap, SlackSeed.getChannelSchema(), Optional.empty(), "");
        entityData.setConnectorId(request.getConfig().getId());
        entityData.setLastModified(SlackRestClient.convertFromMicroTimestamp(event.get("event_ts")));
        eventData.setData(entityData).setOperation(Operation.update);
        return eventData;
    }

    private static EventData processChannelArchive(Map<String, Object> event, WebhookRequest request) {
        EventData eventData = new EventData();
        EntityData entityData = new EntityData();
        entityData.setConnectorId(request.getConfig().getId());
        entityData.setId(String.valueOf(event.get("channel")));
        entityData.setName(SlackSeed.CHANNEL);
        if (String.valueOf(event.get("type")).equalsIgnoreCase("channel_archive")) {
            entityData.addValue("is_archived", true);
        }
        if (String.valueOf(event.get("type")).equalsIgnoreCase("channel_unarchive")) {
            entityData.addValue("is_archived", false);
        }
        entityData.setLastModified(SlackRestClient.convertFromMicroTimestamp(event.get("event_ts")));
        eventData.setData(entityData).setOperation(Operation.update);
        return eventData;
    }

    private static EventData processUserChange(Map<String, Object> event, WebhookRequest request) {
        EventData eventData = new EventData();
        EntityData entityData = SlackRestClient.processResponseRow((Map) event.get("user"),
                SlackSeed.getUserSchema(), Optional.empty(), "").setConnectorId(request.getConfig().getId());
        entityData.setConnectorId(request.getConfig().getId());
        entityData.setName(SlackSeed.USER);
        entityData.setLastModified(SlackRestClient.convertFromMicroTimestamp(event.get("event_ts")));
        Operation operation = Operation.update;
        if (String.valueOf(event.get("type")).equalsIgnoreCase("user_change")) {
            if(entityData.has("deleted") && (boolean)entityData.getValue("deleted")) {
                operation = Operation.delete;
                entityData.setDeleted(true);
            }
            else operation = Operation.update;
        }
        if (String.valueOf(event.get("type")).equalsIgnoreCase("team_join")) operation = Operation.create;
        eventData.setData(entityData)
                .setOperation(operation);
        return eventData;
    }

    private static List<EventData> processNewMessage(Map<String, Object> event, WebhookRequest request) {
        List<EventData> eventDataList = new ArrayList<>();
        EventData eventData = new EventData();
        String channelId = String.valueOf(event.get("channel"));
        EntityData channel = new EntityData();
        channel.setId(channelId);
        String parentTs = "";
        if(event.containsKey("thread_ts")) {
            if(!String.valueOf(event.get("thread_ts")).equalsIgnoreCase(String.valueOf(event.get("ts")))) {
                parentTs = String.valueOf(event.get("thread_ts"));
                String parentMessageId = String.format("%s_%s", channelId, parentTs);
                String url = SlackService.BASE_URL + SlackService.getByIdEndpointMap.get(SlackSeed.MESSAGE);
                SlackRestClient restClient = new SlackRestClient();
                List<EntityData> results = restClient.getByIds(List.of(parentMessageId), request.getConfig().getAuthConfig(),
                        SlackSeed.MESSAGE, SlackSeed.getMessageSchema(), url);
                if(results.size() > 0) {
                    EntityData parent = results.get(0);
                    parent.setConnectorId(request.getConfig().getId());
                    EventData parentEvent = new EventData();
                    parentEvent.setData(parent).setOperation(Operation.update);
                    eventDataList.add(parentEvent);
                }
            }
        }
        EntityData entityData = SlackRestClient.processResponseRow(event,
                SlackSeed.getMessageSchema(), Optional.of(channel), parentTs).setConnectorId(request.getConfig().getId());
        entityData.setConnectorId(request.getConfig().getId());
        entityData.addValue("channel_id", channelId);
        entityData.setLastModified(SlackRestClient.convertFromMicroTimestamp(event.get("event_ts")));
        eventData.setData(entityData).setOperation(Operation.update);
        eventDataList.add(eventData);
        return eventDataList;
    }

    private static EventData processMessageUpdate(Map<String, Object> event, WebhookRequest request) {
        EventData eventData = new EventData();
        String channelId = String.valueOf(event.get("channel"));
        Map messageMap = (Map) event.get("message");
        Map previousMessageMap = (Map) event.get("previous_message");
        EntityData channel = new EntityData();
        channel.setId(channelId);
        String parentTs = "";
        if(messageMap.containsKey("thread_ts")) {
            if(!String.valueOf(messageMap.get("thread_ts")).equalsIgnoreCase(String.valueOf(messageMap.get("ts"))))
                parentTs = String.valueOf(messageMap.get("thread_ts"));
        }
        EntityData entityData = SlackRestClient.processResponseRow(messageMap,
                SlackSeed.getMessageSchema(), Optional.of(channel), parentTs).setConnectorId(request.getConfig().getId());
        entityData.setConnectorId(request.getConfig().getId());
        entityData.addValue("channel_id", channelId);
        if(previousMessageMap.containsKey("thread_ts") && !messageMap.containsKey("thread_ts")) {
            entityData.addValue("reply_count", 0);
            entityData.addValue("reply_users_count", 0);
            // TODO reset thread_ts and latest_reply
        }
        entityData.setLastModified(SlackRestClient.convertFromMicroTimestamp(event.get("event_ts")));
        eventData.setData(entityData).setOperation(Operation.update);
        return eventData;
    }

    private static EventData processMessageDelete(Map<String, Object> event, WebhookRequest request) {
        EventData eventData = new EventData();
        String channelId = String.valueOf(event.get("channel"));
        String ts = String.valueOf(event.get("deleted_ts"));
        String messageId = String.format("%s_%s", channelId, ts);
        EntityData entityData = new EntityData();
        entityData.setName(SlackSeed.MESSAGE);
        entityData.setConnectorId(request.getConfig().getId());
        entityData.setId(messageId);
        entityData.setDeleted(true);
        entityData.setLastModified(SlackRestClient.convertFromMicroTimestamp(event.get("event_ts")));
        eventData.setData(entityData).setOperation(Operation.delete);
        return eventData;
    }

    private static Optional<EventData> processReactions(Map<String, Object> event, WebhookRequest request) {
        EventData eventData = new EventData();
        Map<String, String> itemMap = (Map<String, String>) event.get("item");
        String channelId = itemMap.get("channel");
        String timestamp = itemMap.get("ts");
        String url = SlackService.BASE_URL + String.format(SlackService.getByIdEndpointMap.get("reaction"), channelId, timestamp);
        SlackRestClient restClient = new SlackRestClient();
        Optional<EntityData> e = restClient.getReactionsById(timestamp, request.getConfig().getAuthConfig(), url);
        if(e.isPresent()) {
            e.get().setConnectorId(request.getConfig().getId());
            e.get().setName("message");
            e.get().setId(String.format("%s_%s", channelId, timestamp));
            e.get().setLastModified(SlackRestClient.convertFromMicroTimestamp(event.get("event_ts")));
            eventData.setData(e.get()).setOperation(Operation.update);
            return Optional.of(eventData);
        }
        return Optional.empty();
    }

    public static List<EventData> processBlockActionResponse(Map<String, Object> event, WebhookRequest request) {
        List<EventData> eventData = new ArrayList<>();
        String responseURL = (String) event.get("response_url");
        List actionList = (List) event.get("actions");
        for(Object action: actionList) {
            Map<String, Object> actionMap = (Map<String, Object>) action;
            if (SUPPORTED_BLOCK_ACTIONS.containsKey(actionMap.get("type"))) {
                EntityData entityData = new EntityData("block_action_response");
                entityData.setId((String) actionMap.get("action_ts"));
                entityData.addValue("type", actionMap.get("type"));
                entityData.addValue("action_id", actionMap.get("action_id"));
                entityData.addValue("block_id", actionMap.get("block_id"));
                processActions(entityData, actionMap);
                Map<String, String> containerMap = (Map<String, String>) event.get("container");
                entityData.addValue("channel_id", containerMap.get("channel_id"));
                Map<String, String> userMap = (Map<String, String>) event.get("user");
                entityData.addValue("user_id", userMap.get("id"));
                entityData.addValue("action_ts", SlackRestClient.convertFromMicroTimestamp(actionMap.get("action_ts")));
                entityData.addValue("response_url", responseURL);
                entityData.setLastModified(SlackRestClient.convertFromMicroTimestamp(actionMap.get("action_ts")));
                entityData.setConnectorId(request.getConfig().getId());
                EventData responseEventData = new EventData();
                responseEventData.setData(entityData).setOperation(Operation.create);
                eventData.add(responseEventData);
            }
        }
        return eventData;
    }

    private static void processActions(EntityData entityData, Map<String, Object> actionMap) {
        String actionField = SUPPORTED_BLOCK_ACTIONS.get(actionMap.get("type"));
        if(actionField.equalsIgnoreCase("selected_option")) {
            Map<String, Object> optionMap = (Map<String, Object>) actionMap.get(actionField);
            entityData.addValue("selected_option", optionMap.get("value"));
        } else if(actionField.equalsIgnoreCase("selected_options")) {
            List optionList = (List) actionMap.get(actionField);
            List<String> options = new ArrayList<>();
            for(Object option: optionList) {
                Map<String, Object> optionMap = (Map<String, Object>) option;
                options.add((String)optionMap.get("value"));
            }
            entityData.addValue("selected_options", options);
        } else {
            entityData.addValue(actionField, actionMap.get(actionField));
        }
    }

}
