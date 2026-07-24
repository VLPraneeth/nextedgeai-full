package com.syncari.connector.slack;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.syncari.connector.*;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.data.iterator.EntityDataBatchIterator;
import com.syncari.connector.data.iterator.SlackIterator;
import com.syncari.connector.service.def.*;
import com.syncari.utils.I18n;
import com.syncari.utils.KeyValue;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static com.syncari.connector.slack.SlackEventProcessor.processBlockActionResponse;
import static com.syncari.connector.slack.SlackEventProcessor.processEvent;
import static com.syncari.utils.I18n.i18n;

@Slf4j
@Component(Constants.SLACK_SYNAPSE)
public class SlackService implements OauthAuthenticationService, CommonDataService, MetadataService, SynapseInfoService, WebhookService {

    @Autowired
    ObjectMapper mapper;
    @Autowired
    DefaultAuthTokenHandler tokenHandler;

    private static final String OAUTH_HOST = "https://slack.com/oauth/v2";
    private static final String GET_ACCESS_TOKEN_URL = "https://slack.com/api/oauth.v2.access";
    public static final String BASE_URL = "https://slack.com/api";
    private static final String TEAM_INFO = "/team.info";

    private static final Map<String, String> getEndpointMap = Map.of(
            "channel", "/conversations.list?limit=%s&cursor=%s",
            "user", "/users.list?limit=%s&cursor=%s",
            "message", "/conversations.history?oldest=%s&latest=%s&channel=%s&cursor=%s&limit=%s",
            "reply", "/conversations.replies?oldest=%s&latest=%s&channel=%s&ts=%s&cursor=%s&limit=%s"
    );

    public static final Map<String, String> getByIdEndpointMap = Map.of(
            "channel", "/conversations.info?channel=%s&include_num_members=true",
            "user", "/users.info?user=%s",
            "message", "/conversations.history?channel=%s&latest=%s&inclusive=true&limit=1",
            "reaction", "/reactions.get?channel=%s&timestamp=%s&full=true"
    );

    private static final int DEFAULT_PAGE_SIZE = 200;
    private static final int MAX_CHANNEL_USER_LIMIT = 20000;
    private static final int MAX_MESSAGE_LIMIT = 20000;

    @Override
    public TestConnectionResponse testConnection(ConnectorInfo config, List<String> entityNames) {
        TestConnectionResponse response = new TestConnectionResponse();
        try {
            String url = BASE_URL + String.format(getEndpointMap.get("channel"), 1, "");
            SlackRestClient restClient = new SlackRestClient();
            ResponseEntity<String> apiResponse = restClient.getResponse(url, config.getAuthConfig());
            Map<String, Object> map = mapper.readValue(apiResponse.getBody(), Map.class);
            if (!(boolean)map.get("ok")) {
                response.setCode(ConnectorErrorCodes.CONNECTION_ERROR);
                response.setMessage(I18n.i18n("invalid_token_bearer"));
            }
            return response;
        } catch (Exception e) {
            handleAuthenticationErrorMessage(response, e);
        }
        return response;
    }

    @Override
    public FetchResponse getByWatermark(SyncRequest request) {
        if (request.getWatermark().isResync() && (request.getEntityName().equalsIgnoreCase(SlackSeed.CHANNEL)
                || request.getEntityName().equalsIgnoreCase(SlackSeed.USER))) {
            return new FetchResponse(request.getWatermark(), new ListBasedIterator(getData(request), request.getWatermark()));
        }
        if (request.getWatermark().isResync() && request.getEntityName().equalsIgnoreCase(SlackSeed.MESSAGE)) {
            return new FetchResponse(request.getWatermark(), new ListBasedIterator(getMessages(request), request.getWatermark()));
        }
        return new FetchResponse(request.getWatermark(), new SlackIterator(true));
    }

