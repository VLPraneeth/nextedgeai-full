package com.syncari.connector.stripe;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.gson.JsonSyntaxException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import com.syncari.connector.*;
import com.syncari.connector.data.*;
import com.syncari.connector.data.iterator.DefaultCursorBasedIterator;
import com.syncari.connector.service.def.*;
import com.syncari.utils.I18n;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jooq.lambda.function.Function3;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static com.syncari.utils.I18n.i18n;
import static java.lang.String.format;

@Slf4j
@Component(Constants.STRIPE)
public class StripeService implements AuthenticationService, CommonDataService, MetadataService, SynapseInfoService, WebhookService {

    ObjectMapper mapper = new ObjectMapper();

    public static final String BASE_URL = "https://api.stripe.com/v1";

    private static final String GET_BY_WATERMARK_INITIAL_URL = "/%s?created[gte]=%s&created[lte]=%s&limit=%s";
    private static final String GET_BY_WATERMARK_INITIAL_URL_COUPONS = "/%s?expand[0]=data.applies_to&created[gte]=%s&created[lte]=%s&limit=%s";

    private static final String GET_BY_WATERMARK_URL = "/%s?created[gte]=%s&created[lte]=%s&limit=%s&starting_after=%s";
    private static final String GET_BY_WATERMARK_URL_COUPONS = "/%s?expand[0]=data.applies_to&created[gte]=%s&created[lte]=%s&limit=%s&starting_after=%s";

    private static final Map<String, Map<String, String>> GET_URL_MAP = Map.of(
            StripeSeed.CUSTOMERS, Map.of("initial", "/customers?limit=%s", "nextPage", "/customers?limit=%s&starting_after=%s"),
            StripeSeed.SUBSCRIPTIONS, Map.of("initial", "/subscriptions?limit=%s", "nextPage", "/subscriptions?limit=%s&starting_after=%s"),
            StripeSeed.SUBSCRIPTION_ITEMS, Map.of("initial", "/subscription_items?subscription=%s&limit=%s", "nextPage", "/subscription_items?subscription=%s&limit=%s&starting_after=%s"),
            StripeSeed.PAYMENT_METHODS, Map.of("initial", "/payment_methods?type=card&customer=%s&limit=%s", "nextPage", "/payment_methods?type=card&customer=%s&limit=%s&starting_after=%s"),
            StripeSeed.SESSIONS, Map.of("initial", "/checkout/sessions?limit=%s", "nextPage", "/checkout/sessions?limit=%s&starting_after=%s"),
            StripeSeed.COUPONS, Map.of("initial", "/coupons?expand[0]=data.applies_to&limit=%s", "nextPage", "/coupons?expand[0]=data.applies_to&limit=%s&starting_after=%s")

    );

    private static final String BY_ID_URL = "/%s/%s";

    private static final String SESSION_BY_ID_URL = "/checkout/%s/%s";

    private static final String WEBHOOK_URL = "/webhook_endpoints";

    private static final int API_MAX_PAGESIZE = 100;

    private static final List<String> webhookEvents = List.of(
            "charge.failed", "charge.pending", "charge.refunded",
            "charge.succeeded", "charge.updated", "charge.dispute.closed", "charge.dispute.funds_reinstated",
            "charge.dispute.funds_withdrawn", "charge.dispute.updated", "charge.refund.updated",
            "customer.deleted", "customer.updated",
            "payment_intent.canceled", "payment_intent.payment_failed", "payment_intent.processing",
            "payment_intent.requires_action", "payment_intent.succeeded",
            "payment_method.attached", "payment_method.automatically_updated", "payment_method.detached",
            "payment_method.updated",
            "product.deleted", "product.updated",
            "price.updated", "price.deleted",
            "invoice.deleted", "invoice.finalized", "invoice.marked_uncollectible", "invoice.paid",
            "invoice.payment_action_required", "invoice.payment_failed", "invoice.payment_succeeded",
            "invoice.sent", "invoice.updated", "invoice.voided",
            "invoiceitem.created", "invoiceitem.deleted",
            "customer.subscription.deleted", "customer.subscription.pending_update_applied",
            "customer.subscription.pending_update_expired", "customer.subscription.updated",
            "coupon.deleted","coupon.updated","customer.discount.created",
            "customer.discount.updated","customer.discount.deleted"
    );

    public static final Map<String, String> LINE_ITEM_MAP = Map.of(StripeSeed.SUBSCRIPTION_ITEMS, StripeSeed.SUBSCRIPTIONS);

