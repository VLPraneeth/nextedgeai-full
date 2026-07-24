package com.syncari.connector.stripe;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import com.syncari.connector.EntityData;
import com.syncari.connector.Operation;
import com.syncari.connector.data.*;
import net.minidev.json.JSONArray;
import org.apache.commons.lang3.StringUtils;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;

import static java.time.ZoneOffset.UTC;

public class StripeEventProcessor {

    public static final Set<String> DISCOUNT_EVENTS = Set.of("customer.discount.created", "customer.discount.updated", "customer.discount.deleted");

    public static List<EventData> processEvent(Event event, WebhookRequest request) {
        EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
        if (dataObjectDeserializer.getObject().isPresent() || StringUtils.isNotBlank(dataObjectDeserializer.getRawJson())) {
            String json = "";
            if(dataObjectDeserializer.getObject().isPresent()) {
                StripeObject stripeObject = dataObjectDeserializer.getObject().get();
                json = stripeObject.toJson();
            } else {
                json = dataObjectDeserializer.getRawJson();
            }
            List<EventData> eventData = new ArrayList<>();
            switch (event.getType()) {
                case "customer.deleted":
                case "customer.updated":
                    eventData.addAll(processEvent(json, event, request, StripeSeed.getCustomerSchema()));
                    break;
                case "charge.refunded":
                case "charge.updated":
                    eventData.addAll(processEvent(json, event, request, StripeSeed.getChargeSchema()));
                    break;
                case "charge.dispute.closed":
                case "charge.dispute.funds_reinstated":
                case "charge.dispute.funds_withdrawn":
                case "charge.dispute.updated":
                    eventData.addAll(processEvent(json, event, request, StripeSeed.getDisputeSchema()));
                    break;
                case "charge.refund.updated":
                    eventData.addAll(processEvent(json, event, request, StripeSeed.getRefundSchema()));
                    break;
                case "payment_intent.canceled":
                case "payment_intent.payment_failed":
                case "payment_intent.processing":
                case "payment_intent.requires_action":
                case "payment_intent.succeeded":
                    eventData.addAll(processEvent(json, event, request, StripeSeed.getPaymentIntentSchema()));
                    break;
                case "payment_method.attached":
                case "payment_method.automatically_updated":
                case "payment_method.detached":
                case "payment_method.updated":
                    eventData.addAll(processEvent(json, event, request, StripeSeed.getPaymentMethodSchema()));
                    break;
                case "product.updated":
                case "product.deleted":
                    eventData.addAll(processEvent(json, event, request, StripeSeed.getProductSchema()));
                    break;
                case "price.updated":
                case "price.deleted":
                    eventData.addAll(processEvent(json, event, request, StripeSeed.getPriceSchema()));
                    break;
                case "invoice.deleted":
                case "invoice.finalized":
                case "invoice.marked_uncollectible":
                case "invoice.paid":
                case "invoice.payment_action_required":
                case "invoice.payment_failed":
                case "invoice.payment_succeeded":
                case "invoice.sent":
                case "invoice.updated":
                case "invoice.voided":
                    eventData.addAll(processEvent(json, event, request, StripeSeed.getInvoicesSchema()));
                    break;
                case "invoiceitem.created":
                case "invoiceitem.deleted":
                    eventData.addAll(processEvent(json, event, request, StripeSeed.getInvoiceItemSchema()));
                    break;
                case "customer.subscription.deleted":
                case "customer.subscription.pending_update_applied":
                case "customer.subscription.pending_update_expired":
                case "customer.subscription.updated":
                case "customer.discount.updated":
                case "customer.discount.deleted":
                case "customer.discount.created":
                    eventData.addAll(processEvent(json, event, request, StripeSeed.getSubscriptionSchema()));
                    break;
                case "coupon.updated":
                case "coupon.deleted":
                    eventData.addAll(processEvent(json, event, request, StripeSeed.getCouponSchema()));
                    break;
                default:
                    throw new RuntimeException(String.format("Event type %s not supported", event.getType()));
            }
            return eventData;
        } else {
            throw new RuntimeException("Stripe json object deserialization failed");
        }
    }