    private List<EntityData> getData(SyncRequest request) {
        String nextPageURL = "";
        int limit = Math.min(request.getWatermark().getLimit(), MAX_CHANNEL_USER_LIMIT);
        List<EntityData> results = new ArrayList<>();
        do {
            String url = BASE_URL + String.format(getEndpointMap.get(request.getEntityName()), DEFAULT_PAGE_SIZE, nextPageURL);
            SlackRestClient restClient = new SlackRestClient();
            DataWithCursor dataWithCursor = restClient.getDataWithCursor(request, url, nextPageURL, Optional.empty(), "");
            nextPageURL = dataWithCursor.getNextPageURL();
            results.addAll(dataWithCursor.getData());
        } while (StringUtils.isNotEmpty(nextPageURL));
        return limit != 0 ? results.stream().limit(limit).collect(Collectors.toList()) : results;
    }

    private List<EntityData> getMessages(SyncRequest request) {
        int limit = Math.min(request.getWatermark().getLimit(), MAX_MESSAGE_LIMIT);
        SlackRestClient restClient = new SlackRestClient();
        WatermarkInfo wm = request.getWatermark();
        wm.setLimit(0);
        EntitySchema channelSchema = SlackSeed.getChannelSchema();
        SyncRequest channelresyncRequest = new SyncRequest().Builder(request.getConnector(), channelSchema);
        channelresyncRequest.setWatermark(wm);
        List<EntityData> channels = getData(channelresyncRequest);
        List<EntityData> messages = new ArrayList<>();
        for(EntityData channel: channels) {
            String messageNextPageURL = "";
            String replyNextPageURL = "";
            List<String> replyIds = new ArrayList<>();
            do {
                String url = String.format(BASE_URL + getEndpointMap.get(request.getEntityName()),
                        wm.getStart() / 1000, wm.getEnd() / 1000, channel.getId(), messageNextPageURL, DEFAULT_PAGE_SIZE);
                DataWithCursor messageData = restClient.getDataWithCursor(request, url, messageNextPageURL, Optional.of(channel), "");
                messages.addAll(messageData.getData());

                replyIds.addAll(messageData.getData().stream()
                        .filter(e -> e.getValue("reply_count") != null && (int) e.getValue("reply_count") > 0)
                        .map(e -> {
                            String[] idArray = e.getId().split("_");
                            return idArray[1];
                        }).collect(Collectors.toCollection(LinkedList::new)));
                messageNextPageURL = messageData.getNextPageURL();
            } while (StringUtils.isNotEmpty(messageNextPageURL));
            for (String id : replyIds) {
                do {
                    String url = String.format(BASE_URL + getEndpointMap.get("reply"), wm.getStart() / 1000,
                            wm.getEnd() / 1000, channel.getId(), id, replyNextPageURL, DEFAULT_PAGE_SIZE);

                    DataWithCursor replyData = restClient.getDataWithCursor(request, url, replyNextPageURL,
                            Optional.of(channel), id);
                    List<EntityData> responseData = replyData.getData().stream()
                            .filter(e -> {
                                String[] idArray = e.getId().split("_");
                                String timestamp = idArray[1];
                                return !timestamp.equalsIgnoreCase(id);
                            })
                            .collect(Collectors.toList());
                    messages.addAll(responseData);
                    replyNextPageURL = replyData.getNextPageURL();
                } while (StringUtils.isNotEmpty(replyNextPageURL));
            }
        }
        log.info("Fetched {} number of messages for {} channels", messages.size(), channels.size());
        return limit != 0 ? messages.stream().limit(limit).collect(Collectors.toList()) : messages;
    }

    @Override
    public long getFirstCreatedTime(SyncRequest request) {
        return 0;
    }

    @Override
    public List<EntityData> getByIds(SyncRequest request) {
        String url = BASE_URL + getByIdEndpointMap.get(request.getEntityName());
        SlackRestClient restClient = new SlackRestClient();
        return restClient.getByIds(request.getIds(), request.getConnector().getAuthConfig(),
                request.getEntityName(), request.getEntitySchema(), url);
    }

    @Override
    public SyncResponse create(SyncRequest request) {
        return null;
    }

    @Override
    public SyncResponse update(SyncRequest request) {
        return null;
    }

    @Override
    public SyncResponse delete(SyncRequest request) {
        return null;
    }