    public static BiMap<String, String> LINE_ITEM_BIMAP = HashBiMap.create(LINE_ITEM_MAP);

    public static Map<String, EntitySchema> CHILD_SCHEMA_MAP = Map.of(StripeSeed.SUBSCRIPTIONS, StripeSeed.getSubscriptionItemSchema());

    private static final Set<String> NON_WM_ENTITIES = Set.of(StripeSeed.PAYMENT_METHODS, StripeSeed.SESSIONS);

    @Override
    public TestConnectionResponse testConnection(ConnectorInfo config, List<String> entityNames) {
        TestConnectionResponse response = new TestConnectionResponse();
        try {
            String url = BASE_URL + format(GET_BY_WATERMARK_INITIAL_URL, StripeSeed.CUSTOMERS, Instant.EPOCH.toEpochMilli(), 1, "");
            StripeRestClient restClient = new StripeRestClient();
            ResponseEntity<String> apiResponse = restClient.getResponse(url, config.getAuthConfig());
            if(apiResponse.getStatusCode() != HttpStatus.OK) {
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
    public String getCapabilitiesArticleId() {
        return "19200273276948";
    }

    @Override
    public FetchResponse getByWatermark(SyncRequest request) {
        if(NON_WM_ENTITIES.contains(request.getEntityName())) {
            return new FetchResponse(request.getWatermark(), new ListBasedIterator(fetchAllData(request), request.getWatermark()));
        }
        Function3<WatermarkInfo, Integer, String, DataWithCursor> generator = (wm, pageSize,
                                                                              cursor) -> {
            StripeRestClient restClient = new StripeRestClient();
            String url = null;
            String entityName = getEntityName(request.getEntityName());
            boolean isChild = false;
            EntitySchema schema = request.getEntitySchema();
            if(LINE_ITEM_MAP.containsKey(request.getEntityName())) {
                schema = describe(new DescribeRequest(request.getConnector(), entityName)).get();
                isChild = true;
            }

            if(entityName.equals(StripeSeed.COUPONS)){
                url = BASE_URL + format(GET_BY_WATERMARK_URL_COUPONS, entityName, request.getWatermark().getStart()/1000,
                        request.getWatermark().getEnd()/1000, pageSize, cursor);
            }else{
                url = BASE_URL + format(GET_BY_WATERMARK_URL, entityName, request.getWatermark().getStart()/1000,
                        request.getWatermark().getEnd()/1000, pageSize, cursor);
            }
            // If for first page, cursor will be empty, in which case, begin the cursor iteration.
            if (StringUtils.isEmpty(cursor)) {
                if(entityName.equals(StripeSeed.COUPONS)){
                    url = BASE_URL + format(GET_BY_WATERMARK_INITIAL_URL_COUPONS, entityName, request.getWatermark().getStart()/1000,
                            request.getWatermark().getEnd()/1000, pageSize);
                }else{
                    url = BASE_URL + format(GET_BY_WATERMARK_INITIAL_URL, entityName, request.getWatermark().getStart()/1000,
                            request.getWatermark().getEnd()/1000, pageSize);
                }

            }
            if(schema.getApiName().equalsIgnoreCase(StripeSeed.INVOICES) || schema.getApiName().equalsIgnoreCase(StripeSeed.INVOICE_ITEMS)) {
                url = url + "&expand[]=data.discounts";
            }
            DataWithCursor results = restClient.getDataWithCursor(url, request, cursor, getConnectedAccountId(request.getConnector()), schema);
            if(isChild) {
                return new DataWithCursor(results.getPrevPageURL(), results.getNextPageURL(), results.getData().stream().map(ed -> ed.getChildrenRecords("items")).flatMap(List::stream).collect(Collectors.toList()));
            }
            return results;
        };

        int pgSize = (request.getPageSize() <= 0) ? API_MAX_PAGESIZE : request.getPageSize();

        DefaultCursorBasedIterator iterator = new DefaultCursorBasedIterator(request.getWatermark(),
                request.getWatermark().getChangeStream(),
                request.getWatermark().getOffset(), generator, new ArrayList<>(),
                pgSize, request.getWatermark().getLimit(), true);
        return new FetchResponse(request.getWatermark(), iterator);
    }

    private String getEntityName(String entityName) {
        if(LINE_ITEM_MAP.containsKey(entityName)) {
            return LINE_ITEM_MAP.get(entityName);
        }
        return entityName;
    }

    private List<EntityData> fetchAllData(SyncRequest request) {
        StripeRestClient restClient = new StripeRestClient();
        int pgSize = (request.getPageSize() <= 0) ? API_MAX_PAGESIZE : request.getPageSize();
        String nextPageURL = "";
        int limit = request.getWatermark().getLimit();
        List<EntityData> results = new ArrayList<>();
        do {
            String url = getURL(request, pgSize, nextPageURL, "");
            DataWithCursor dataWithCursor = restClient.getDataWithCursor(url, request, nextPageURL, getConnectedAccountId(request.getConnector()), request.getEntitySchema());
            nextPageURL = dataWithCursor.getNextPageURL();
            if (request.getEntityName().equalsIgnoreCase(StripeSeed.PAYMENT_METHODS) || request.getEntityName().equalsIgnoreCase(StripeSeed.SUBSCRIPTION_ITEMS)) {
                // Fetch payment methods or subscription items
                List<String> ids = dataWithCursor.getData().stream().map(EntityData::getId).collect(Collectors.toList());
                List<EntityData> resultsForID = new ArrayList<>();
                ids.forEach(id -> {
                    resultsForID.addAll(fetchDataForID(id, request));
                });
                results.addAll(resultsForID);
            } else {
                results.addAll(dataWithCursor.getData());
            }
        } while (StringUtils.isNotEmpty(nextPageURL));
        return limit != 0 ? results.stream().limit(limit).collect(Collectors.toList()) : results;
    }

    private String getURL(SyncRequest request, int pgSize, String nextPageURL, String id) {
        String url = "";
        if (request.getEntityName().equalsIgnoreCase(StripeSeed.PAYMENT_METHODS) && StringUtils.isEmpty(id)) {
            // Payment Methods can only be fetched with a customer id. So we need to fetch customers first, then payment methods
            url = getURL(pgSize, nextPageURL, StripeSeed.CUSTOMERS, id);
        } else if(request.getEntityName().equalsIgnoreCase(StripeSeed.PAYMENT_METHODS)) {
            url = getURL(pgSize, nextPageURL, StripeSeed.PAYMENT_METHODS, id);
        } else if (request.getEntityName().equalsIgnoreCase(StripeSeed.SUBSCRIPTION_ITEMS) && StringUtils.isEmpty(id)) {
            // Subscription Items can only be fetched with a subscription id. So we need to fetch subscriptions first, then subscription items
            url = getURL(pgSize, nextPageURL, StripeSeed.SUBSCRIPTIONS, id);
        } else if (request.getEntityName().equalsIgnoreCase(StripeSeed.SUBSCRIPTION_ITEMS) ) {
            url = getURL(pgSize, nextPageURL, StripeSeed.SUBSCRIPTION_ITEMS, id);
        } else if (request.getEntityName().equalsIgnoreCase(StripeSeed.SESSIONS)) {
            url = getURL(pgSize, nextPageURL, StripeSeed.SESSIONS, id);
        } else if (request.getEntityName().equalsIgnoreCase(StripeSeed.COUPONS)) {
            url = getURL(pgSize, nextPageURL, StripeSeed.COUPONS, id);
        }
        return url;
    }

    private String getURL(int pgSize, String nextPageURL, String entityName, String id) {
        String url;
        if (StringUtils.isEmpty(nextPageURL)) {
            if(StringUtils.isEmpty(id)) {
                url = format(BASE_URL + GET_URL_MAP.get(entityName).get("initial"), pgSize);
            } else {
                url = format(BASE_URL + GET_URL_MAP.get(entityName).get("initial"), id, pgSize);
            }
        } else {
            if(StringUtils.isEmpty(id)) {
                url = format(BASE_URL + GET_URL_MAP.get(entityName).get("nextPage"), pgSize, nextPageURL);
            } else {
                url = format(BASE_URL + GET_URL_MAP.get(entityName).get("nextPage"), id, pgSize, nextPageURL);
            }
        }
        return url;
    }

    private List<EntityData> fetchDataForID(String id, SyncRequest request) {
        StripeRestClient restClient = new StripeRestClient();
        int pgSize = (request.getPageSize() <= 0) ? API_MAX_PAGESIZE : request.getPageSize();
        String nextPageURL = "";
        List<EntityData> results = new ArrayList<>();
        do {
            String url = getURL(request, pgSize, nextPageURL, id);
            DataWithCursor dataWithCursor = restClient.getDataWithCursor(url, request, nextPageURL, getConnectedAccountId(request.getConnector()), request.getEntitySchema());
            nextPageURL = dataWithCursor.getNextPageURL();
            results.addAll(dataWithCursor.getData());
        } while (StringUtils.isNotEmpty(nextPageURL));
        return results;
    }


    public static String getConnectedAccountId(ConnectorInfo info) {
        if(info.getMetaConfig().get("connectedAccountId") != null) {
            return info.getMetaConfig().get("connectedAccountId").toString();
        }
        return null;
    }

    @Override
    public long getFirstCreatedTime(SyncRequest request) {
        return 0;
    }

    @Override
    public List<EntityData> getByIds(SyncRequest request) {
        StripeRestClient restClient = new StripeRestClient();
        List<EntityData> entityList = request.getData().get(request.getConnector().getId());
        List<String> ids = entityList.stream().map(EntityData::getId).collect(Collectors.toList());
        String url = BASE_URL + BY_ID_URL;
        if(request.getEntityName().equalsIgnoreCase(StripeSeed.SESSIONS)) {
            url = BASE_URL + SESSION_BY_ID_URL;
        } else if (request.getEntityName().equalsIgnoreCase(StripeSeed.COUPONS)) {
            url +="?expand[0]=applies_to";
        } else if(request.getEntityName().equalsIgnoreCase(StripeSeed.INVOICES) || request.getEntityName().equalsIgnoreCase(StripeSeed.INVOICE_ITEMS)) {
            url = url + "?expand[]=discounts";
        }
        return restClient.getByIds(request, ids, url, getConnectedAccountId(request.getConnector()));
    }

    @Override
    public SyncResponse create(SyncRequest request) {
        if(request.getEntityName().equalsIgnoreCase(StripeSeed.DISPUTES)) {
            throw new RuntimeException("Disputes cannot be created through stripe API. Only updates are supported");
        }
        StripeRestClient restClient = new StripeRestClient();
        return restClient.createOrUpdate(request, false, BASE_URL, getConnectedAccountId(request.getConnector()));
    }

    @Override
    public SyncResponse update(SyncRequest request) {
        StripeRestClient restClient = new StripeRestClient();
        return restClient.createOrUpdate(request, true, BASE_URL, getConnectedAccountId(request.getConnector()));
    }

    @Override
    public SyncResponse delete(SyncRequest request) {
        StripeRestClient restClient = new StripeRestClient();
        List<EntityData> entityList = request.getData().get(request.getConnector().getId());
        List<String> ids = entityList.stream().map(EntityData::getId).collect(Collectors.toList());
        String url = BASE_URL + BY_ID_URL;
        restClient.deleteByIds(request, ids, url, getConnectedAccountId(request.getConnector()));
        return new SyncResponse(true);
    }

    @Override
    public Optional<EntitySchema> describe(DescribeRequest request) {
        switch (request.getEntity()) {
            case StripeSeed.CUSTOMERS:
                return Optional.of(StripeSeed.getCustomerSchema());
            case StripeSeed.CHARGES:
                return Optional.of(StripeSeed.getChargeSchema());
            case StripeSeed.REFUNDS:
                return Optional.of(StripeSeed.getRefundSchema());
            case StripeSeed.DISPUTES:
                return Optional.of(StripeSeed.getDisputeSchema());
            case StripeSeed.PAYMENT_METHODS:
                return Optional.of(StripeSeed.getPaymentMethodSchema());
            case StripeSeed.PAYMENT_INTENTS:
                return Optional.of(StripeSeed.getPaymentIntentSchema());
            case StripeSeed.PRODUCTS:
                return Optional.of(StripeSeed.getProductSchema());
            case StripeSeed.PRICES:
                return Optional.of(StripeSeed.getPriceSchema());
            case StripeSeed.FILES:
                return Optional.of(StripeSeed.getFileSchema());
            case StripeSeed.BALANCE_TRANSACTIONS:
                return Optional.of(StripeSeed.getBalanceTransactionSchema());
            case StripeSeed.INVOICES:
                return Optional.of(StripeSeed.getInvoicesSchema());
            case StripeSeed.INVOICE_ITEMS:
                return Optional.of(StripeSeed.getInvoiceItemSchema());
            case StripeSeed.SUBSCRIPTIONS:
                return Optional.of(StripeSeed.getSubscriptionSchema());
            case StripeSeed.SUBSCRIPTION_ITEMS:
                return Optional.of(StripeSeed.getSubscriptionItemSchema());
            case StripeSeed.SESSIONS:
                return Optional.of(StripeSeed.getSessionSchema());
            case StripeSeed.COUPONS:
                return Optional.of(StripeSeed.getCouponSchema());
            default:
                throw new RuntimeException("Entity not supported");
        }
    }

    @Override
    public List<EntitySchema> describeAll(DescribeAllRequest request) {
        return List.of(StripeSeed.getCustomerSchema(), StripeSeed.getChargeSchema(), StripeSeed.getRefundSchema(),
                StripeSeed.getDisputeSchema(), StripeSeed.getPaymentMethodSchema(), StripeSeed.getPaymentIntentSchema(),
                StripeSeed.getProductSchema(), StripeSeed.getPriceSchema(), StripeSeed.getFileSchema(),
                StripeSeed.getBalanceTransactionSchema(), StripeSeed.getInvoiceItemSchema(), StripeSeed.getInvoicesSchema(),
                StripeSeed.getSessionItemSchema(), StripeSeed.getSessionSchema(), StripeSeed.getSubscriptionSchema(),
                StripeSeed.getSubscriptionItemSchema(), StripeSeed.getCouponSchema()
        );
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
    public List<AuthMetadata> getSupportedAuthTypes() {
        return List.of(ConnectorHelper.getApiKey());
    }

    @Override
    public List<AuthField> getConfigureFields() {
        AuthField teamId = new AuthField().setName("connectedAccountId").setLabel(i18n("connected_account_id"))
                .setRequired(false)
                .setDataType("text").setHelpSummary(i18n("connected_account_id_summary"));
        return List.of(teamId, ConnectorHelper.getSupportedAuthPicker());
    }

    @Override
    public Map<String, String> getEntityMappings() {
        return Map.of();
    }

    @Override
    public Map<String, String> getAttributeMappings(String entityApiName) {
        return Map.of();
    }

    @Override
    public String getName() {
        return Constants.STRIPE;
    }

    @Override
    public String getCategory() {
        return "Payments Processing";
    }

    @Override
    public UIMetadata getUIMetadata() {
        return new UIMetadata().setIconPath("/assets/icons/logos/stripe.svg")
                .setDisplayName("Stripe")
                .setBackgroundColor("#EFF2F6")
                .setHelpUrl(helpArticlesBaseUrl + "/4536852512148");
    }

    @Override
    public String extractIdentifier(WebhookRequest request) {
        try {
            Map<String, Object> map = mapper.readValue(request.getBody(), Map.class);
            if(map != null && map.containsKey("account")) {
                return map.get("account").toString();
            } else {
                return null;
            }
        } catch (JsonProcessingException e) {
            log.error(ExceptionUtils.getStackTrace(e));
            throw new RuntimeException("Invalid request. The eventdata json is invalid");
        }
    }

    @Override
    public String getIdentifier(ConnectorInfo config) {
        if(config.getMetaConfig().containsKey("connectedAccountId")) {
            return config.getMetaConfig().get("connectedAccountId").toString();
        }
        return config.getInstanceId();
    }

    @Override
    public String getEndpoint() {
        return null;
    }

    @Override
    public List<EventData> parseEventData(WebhookRequest request) {
        log.debug("Parsing event {}", request.getBody());
        Map<String, Object> headers = request.getHeaders();
        if(headers.containsKey("stripe-signature")) {
            Map<String, Object> metaConfig = request.getConfig().getMetaConfig();
            String signingSecret = (String) metaConfig.get("webhook_signing_secret");
            String signatureHeader = (String) headers.get("stripe-signature");
            try {
                Event event = Webhook.constructEvent(request.getBody(), signatureHeader, signingSecret);
                return StripeEventProcessor.processEvent(event, request);
            } catch (JsonSyntaxException e) {
                throw new RuntimeException("Invalid JSON");
            } catch (SignatureVerificationException e) {
                throw new RuntimeException("Invalid request. The signatures do not match.");
            }
        } else {
            throw new RuntimeException("Invalid headers. Missing stripe-signature");
        }
    }

    @Override
    public boolean webhookCreatable() {
        return true;
    }

    @Override
    public String createWebhook(ConnectorInfo config, String spectrumHost) {
        String webhookEndpoint = spectrumHost + "/arcade/api/v1/webhooks/stripe";
        if(!config.getMetaConfig().containsKey("connectedAccountId")) {
            webhookEndpoint = webhookEndpoint + "_" + config.getInstanceId();
        }
        StripeRestClient restClient = new StripeRestClient();
        return restClient.createWebhook(config, webhookEndpoint, BASE_URL + WEBHOOK_URL, webhookEvents);
    }

    @Override
    public void deleteWebhook(ConnectorInfo config) {
        StripeRestClient restClient = new StripeRestClient();
        restClient.deleteWebhook(config, BASE_URL + WEBHOOK_URL);
    }
}