    public static List<EventData> processEvent(String eventJson, Event event, WebhookRequest request, EntitySchema schema) {
        List<EventData> results = new ArrayList<>();
        EventData eventData = new EventData();
        StripeRestClient restClient = new StripeRestClient();
        ReadContext ctx = JsonPath.parse(eventJson);
        if(DISCOUNT_EVENTS.contains(event.getType())) {
            extractCoupon(results, ctx, request.getConfig().getId(), event.getCreated());
        } else {
            SyncRequest syncRequest = new SyncRequest().setConnector(request.getConfig());
            EntityData entityData = restClient.parseJSON(ctx, schema, "", Optional.of(syncRequest));
            entityData.setConnectorId(request.getConfig().getId());
            entityData.setLastModified(ZonedDateTime.ofInstant(Instant.ofEpochSecond(event.getCreated()), UTC).toEpochSecond() * 1000);
            Operation operation = getOperation(event);
            if (Operation.delete == operation) {
                entityData.setDeleted(true);
            }
            eventData.setData(entityData);
            eventData.setOperation(operation);
            results.add(eventData);
            if (event.getType().equalsIgnoreCase("invoice.updated")) {
                // Fetch invoice items
                List<EventData> invoiceItems = fetchInvoiceItems(request, entityData.getId(), event);
                results.addAll(invoiceItems);
            }
            if (event.getType().equalsIgnoreCase("customer.subscription.updated") ||
                    event.getType().equalsIgnoreCase("customer.subscription.pending_update_applied")) {
                // Fetch subscription items
                List<EventData> subscriptionItems = fetchSubscriptionItems(request, entityData.getId(), event, entityData);
                results.addAll(subscriptionItems);
            }
        }
        return results;
    }

    private static void extractCoupon(List<EventData> results, ReadContext ctx, String connectorId, Long eventTime) {
        String couponId = ctx.read("coupon.id");
        String customerId = ctx.read("customer");
        String subscriptionId = ctx.read("subscription");
        if(customerId != null) {
            EntityData customerData = new EntityData(StripeSeed.CUSTOMERS);
            customerData.setId(customerId);
            customerData.addValue("coupon", couponId);
            customerData.setConnectorId(connectorId);
            customerData.setLastModified(ZonedDateTime.ofInstant(Instant.ofEpochSecond(eventTime), UTC).toEpochSecond() * 1000);
            EventData eventData = new EventData();
            eventData.setOperation(Operation.update);
            eventData.setData(customerData);
            results.add(eventData);
        }
        if(subscriptionId != null) {
            EntityData subscriptionData = new EntityData(StripeSeed.SUBSCRIPTIONS);
            subscriptionData.setId(subscriptionId);
            subscriptionData.addValue("coupon", couponId);
            subscriptionData.setConnectorId(connectorId);
            subscriptionData.setLastModified(ZonedDateTime.ofInstant(Instant.ofEpochSecond(eventTime), UTC).toEpochSecond() * 1000);
            EventData eventData = new EventData();
            eventData.setOperation(Operation.update);
            eventData.setData(subscriptionData);
            results.add(eventData);
        }
    }

    private static List<EventData> fetchInvoiceItems(WebhookRequest request, String id, Event event) {
        List<EventData> results = new ArrayList<>();
        StripeRestClient restClient = new StripeRestClient();
        String cursor = "";
        String url = StripeService.BASE_URL + "/invoiceitems?limit=100&invoice=" + id;
        SyncRequest syncRequest = new SyncRequest().setEntitySchema(StripeSeed.getInvoiceItemSchema()).setConnector(request.getConfig());
        do {
            DataWithCursor dataWithCursor = restClient.getDataWithCursor(url, syncRequest, cursor, StripeService.getConnectedAccountId(request.getConfig()), StripeSeed.getInvoiceItemSchema());
            dataWithCursor.getData().forEach(data -> {
                if(data.getLastModified() <= 0) {
                    data.setLastModified(ZonedDateTime.ofInstant(Instant.ofEpochSecond(event.getCreated()), UTC).toEpochSecond() * 1000);
                }
                EventData eventData = new EventData();
                eventData.setData(data);
                eventData.setOperation(Operation.update);
                eventData.setConnectorId(request.getConfig().getId());
                results.add(eventData);
            });
            cursor = dataWithCursor.getNextPageURL();
            url = StripeService.BASE_URL + "/invoiceitems?limit=100&invoice=" + id + "&starting_after=" + cursor;
        } while (StringUtils.isNotBlank(cursor));
        return results;
    }