    @Override
    public Optional<EntitySchema> describe(DescribeRequest request) {
        if (request.getEntity().equalsIgnoreCase(SlackSeed.CHANNEL)) {
            return Optional.of(SlackSeed.getChannelSchema());
        }
        if (request.getEntity().equalsIgnoreCase(SlackSeed.USER)) {
            return Optional.of(SlackSeed.getUserSchema());
        }
        if (request.getEntity().equalsIgnoreCase(SlackSeed.MESSAGE)) {
            return Optional.of(SlackSeed.getMessageSchema());
        }
        if (request.getEntity().equalsIgnoreCase(SlackSeed.BLOCK_ACTION_RESPONSE)) {
            return Optional.of(SlackSeed.getBlockActionResponseSchema());
        }
        return Optional.empty();
    }

    @Override
    public List<EntitySchema> describeAll(DescribeAllRequest request) {
        List<EntitySchema> schemas = new ArrayList<>();
        schemas.add(SlackSeed.getChannelSchema());
        schemas.add(SlackSeed.getUserSchema());
        schemas.add(SlackSeed.getMessageSchema());
        schemas.add(SlackSeed.getBlockActionResponseSchema());
        return schemas;
    }

    @Override
    public EntitySchema createObject(CreateObjectRequest request) {
        return null;
    }

    @Override
    public AttributeSchema createField(CreateFieldRequest request) {
        return null;
    }

    @Override
    public void deleteField(DeleteFieldRequest request) {

    }

    @Override
    public AuthConfig getAccessToken(OAuthRequest oAuthRequest) {
        Map<String, String> map = Map.of(DefaultAuthTokenHandler.GRANT_TYPE, "authorization_code",
                DefaultAuthTokenHandler.CODE, oAuthRequest.getCode(),
                DefaultAuthTokenHandler.CLIENT_ID, oAuthRequest.getConfig().getClientId(),
                DefaultAuthTokenHandler.CLIENT_SECRET, oAuthRequest.getConfig().getClientSecret(),
                DefaultAuthTokenHandler.REDIRECT_URI, oAuthRequest.getRedirectUri());

        return tokenHandler.getAccessToken(GET_ACCESS_TOKEN_URL, map);
    }

    @Override
    public AuthConfig refreshToken(ConnectorInfo connector) {
        AuthConfig config = connector.getAuthConfig();
        Map<String, String> map = Map.of(DefaultAuthTokenHandler.GRANT_TYPE, DefaultAuthTokenHandler.REFRESH_TOKEN,
                DefaultAuthTokenHandler.REFRESH_TOKEN, config.getRefreshToken(),
                DefaultAuthTokenHandler.CLIENT_ID, config.getClientId(),
                DefaultAuthTokenHandler.CLIENT_SECRET, config.getClientSecret());

        return tokenHandler.refreshToken(config, GET_ACCESS_TOKEN_URL, map);
    }

    @Override
    public String getOAuthUri(ConnectorInfo connector) {
        return "/authorize?client_id={{client_id}}&scope=channels:read,channels:history,users:read,reactions:read,users:read.email&redirect_uri={{redirect_uri}}&state={{state}}";
    }

