package com.syncari.connector.slack;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.DataWithCursor;
import com.syncari.connector.data.EntitySchema;
import com.syncari.connector.data.SyncRequest;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.rest.SyncariEntityDataRestClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static java.time.ZoneOffset.UTC;

@Slf4j
public class SlackRestClient extends SyncariEntityDataRestClient {

    private static final Map<String, String>    entityToJsonMap = Map.of(
            "channel", "channels",
            "message", "messages",
            "user", "members"
    );

    private static final Set<String> datetimeAttributes = Set.of("created", "updated", "last_set");

    private static final Set<String> microTimestampAttributes = Set.of("latest_reply", "thread_ts");

    private static final Set<String> nestedAttributes = Set.of("profile", "topic", "purpose");

    public SlackRestClient() {
        objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public DataWithCursor getDataWithCursor(SyncRequest request, String url, String prevCursor,
                                            Optional<EntityData> channelOption, String replyMessageId) {
        AuthConfig authConfig = request.getConnector().getAuthConfig();
        HttpHeaders headers = getHeaders(authConfig);
        ResponseEntity<String> response = getResponse(headers, url, authConfig);
        checkResponse(response);
        String nextPageURL = "";
        String prevPageURL = "";
        List<EntityData> result = new ArrayList<>();
        try {
            Map results = objectMapper.readValue(response.getBody(), Map.class);
            String nextCursor = "";
            if (results.containsKey("response_metadata")) {
                Map responseMetadata = (Map) results.get("response_metadata");
                nextCursor = String.valueOf(responseMetadata.get("next_cursor"));
            }
            if (StringUtils.isNotEmpty(nextCursor)) {
                nextPageURL = nextCursor;
            }
            if (StringUtils.isNotEmpty(prevCursor)) {
                prevPageURL = prevCursor;
            }

            if (results.containsKey(entityToJsonMap.get(request.getEntityName()))) {
                List<Map> resultArray = (ArrayList<Map>) results.get(entityToJsonMap.get(request.getEntityName()));
                for (Map r : resultArray) {
                    EntityData data = processResponseRow(r, request.getEntitySchema(), channelOption, replyMessageId);
                    result.add(data);
                }
            }
            else if(results.containsKey("error") && (String.valueOf(results.get("error")).equalsIgnoreCase("not_in_channel") ||
                    String.valueOf(results.get("error")).equalsIgnoreCase("thread_not_found"))) {
                return new DataWithCursor(prevPageURL, nextPageURL, result);
            }
            else {
                String errMsg = String.format("Invalid api response for Slack api call %s", request.getEntityName());
                log.error("Slack api error response - {}", response.getBody());
                throw new NonRetriableException(ErrorCodes.UNKNOWN_ERROR.name(), errMsg, "500");
            }
        } catch (IOException e) {
            String errMsg = String.format("Failed to read response for object %s", request.getEntityName());
            throw new NonRetriableException(ErrorCodes.UNKNOWN_ERROR.name(), errMsg, "500");
        }
        return new DataWithCursor(prevPageURL, nextPageURL, result);
    }

    public List<EntityData> getByIds(List<String> ids, AuthConfig authConfig, String entityName, EntitySchema schema, String url) {
        List<EntityData> data = new ArrayList<>();
        HttpHeaders headers = getHeaders(authConfig);

        ids.forEach(id -> {
            String finalUrl = "";
            String channelId = "";
            if(entityName.equalsIgnoreCase(SlackSeed.MESSAGE)) {
                String[] messageId = id.split("_");
                if(messageId.length != 2) return;
                channelId = messageId[0];
                String ts = messageId[1];
                finalUrl = String.format(url, channelId, ts);
            }
            else {
                finalUrl = String.format(url, id);
            }
            ResponseEntity<String> response = getResponse(headers, finalUrl, authConfig);
            checkResponse(response);
            try {
                Map results = objectMapper.readValue(response.getBody(), Map.class);
                if (MapUtils.isNotEmpty(results)){
                    if(entityName.equalsIgnoreCase(SlackSeed.MESSAGE)) {
                        List<Map> entityMapArray = (List<Map>) results.get("messages");
                        if(!CollectionUtils.isEmpty(entityMapArray)) {
                            EntityData channel = new EntityData();
                            channel.setId(channelId);
                            data.add(processResponseRow(entityMapArray.get(0), schema, Optional.of(channel), ""));
                        }
                    }
                    else {
                        Map entityMap = (Map) results.get(entityName);
                        data.add(processResponseRow(entityMap, schema, Optional.empty(), ""));
                    }
                }
            } catch (IOException e) {
                String errMsg = String.format("Failed to read response for object %s with id %s", entityName, id);
                throw new NonRetriableException(ErrorCodes.UNKNOWN_ERROR.name(), errMsg, "500");
            }
        });
        return data;
    }

    public Optional<EntityData> getReactionsById(String timestamp, AuthConfig authConfig, String url) {
        HttpHeaders headers = getHeaders(authConfig);
        ResponseEntity<String> response = getResponse(headers, url, authConfig);
        checkResponse(response);
        try {
            Map results = objectMapper.readValue(response.getBody(), Map.class);
            Map entityMap = (Map) results.get("message");
            if(results.containsKey("error") && results.get("error") != null && "message_not_found".equalsIgnoreCase(results.get("error").toString())) {
                return Optional.empty();
            }
            return Optional.of(new EntityData().addValue("reactions", countReactions(entityMap.get("reactions"))));
        } catch (IOException e) {
            String errMsg = String.format("Failed to read response for object message with timestamp %s", timestamp);
            throw new NonRetriableException(ErrorCodes.UNKNOWN_ERROR.name(), errMsg, "500");
        }
    }

    public static EntityData processResponseRow(Map row, EntitySchema schema, Optional<EntityData> channelOption, String replyMessageId) {
        var e = new EntityData();
        e.setName(schema.getApiName());
        row.forEach((k, v) -> {
            String key = k.toString();
            if (schema.getField(key).isPresent() || (nestedAttributes.contains(key) && !schema.getApiName().equalsIgnoreCase("message"))) {
                if ("id".equalsIgnoreCase(key)) {
                    e.setId(String.valueOf(v));
                }
                if (schema.getApiName().equalsIgnoreCase("message") && "ts".equalsIgnoreCase(key)) {
                    if (channelOption.isPresent()) {
                        EntityData channel = channelOption.get();
                        e.addValue("channel_id", channel.getId());
                        String messageId = String.format("%s_%s", channel.getId(), v);
                        e.setId(messageId);
                    }
                    long dateTime = convertFromMicroTimestamp(v);
                    e.setCreatedAt(dateTime);
                    e.setLastModified(dateTime);
                    e.addValue(key, dateTime);
                    e.addValue("micro_ts", v);
                    return;
                }
                if (datetimeAttributes.contains(key)) {
                    long dateTime = getTimestamp(v);
                    e.setCreatedAt(dateTime);
                    e.setLastModified(dateTime);
                    e.addValue(key, dateTime);
                    return;
                }
                if (microTimestampAttributes.contains(key)) {
                    long dateTime = convertFromMicroTimestamp(v);
                    e.addValue(key, dateTime);
                    return;
                }
                if (nestedAttributes.contains(key)) {
                    processNestedRow(schema, e, key, v);
                    return;
                }
                if ("shared_team_ids".equalsIgnoreCase(key) || "previous_names".equalsIgnoreCase(key)) {
                    List<String> array = (ArrayList<String>) v;
                    String teamIds = array.stream().collect(Collectors.joining(","));
                    e.addValue(key, teamIds);
                    return;
                }
                if ("reactions".equalsIgnoreCase(key)) {
                    e.addValue(key, countReactions(v));
                    return;
                }
                e.addValue(key, v);
            }
        });
        if (channelOption.isPresent() && StringUtils.isNotEmpty(replyMessageId)) {
            e.addValue("parent_ts", String.format("%s_%s", channelOption.get().getId(), replyMessageId));
        }
        return e;
    }

    private static void processNestedRow(EntitySchema schema, EntityData e, String parentKey, Object parentValue) {
        Map<String, Object> map = (Map<String, Object>) parentValue;
        map.forEach((k, v) -> {
            String attribute = String.format("%s_%s", parentKey, k);
            if (schema.getField(attribute).isPresent()) {
                if (datetimeAttributes.contains(k)) {
                    v = getTimestamp(v);
                }
                if (k.contains("status_") && String.valueOf(v).equals("")) {
                    e.addValue(attribute, " ");
                } else {
                    e.addValue(attribute, v);
                }
            }
        });
    }

    private static int countReactions(Object v) {
        int count = 0;
        List<Map<String, Object>> reactions = (ArrayList<Map<String, Object>>) v;
        for (Map<String, Object> map : reactions) {
            count += (int) map.get("count");
        }
        return count;
    }

    public static long getTimestamp(Object value) {
        return ZonedDateTime.ofInstant(Instant.ofEpochSecond((Integer) value), UTC).toEpochSecond() * 1000;
    }

    public static long convertFromMicroTimestamp(Object ts) {
        String eventTs = String.valueOf(ts);
        String[] microTimestamp = eventTs.split("\\.");
        Instant timestamp = Instant.ofEpochSecond(Long.valueOf(microTimestamp[0]));
        return ZonedDateTime.ofInstant(timestamp.plusMillis(Long.valueOf(microTimestamp[1])/1000), ZoneOffset.UTC).toInstant().toEpochMilli();
    }

}