    private static List<EventData> fetchSubscriptionItems(WebhookRequest request, String id, Event event, EntityData subscription) {
        List<EventData> results = new ArrayList<>();
        StripeRestClient restClient = new StripeRestClient();
        String cursor = "";
        String url = StripeService.BASE_URL + "/subscription_items?limit=100&subscription=" + id;
        SyncRequest syncRequest = new SyncRequest().setEntitySchema(StripeSeed.getSubscriptionItemSchema()).setConnector(request.getConfig());
        Set<String> currentItemIds = new HashSet<>();
        // Reset items so we don't end up with duplicates
        subscription.addValue("items", new ArrayList<>());
        do {
            DataWithCursor dataWithCursor = restClient.getDataWithCursor(url, syncRequest, cursor, StripeService.getConnectedAccountId(request.getConfig()), StripeSeed.getSubscriptionItemSchema());
            dataWithCursor.getData().forEach(data -> {
                if(data.getLastModified() <= 0) {
                    data.setLastModified(ZonedDateTime.ofInstant(Instant.ofEpochSecond(event.getCreated()), UTC).toEpochSecond() * 1000);
                }
                String itemId = data.getId();
                data.setId(subscription.getId() + "#" + itemId);
                ((List<EntityData>)subscription.getValue("items")).add(data);
                data.setConnectorId(request.getConfig().getId());
                // Need to be added as separate events for standalone child pipelines
                EventData eventData = new EventData();
                eventData.setData(data);
                eventData.setOperation(Operation.update);
                results.add(eventData);
                currentItemIds.add(data.getId());
            });
            cursor = dataWithCursor.getNextPageURL();
            url = StripeService.BASE_URL + "/subscription_items?limit=100&subscription=" + id + "&starting_after=" + cursor;
        } while (StringUtils.isNotBlank(cursor));
        Set<String> previousItemIds = getPreviousItemIds(event, subscription);
        addDeletedItems(request, event, previousItemIds, currentItemIds, results, subscription);
        return results;
    }

    public static void addDeletedItems(WebhookRequest request, Event event, Set<String> previousItemIds, Set<String> currentItemIds, List<EventData> results, EntityData subscription) {
        if(!previousItemIds.isEmpty() && !currentItemIds.equals(previousItemIds)) {
            previousItemIds.forEach(item -> {
                if(!currentItemIds.contains(item)) {
                    EventData eventData = new EventData();
                    EntityData entityData = new EntityData(StripeSeed.SUBSCRIPTION_ITEMS);
                    entityData.setId(item);
                    entityData.setConnectorId(request.getConfig().getId());
                    entityData.setLastModified(ZonedDateTime.ofInstant(Instant.ofEpochSecond(event.getCreated()), UTC).toEpochSecond() * 1000);
                    entityData.setDeleted(true);
                    eventData.setData(entityData);
                    eventData.setOperation(Operation.delete);
                    results.add(eventData);
                }
            });
        }
    }

    public static Set<String> getPreviousItemIds(Event event, EntityData subscription) {
        Set<String> previousItemIds = new HashSet<>();
        if(event.getData() != null && event.getData().getPreviousAttributes() != null && event.getData().getPreviousAttributes().containsKey("items")) {
            Map<String, Object> itemsMap = (Map<String, Object>)event.getData().getPreviousAttributes().get("items");
            if(itemsMap.containsKey("data")) {
                List<Map<String, Object>> dataMap = (List<Map<String, Object>>)itemsMap.get("data");
                dataMap.forEach(item -> {
                    if(item.containsKey("id")) {
                        previousItemIds.add(subscription.getId() + "#" + item.get("id"));
                    }
                });
            }
        }
        return previousItemIds;
    }

    private static Operation getOperation(Event event) {
        if(event.getType().contains(".deleted")) {
            return Operation.delete;
        } else {
            return Operation.update;
        }
    }
}