    @Override
    public String getAuthHost(AuthConfig config) {
        return OAUTH_HOST;
    }

    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        var auth = new AuthMetadata(AuthType.Oauth, Lists.newArrayList(), "OAuth", "");
        auth.setOptions(KeyValue.of("oneClickOauth", true));
        return List.of(auth);
    }

    @Override
    public List<AuthField> getConfigureFields() {
        AuthField teamId = new AuthField().setName("teamId").setLabel(i18n("team_id"))
                .setRequired(true)
                .setDataType("text").setHelpSummary(i18n("team_summary"));
        return List.of(teamId, ConnectorHelper.getSupportedAuthPicker());
    }

    @Override
    public Map<String, String> getEntityMappings() {
        return Map.of("channel", "channel", "message", "message", "user", "user");
    }

    @Override
    public Map<String, String> getAttributeMappings(String entityApiName) {
        return Map.of();
    }

    @Override
    public String getName() {
        return Constants.SLACK_SYNAPSE;
    }

    @Override
    public String getCategory() {
        return "Communications";
    }

    @Override
    public UIMetadata getUIMetadata() {
        return new UIMetadata().setIconPath("/assets/icons/logos/slack.svg")
                .setDisplayName("Slack")
                .setBackgroundColor("#EFF2F6")
                .setHelpUrl(helpArticlesBaseUrl + "/4410705394708");
    }

    @Override
    public String getCapabilitiesArticleId() {
        return "19208903961236";
    }

    @Override
    public String extractIdentifier(WebhookRequest request) {
        try {
            Map eventMap = mapper.readValue(request.getBody(), Map.class);
            if(eventMap.containsKey("team_id")) {
                return String.valueOf(eventMap.get("team_id"));
            } else {
                Map<String, String> teamMap = (Map<String, String>) eventMap.get("team");
                return teamMap.get("id");
            }
        } catch (JsonProcessingException | NullPointerException e) {
            log.error(ExceptionUtils.getStackTrace(e));
            throw new RuntimeException("Invalid request. The team info api response json is invalid");
        }
    }

    @Override
    public String getIdentifier(ConnectorInfo config) {
        return config.getMetaConfig().get("teamId").toString();
    }

    private String getWorkspaceTeamId(AuthConfig config) {
        String url = BASE_URL + TEAM_INFO;
        SlackRestClient restClient = new SlackRestClient();
        HttpHeaders header = restClient.getHeaders(config);
        ResponseEntity<String> response = restClient.getResponse(header, url, config);
        try {
            Map body = mapper.readValue(response.getBody(), Map.class);
            Map<String, String> teamMap = (Map<String, String>) body.get("team");
            return teamMap.get("id");
        } catch (JsonProcessingException | NullPointerException e) {
            log.error(ExceptionUtils.getStackTrace(e));
            throw new RuntimeException("Invalid request. The team info api response json is invalid");
        }
    }

    @Override
    public String getEndpoint() {
        // TODO Update
        return null;
    }

    @Override
    public List<EventData> parseEventData(WebhookRequest request) {
        List<EventData> response = new ArrayList<>();
        try {
            Map eventMap = mapper.readValue(request.getBody(), Map.class);
            List<EventData> eventData = new ArrayList<>();
            if(eventMap.containsKey("event")) {
                Map<String, Object> event = (Map<String, Object>) eventMap.get("event");
                eventData.addAll(processEvent(event, request));
            }
            if(eventMap.containsKey("actions")) {
                eventData.addAll(processBlockActionResponse(eventMap, request));
            }
            response.addAll(eventData);
        } catch (JsonProcessingException e) {
            log.error(ExceptionUtils.getStackTrace(e));
            throw new RuntimeException("Invalid request. The eventdata json is invalid");
        }
        log.debug("Parsed {} records for slack", response.size());
        return response;
    }
}

@Data
class SlackChannelIterator {

    String messageOffset;
    String replyMessageId;
    String replyOffset;
    EntityData currentChannel;
    EntityDataBatchIterator channelBatchIterator;
    List<EntityData> channelList = new ArrayList<>();
    ListIterator<EntityData> channelIterator;
    private final int maxChannelLimit;
    private final int maxMessageLimit;
    private int channelsConsumed = 0;
    private int messagesConsumed = 0;

    public SlackChannelIterator(EntityDataBatchIterator channelBatchIterator, int maxChannelLimit, int maxMessageLimit,
                                String messageOffset, String replyMessageId, String replyOffset) {
        this.channelBatchIterator = channelBatchIterator;
        this.channelIterator = channelList.listIterator();
        this.maxChannelLimit = maxChannelLimit;
        this.maxMessageLimit = maxMessageLimit;
        this.messageOffset = messageOffset;
        this.replyOffset = replyOffset;
        this.replyMessageId = replyMessageId;
    }

    public EntityData getCurrentChannel() {
        return currentChannel;
    }

    public boolean hasNext() {
        if (channelList.isEmpty() || !channelIterator.hasNext()) {
            if (channelBatchIterator.hasNext()) {
                channelList = channelBatchIterator.next();
                channelIterator = channelList.listIterator();
            } else {
                channelList = new ArrayList<>();
            }
        }
        return channelIterator.hasNext();
    }

    public EntityData next() {
        if (!hasNext()) return null;
        currentChannel = channelIterator.next();
        channelsConsumed++;
        return currentChannel;
    }

    public void incrementMessagesConsumed(int size) {
        messagesConsumed += size;
    }

    public boolean hasReachedLimit() {
        if (channelsConsumed >= maxChannelLimit)
            return true;
        return messagesConsumed >= maxMessageLimit;
    }
}
