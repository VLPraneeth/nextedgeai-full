package com.syncari.connector.oraclepim;

import com.syncari.connector.ConnectorInfo;
import org.apache.axis.encoding.Base64;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.soap.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class OraclePIMServiceSoapClient {
    private static final String CONJUNCTION = "conjunction";
    private static final String FILTER = "filter";
    private static final String AND_CONDITION = "And";
    private static final String OR_CONDITION = "Or";
    private static final String EQUALS = "=";
    private static final String OPERATOR = "operator";
    private static final String GROUP = "group";
    private static final String UPPER_CASE_COMPARE = "upperCaseCompare";
    private static final String ATTRIBUTE = "attribute";
    private static final String VALUE = "value";
    private static final String FIND_ITEM_TAG = "findItem";
    private static final String FIND_CRITERIA_TAG = "findCriteria";
    private static final String EXCLUDE_ATTRIBUTE_TAG = "excludeAttribute";
    private static final String FIND_CONTROL_TAG = "findControl";
    private static final String RETRIEVE_ALL_TRANSLATIONS_TAG = "retrieveAllTranslations";
    private static final String FETCH_START_TAG = "fetchStart";
    private static final String FETCH_SIZE_TAG = "fetchSize";

    private static final String SOAP_ENV_KEY = "myNamespace";
    private static final String SOAP_ENV_VALUE = "http://schemas.xmlsoap.org/soap/envelope/";
    private static final String TYP = "typ";
    private static final String TYP_VALUE = "http://xmlns.oracle.com/apps/scm/productModel/items/itemServiceV2/types/";
    private static final String TYP1 = "typ1";
    private static final String TYP1_VALUE = "http://xmlns.oracle.com/adf/svc/types/";

    private static final String ITEM_CLASS_CONFIG_KEY = "itemClass"; //TODO: CHECK
    private static final String ORGANIZATION_CODE_CONFIG_KEY = "organizationCode"; //TODO: CHECK
    private static final String ITEM_STATUS_VALUE = "ItemStatusValue";
    private static final String ITEM = "item";
    private static final String ORGANIZATION_CODE = "OrganizationCode";
    private static final String ITEM_CLASS = "ItemClass";
    private static final String ITEM_ID = "ItemId";
    private static final String ITEM_STATUS_INPUT = "itemStatus";

    private static final String TRUE = "true";
    private static final String FALSE = "false";
    private static final String ACTIVE = "Active";

    private static final Logger log = LoggerFactory.getLogger(OraclePIMServiceSoapClient.class);

    public SOAPMessage getItems(int start, int pageSize, ConnectorInfo config, String url) {
        SOAPMessage payload = getItemsPayload(start, pageSize, config);
        return makeRequest(url, payload);
    }

    private void addItemIdFilters(String itemId, SOAPElement filterElement) {
        if (StringUtils.isBlank(itemId))
            return;
        try {
            SOAPElement group = filterElement.addChildElement(GROUP, TYP1);
            group.addChildElement(CONJUNCTION, TYP1).addTextNode(AND_CONDITION);
            group.addChildElement(UPPER_CASE_COMPARE, TYP1).addTextNode(FALSE);
            SOAPElement item = group.addChildElement(ITEM, TYP1);
            item.addChildElement(CONJUNCTION, TYP1).addTextNode(AND_CONDITION);
            item.addChildElement(UPPER_CASE_COMPARE, TYP1).addTextNode(FALSE);
            item.addChildElement(ATTRIBUTE, TYP1).addTextNode(ITEM_ID);
            item.addChildElement(OPERATOR, TYP1).addTextNode(EQUALS);
            item.addChildElement(VALUE, TYP1).addTextNode(itemId);
        } catch (SOAPException e) {
            throw new RuntimeException(e);
        }
    }

    private void addItemFilters(String itemId, ConnectorInfo config, SOAPElement parentElement) {
        String itemClasses = config.getMetaConfig().getOrDefault(ITEM_CLASS_CONFIG_KEY, "").toString();
        String org = config.getMetaConfig().getOrDefault(ORGANIZATION_CODE_CONFIG_KEY, "").toString();
        String itemStatusValue = config.getMetaConfig().getOrDefault(ITEM_STATUS_INPUT, "").toString();
        if (StringUtils.isBlank(itemClasses) && StringUtils.isBlank(org) && StringUtils.isBlank(itemId))
            return;
        SOAPElement filterElement;
        try {
            filterElement = parentElement.addChildElement(FILTER, TYP1);
            filterElement.addChildElement(CONJUNCTION, TYP1).addTextNode(AND_CONDITION);
            addItemIdFilters(itemId, filterElement);
            addItemClassFilters(itemClasses, filterElement);
            addItemStatusFilters(itemStatusValue, filterElement);
            addOrgFilters(org, filterElement);
        } catch (SOAPException e) {
            throw new RuntimeException(e);
        }
    }

    private void addItemStatusFilters(String itemStatusValue, SOAPElement filterElement) {
        List<String> itemStatusList = List.of(StringUtils.split(itemStatusValue, ","));
        if (itemStatusList.isEmpty())
            return;
        try {
            SOAPElement group = filterElement.addChildElement(GROUP, TYP1);
            group.addChildElement(CONJUNCTION, TYP1).addTextNode(AND_CONDITION);
            group.addChildElement(UPPER_CASE_COMPARE, TYP1).addTextNode(FALSE);
            for (String itemStatus : itemStatusList){
                itemStatus = itemStatus.stripLeading().stripTrailing();
                SOAPElement item = group.addChildElement(ITEM, TYP1);
                item.addChildElement(CONJUNCTION, TYP1).addTextNode(OR_CONDITION);
                item.addChildElement(UPPER_CASE_COMPARE, TYP1).addTextNode(FALSE);
                item.addChildElement(ATTRIBUTE, TYP1).addTextNode(ITEM_STATUS_VALUE);
                item.addChildElement(OPERATOR, TYP1).addTextNode(EQUALS);
                item.addChildElement(VALUE, TYP1).addTextNode(itemStatus);
            }
        } catch (SOAPException e) {
            throw new RuntimeException(e);
        }
    }

    private void addOrgFilters(String orgInput, SOAPElement filterElement) {
        String org = orgInput.stripLeading().stripTrailing();
        if (StringUtils.isBlank(org))
            return;
        try {
            SOAPElement group = filterElement.addChildElement(GROUP, TYP1);
            group.addChildElement(CONJUNCTION, TYP1).addTextNode(AND_CONDITION);
            group.addChildElement(UPPER_CASE_COMPARE, TYP1).addTextNode(FALSE);
            SOAPElement item = group.addChildElement(ITEM, TYP1);
            item.addChildElement(CONJUNCTION, TYP1).addTextNode(AND_CONDITION);
            item.addChildElement(UPPER_CASE_COMPARE, TYP1).addTextNode(FALSE);
            item.addChildElement(ATTRIBUTE, TYP1).addTextNode(ORGANIZATION_CODE);
            item.addChildElement(OPERATOR, TYP1).addTextNode(EQUALS);
            item.addChildElement(VALUE, TYP1).addTextNode(org);
        } catch (SOAPException e) {
            throw new RuntimeException(e);
        }
    }

    private void addItemClassFilters(String itemClassInput, SOAPElement filterElement) {
        List<String> itemClassList = List.of(StringUtils.split(itemClassInput, ","));
        if (itemClassList.isEmpty())
            return;
        try {
            SOAPElement group = filterElement.addChildElement(GROUP, TYP1);
            group.addChildElement(CONJUNCTION, TYP1).addTextNode(AND_CONDITION);
            group.addChildElement(UPPER_CASE_COMPARE, TYP1).addTextNode(FALSE);
            for (String itemClass: itemClassList){
                itemClass = itemClass.stripLeading().stripTrailing();
                SOAPElement item = group.addChildElement(ITEM, TYP1);
                item.addChildElement(CONJUNCTION, TYP1).addTextNode(OR_CONDITION);
                item.addChildElement(UPPER_CASE_COMPARE, TYP1).addTextNode(FALSE);
                item.addChildElement(ATTRIBUTE, TYP1).addTextNode(ITEM_CLASS);
                item.addChildElement(OPERATOR, TYP1).addTextNode(EQUALS);
                item.addChildElement(VALUE, TYP1).addTextNode(itemClass);
            }
        } catch (SOAPException e) {
            throw new RuntimeException(e);
        }
    }

    private String getAuthString(ConnectorInfo config) {
        String username = config.getAuthConfig().getUserName();
        String password = config.getAuthConfig().getPassword();
        return username+":"+password;
    }

    public SOAPMessage getItem(String url, ConnectorInfo config) {
        SOAPMessage payload = getItemsPayload(0,1, config);
        return makeRequest(url, payload);
    }

    public SOAPMessage getItemById(String id, String url, ConnectorInfo config) {
        SOAPMessage payload = getItemsPayload(0, 1, config, id);
        return makeRequest(url, payload);
    }

    public SOAPMessage getItemsPayload(int start, int pageSize, ConnectorInfo config, String itemId) {

        MessageFactory messageFactory;
        SOAPMessage soapMessage;
        try {
            messageFactory = MessageFactory.newInstance();
            soapMessage = messageFactory.createMessage();
            SOAPPart soapPart = soapMessage.getSOAPPart();

            SOAPEnvelope envelope = soapPart.getEnvelope();
            envelope.addNamespaceDeclaration(SOAP_ENV_KEY, SOAP_ENV_VALUE);
            envelope.addNamespaceDeclaration(TYP, TYP_VALUE);
            envelope.addNamespaceDeclaration(TYP1, TYP1_VALUE);

            SOAPBody soapBody = envelope.getBody();
            SOAPElement findItem = soapBody.addChildElement(FIND_ITEM_TAG, TYP);
            SOAPElement findCriteria = findItem.addChildElement(FIND_CRITERIA_TAG, TYP);
            addItemFilters(itemId, config, findCriteria);
            SOAPElement fetchStart = findCriteria.addChildElement(FETCH_START_TAG, TYP1);
            fetchStart.addTextNode(String.valueOf(start));
            SOAPElement fetchSize = findCriteria.addChildElement( FETCH_SIZE_TAG, TYP1);
            fetchSize.addTextNode(String.valueOf(pageSize));
            SOAPElement excludeAttribute = findCriteria.addChildElement( EXCLUDE_ATTRIBUTE_TAG, TYP1);
            excludeAttribute.addTextNode(FALSE);

            SOAPElement findControl = findItem.addChildElement( FIND_CONTROL_TAG, TYP);
            SOAPElement retrieveAllTranslations = findControl.addChildElement( RETRIEVE_ALL_TRANSLATIONS_TAG, TYP1);
            retrieveAllTranslations.addTextNode(TRUE);

            byte[] authBytes = getAuthString(config).getBytes(StandardCharsets.UTF_8);

            String auth = Base64.encode(authBytes);
            MimeHeaders headers = soapMessage.getMimeHeaders();
            headers.addHeader("Authorization", "Basic " + auth);
            soapMessage.saveChanges();
            return soapMessage;
        } catch (Exception e) {
            log.error("Error constructing payload for items resource. Error : "+e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public SOAPMessage getItemsPayload(int start, int pageSize, ConnectorInfo config) {
        return getItemsPayload(start, pageSize, config, "");
    }

    private SOAPMessage makeRequest(String endPointURL, SOAPMessage payload) {
        try {
            SOAPConnectionFactory soapConnectionFactory = SOAPConnectionFactory.newInstance();
            SOAPConnection soapConnection = soapConnectionFactory.createConnection();
            SOAPMessage soapResponse = soapConnection.call(payload, endPointURL);
            soapConnection.close();
            return soapResponse;
        } catch (Exception e) {
            log.error("Error making soap request for Url : "+endPointURL+". Error : "+e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
