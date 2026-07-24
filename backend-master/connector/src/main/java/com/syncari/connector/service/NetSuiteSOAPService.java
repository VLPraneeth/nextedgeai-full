package com.syncari.connector.service;

import com.netsuite.suitetalk.client.common.authentication.TokenPassport;
import com.netsuite.suitetalk.client.v2020_1.WsClient;
import com.netsuite.suitetalk.client.v2020_1.utils.Utils;
import com.netsuite.suitetalk.proxy.v2020_1.documents.filecabinet.File;
import com.netsuite.suitetalk.proxy.v2020_1.documents.filecabinet.types.MediaType;
import com.netsuite.suitetalk.proxy.v2020_1.lists.accounting.Subsidiary;
import com.netsuite.suitetalk.proxy.v2020_1.lists.accounting.types.ItemSubType;
import com.netsuite.suitetalk.proxy.v2020_1.lists.accounting.types.ItemType;
import com.netsuite.suitetalk.proxy.v2020_1.lists.relationships.*;
import com.netsuite.suitetalk.proxy.v2020_1.platform.common.*;
import com.netsuite.suitetalk.proxy.v2020_1.platform.common.types.Country;
import com.netsuite.suitetalk.proxy.v2020_1.platform.core.*;
import com.netsuite.suitetalk.proxy.v2020_1.platform.core.types.*;
import com.netsuite.suitetalk.proxy.v2020_1.platform.faults.*;
import com.netsuite.suitetalk.proxy.v2020_1.platform.messages.ReadResponse;
import com.netsuite.suitetalk.proxy.v2020_1.setup.customization.*;
import com.netsuite.suitetalk.proxy.v2020_1.setup.customization.types.CustomizationFieldType;
import com.netsuite.suitetalk.proxy.v2020_1.transactions.employees.PaycheckJournal;
import com.netsuite.suitetalk.proxy.v2020_1.transactions.inventory.BinWorksheet;
import com.netsuite.suitetalk.proxy.v2020_1.transactions.sales.*;
import com.netsuite.suitetalk.proxy.v2020_1.transactions.sales.types.TransactionType;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.exception.RetriableException;
import com.syncari.connector.service.seed.NetsuiteSeed;
import com.syncari.utils.I18n;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.axis.AxisFault;
import org.apache.axis.client.Stub;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.rmi.RemoteException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.netsuite.suitetalk.client.common.utils.CommonUtils.composeUrl;
import static com.netsuite.suitetalk.proxy.v2020_1.setup.customization.types.CustomizationFieldType.*;
import static com.syncari.connector.service.NetSuiteService.SAVED_SEARCH_PREFIX;
import static com.syncari.utils.ExceptionUtils.rethrow;
import static java.lang.String.format;

@Slf4j
@Component
public class NetSuiteSOAPService {
    private static final int SESSION_EXPIRY_MINUTES = 15;
    private static final int PAGE_SIZE = 1000;

    private static final String[] supportedDocumentTypes = 
        new String[]{MediaType.__PDF, MediaType.__CSV, MediaType.__EXCEL, MediaType.__GZIP, MediaType.__GIFIMAGE,
            MediaType.__ICON, MediaType.__IMAGE, MediaType.__BMPIMAGE, MediaType.__JPGIMAGE, MediaType.__JSON,
            MediaType.__MISCTEXT, MediaType.__MP3, MediaType.__PDF, MediaType.__PLAINTEXT, MediaType.__PNGIMAGE,
            MediaType.__POWERPOINT, MediaType.__STYLESHEET, MediaType.__SVG, MediaType.__TAR, MediaType.__TIFFIMAGE,
            MediaType.__WORD, MediaType.__XMLDOC, MediaType.__XSD, MediaType.__ZIP};

    private static Map<String, String> STANDARD_OBJECTS = new HashMap<>();
    private static int SERVICE_SOCKET_TIMEOUT=900000;
    static{
        STANDARD_OBJECTS.put("-112","ACCOUNT");
        STANDARD_OBJECTS.put("-105","ACCOUNTINGPERIOD");
        STANDARD_OBJECTS.put("-289","ADDRESS");
        STANDARD_OBJECTS.put("-255","APPDEFINITION");
        STANDARD_OBJECTS.put("-254","APPPACKAGE");
        STANDARD_OBJECTS.put("-333","BILLINGACCOUNT");
        STANDARD_OBJECTS.put("-141","BILLINGSCHEDULE");
        STANDARD_OBJECTS.put("-242","BIN");
        STANDARD_OBJECTS.put("-422","BOM");
        STANDARD_OBJECTS.put("-423","BOMREVISION");
        STANDARD_OBJECTS.put("-396","BUDGETCATEGORY");
        STANDARD_OBJECTS.put("-420","BUDGETIMPORT");
        STANDARD_OBJECTS.put("-20","CALENDAREVENT");
        STANDARD_OBJECTS.put("-24","CAMPAIGN");
        STANDARD_OBJECTS.put("-142","CAMPAIGNAUDIENCE");
        STANDARD_OBJECTS.put("-143","CAMPAIGNCATEGORY");
        STANDARD_OBJECTS.put("-144","CAMPAIGNCHANNEL");
        STANDARD_OBJECTS.put("-145","CAMPAIGNFAMILY");
        STANDARD_OBJECTS.put("-146","CAMPAIGNOFFER");
        STANDARD_OBJECTS.put("-130","CAMPAIGNRESPONSE");
        STANDARD_OBJECTS.put("-148","CAMPAIGNSEARCHENGINE");
        STANDARD_OBJECTS.put("-149","CAMPAIGNSUBSCRIPTION");
        STANDARD_OBJECTS.put("-150","CAMPAIGNVERTICAL");
        STANDARD_OBJECTS.put("-290","CHARGE");
        STANDARD_OBJECTS.put("-101","CLASSIFICATION");
        STANDARD_OBJECTS.put("-322","CONSOLIDATEDEXCHANGERATE");
        STANDARD_OBJECTS.put("-6","CONTACT");
        STANDARD_OBJECTS.put("-158","CONTACTCATEGORY");
        STANDARD_OBJECTS.put("-157","CONTACTROLE");
        STANDARD_OBJECTS.put("-155","COSTCATEGORY");
        STANDARD_OBJECTS.put("-122","CURRENCY");
        STANDARD_OBJECTS.put("-2","CUSTOMER");
        STANDARD_OBJECTS.put("-109","CUSTOMERCATEGORY");
        STANDARD_OBJECTS.put("-161","CUSTOMERMESSAGE");
        STANDARD_OBJECTS.put("-104","CUSTOMERSTATUS");
        STANDARD_OBJECTS.put("-123","CUSTOMLIST");
        STANDARD_OBJECTS.put("-123","CUSTOMRECORDTYPE");
        STANDARD_OBJECTS.put("-102","DEPARTMENT");
        STANDARD_OBJECTS.put("-4","EMPLOYEE");
        STANDARD_OBJECTS.put("-126","EXPENSECATEGORY");

        STANDARD_OBJECTS.put("-332","FAIRVALUEPRICE");
        STANDARD_OBJECTS.put("-540","GENERALTOKEN");
        STANDARD_OBJECTS.put("-250","GLOBALACCOUNTMAPPING");
        STANDARD_OBJECTS.put("-355","HCMJOB");
        STANDARD_OBJECTS.put("-427","INBOUNDSHIPMENT");

        STANDARD_OBJECTS.put("-260","INVENTORYDETAIL");
        STANDARD_OBJECTS.put("-266","INVENTORYNUMBER");
        STANDARD_OBJECTS.put("-26","ISSUE");
        STANDARD_OBJECTS.put("-251","ITEMACCOUNTMAPPING");

        STANDARD_OBJECTS.put("-246","ITEMDEMANDPLAN");
        STANDARD_OBJECTS.put("-124","ITEMCUSTOMFIELD");
        STANDARD_OBJECTS.put("-124","ITEMNUMBERCUSTOMFIELD");
        STANDARD_OBJECTS.put("-124","ITEMOPTIONCUSTOMFIELD");
        STANDARD_OBJECTS.put("-124","OTHERCUSTOMFIELD");
        STANDARD_OBJECTS.put("-124","ENTITYCUSTOMFIELD");
        STANDARD_OBJECTS.put("-124","CUSTOMRECORDCUSTOMFIELD");
        STANDARD_OBJECTS.put("-124","CRMCUSTOMFIELD");

        STANDARD_OBJECTS.put("-269","ITEMREVISION");
        STANDARD_OBJECTS.put("-247","ITEMSUPPLYPLAN");
        STANDARD_OBJECTS.put("-7","JOB");
        STANDARD_OBJECTS.put("-177","JOBTYPE");

        STANDARD_OBJECTS.put("-103","LOCATION");
        STANDARD_OBJECTS.put("-294","MANUFACTURINGCOSTTEMPLATE");
        STANDARD_OBJECTS.put("-36","MANUFACTURINGOPERATIONTASK");
        STANDARD_OBJECTS.put("-288","MANUFACTURINGROUTING");
        STANDARD_OBJECTS.put("-400","NEXUS");
        STANDARD_OBJECTS.put("-303","NOTE");
        STANDARD_OBJECTS.put("-180","NOTETYPE");
        STANDARD_OBJECTS.put("-31","OPPORTUNITY");
        STANDARD_OBJECTS.put("-181","OTHERNAMECATEGORY");
        STANDARD_OBJECTS.put("-5","PARTNER");

        STANDARD_OBJECTS.put("-182","PARTNERCATEGORY");
        STANDARD_OBJECTS.put("-538","PAYMENTCARD");
        STANDARD_OBJECTS.put("-539","PAYMENTCARDTOKEN");
        STANDARD_OBJECTS.put("-183","PAYMENTMETHOD");
        STANDARD_OBJECTS.put("-265","PAYROLLITEM");
        STANDARD_OBJECTS.put("-22","PHONECALL");
        STANDARD_OBJECTS.put("-186","PRICELEVEL");
        STANDARD_OBJECTS.put("-187","PRICINGGROUP");
        STANDARD_OBJECTS.put("-27","PROJECTTASK");
        STANDARD_OBJECTS.put("-121","PROMOTIONCODE");
        STANDARD_OBJECTS.put("-28","RESOURCEALLOCATION");
        STANDARD_OBJECTS.put("-191","SALESROLE");
        STANDARD_OBJECTS.put("-128","SALESTAXITEM");
        STANDARD_OBJECTS.put("-25","SOLUTION");
        STANDARD_OBJECTS.put("-117","SUBSIDIARY");
        STANDARD_OBJECTS.put("-23","SUPPORTCASE");
        STANDARD_OBJECTS.put("-151","SUPPORTCASEISSUE");
        STANDARD_OBJECTS.put("-152","SUPPORTCASEORIGIN");



        STANDARD_OBJECTS.put("-10","MARKUPITEM");
        STANDARD_OBJECTS.put("-10","ASSEMBLYITEM");
        STANDARD_OBJECTS.put("-10","DESCRIPTIONITEM");
        STANDARD_OBJECTS.put("-10","DISCOUNTITEM");
        STANDARD_OBJECTS.put("-10","DOWNLOADITEM");
        STANDARD_OBJECTS.put("-10","GIFTCERTIFICATEITEM");
        STANDARD_OBJECTS.put("-10","INVENTORYITEM");
        STANDARD_OBJECTS.put("-10","ITEMGROUP");
        STANDARD_OBJECTS.put("-10","KITITEM");
        STANDARD_OBJECTS.put("-10","LOTNUMBEREDASSEMBLYITEM");
        STANDARD_OBJECTS.put("-10","LOTNUMBEREDINVENTORYITEM");
        STANDARD_OBJECTS.put("-10","NONINVENTORYPURCHASEITEM");
        STANDARD_OBJECTS.put("-10","NONINVENTORYRESALEITEM");
        STANDARD_OBJECTS.put("-10","NONINVENTORYSALEITEM");
        STANDARD_OBJECTS.put("-10","OTHERCHARGEPURCHASEITEM");
        STANDARD_OBJECTS.put("-10","OTHERCHARGERESALEITEM");
        STANDARD_OBJECTS.put("-10","OTHERCHARGESALEITEM");
        STANDARD_OBJECTS.put("-10","PAYMENTITEM");
        STANDARD_OBJECTS.put("-10","SERIALIZEDASSEMBLYITEM");
        STANDARD_OBJECTS.put("-10","SERIALIZEDINVENTORYITEM");
        STANDARD_OBJECTS.put("-10","SERVICEPURCHASEITEM");
        STANDARD_OBJECTS.put("-10","SERVICERESALEITEM");
        STANDARD_OBJECTS.put("-10","SERVICESALEITEM");
        STANDARD_OBJECTS.put("-10","SUBTOTALITEM");
        STANDARD_OBJECTS.put("-10","ITEM");


        STANDARD_OBJECTS.put("-30","STATISTICALJOURNALENTRY");
        STANDARD_OBJECTS.put("-30","PAYCHECK");
        STANDARD_OBJECTS.put("-30","PAYCHECKJOURNAL");
        STANDARD_OBJECTS.put("-30","PURCHASEORDER");
        STANDARD_OBJECTS.put("-30","PURCHASEREQUISITION");
        STANDARD_OBJECTS.put("-30","RETURNAUTHORIZATION");
        STANDARD_OBJECTS.put("-30","SALESORDER");
        STANDARD_OBJECTS.put("-30","CREDITMEMO");
        STANDARD_OBJECTS.put("-30","DEPOSIT");
        STANDARD_OBJECTS.put("-30","CUSTOMERDEPOSIT");
        STANDARD_OBJECTS.put("-30","DEPOSITAPPLICATION");
        STANDARD_OBJECTS.put("-30","CUSTOMERPAYMENT");
        STANDARD_OBJECTS.put("-30","CUSTOMERREFUND");
        STANDARD_OBJECTS.put("-30","ESTIMATE");
        STANDARD_OBJECTS.put("-30","EXPENSEREPORT");
        STANDARD_OBJECTS.put("-30","CUSTOMTRANSACTION");
        STANDARD_OBJECTS.put("-30","ADVINTERCOMPANYJOURNALENTRY");
        STANDARD_OBJECTS.put("-30","ASSEMBLYBUILD");
        STANDARD_OBJECTS.put("-30","ASSEMBLYUNBUILD");
        STANDARD_OBJECTS.put("-30","BINTRANSFER");
        STANDARD_OBJECTS.put("-30","BINWORKSHEET");
        STANDARD_OBJECTS.put("-30","CASHREFUND");
        STANDARD_OBJECTS.put("-30","CASHSALE");
        STANDARD_OBJECTS.put("-30","CHECK");
        STANDARD_OBJECTS.put("-30","INTERCOMPANYJOURNALENTRY");
        STANDARD_OBJECTS.put("-30","INTERCOMPANYTRANSFERORDER");
        STANDARD_OBJECTS.put("-30","INVENTORYADJUSTMENT");
        STANDARD_OBJECTS.put("-30","INVENTORYCOSTREVALUATION");
        STANDARD_OBJECTS.put("-30","INVENTORYTRANSFER");
        STANDARD_OBJECTS.put("-30","ITEMFULFILLMENT");
        STANDARD_OBJECTS.put("-30","ITEMRECEIPT");
        STANDARD_OBJECTS.put("-30","INVOICE");
        STANDARD_OBJECTS.put("-30","JOURNALENTRY");
    }

    private static List<String> ITEM_OBJECTS = List.of(
        "assemblyitem",
        "inventoryitem",
        "itemgroup",
        "noninventorypurchaseitem",
        "noninventoryresaleitem",
        "noninventorysaleitem",
        "kititem",
        "otherchargepurchaseitem",
        "otherchargeresaleitem",
        "otherchargesaleitem",
        "paymentitem",
        "servicepurchaseitem",
        "serviceresaleitem",
        "servicesaleitem",
        "descriptionitem",
        "discountitem",
        "giftcertificateitem",
        "markupitem",
        "subtotalitem"
    );

    public WsClient getClient(ConnectorInfo config) {
        return rethrow(()-> {
            String account = new URL(config.getEndpoint()).getHost().split("\\.")[0].replace("-", "_").toUpperCase();
            AuthConfig authConfig = config.getAuthConfig();
            TokenPassport tokenPassport = new TokenPassport(account, authConfig.getConsumerKey(), authConfig.getConsumerSecret()
                    , authConfig.getTokenId(), authConfig.getTokenSecret());
            WsClient client = new WsClient(tokenPassport, getWebServicesUrl(config.getEndpoint()));
            ((Stub)client.getPort()).setTimeout(SERVICE_SOCKET_TIMEOUT);
            return client;
        });
    }

    public URL getWebServicesUrl(String baseURL) throws MalformedURLException {

        URL url = new URL(baseURL + "/services/NetSuitePort_2020_1");
        return composeUrl(url.getProtocol(), url.getHost(), url.getPort());
    }

    public Map<String, Map<String, Object>> fetchAllSubsidiary(WsClient client, Optional<List<String>> ids) {
        if(ids.isPresent()) {
            List<EntityData> records = ids.get().stream().map(id -> new EntityData().setId(id)).collect(Collectors.toList());
            Optional<SearchMultiSelectField> searchField = getSearchField(records, "subsidiary", RecordType.subsidiary);
            return searchField.map(s->{
                SubsidiarySearchBasic subsidiarySearchBasic = new SubsidiarySearchBasic();
                subsidiarySearchBasic.setInternalId(s);
                List<?> res;
                try {
                    res = client.searchAll(subsidiarySearchBasic);
                } catch (RemoteException e) {
                    throw new RuntimeException(e);
                }
                return processSearchResults(res);
            }).orElse(Map.of());
        }
        try {
            SubsidiarySearchBasic subsidiarySearchBasic = new SubsidiarySearchBasic();
            var res = client.searchAll(subsidiarySearchBasic);
            return processSearchResults(res);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<String, Map<String, Object>> processSearchResults(List<?> res) {
        if (res == null) {
            log.error("NetSuite SOAP API returned null for subsidiary search. This indicates API failure, timeout, or connection issue.");
            throw new RetriableException("netsuite_soap_null_response",
                    "NetSuite SOAP API returned null response",
                    "The API may be temporarily unavailable. Please try again later.");
        }
        if (res.isEmpty()) {
            log.info("NetSuite SOAP API returned empty subsidiary list - account has no subsidiaries");
            return Map.of();
        }
        return res.stream()
                .map(e -> {
                    Subsidiary subsidiary = (Subsidiary) e;
                    String id = subsidiary.getInternalId();
                    String fiscalCalendar = subsidiary.getFiscalCalendar() != null
                            ? subsidiary.getFiscalCalendar().getName()
                            : null;
                    Map<String, Object> fiscalCalendarMap = new HashMap<>();
                    fiscalCalendarMap.put("fiscalCalendar", fiscalCalendar);
                    return Map.entry(id, fiscalCalendarMap);
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public void updateContactWithAddressDetails(List<EntityData> result, SearchResults results) {
        Map<String, Contact> map = groupContactsByInternalId(results.getRecords());
        for(EntityData entityData: result) {
            if(map.containsKey(entityData.getId())) {
                Contact contact = map.get(entityData.getId());
                Map<String, String> address = extractAddressValues(contact);
                address.forEach((k, v) -> entityData.addValue(k, v));
            }
        }
    }

    private Map<String, String> extractAddressValues(Contact contact) {
        Map<String, String> addressValues = new HashMap<>();
        ContactAddressbookList contactAddressbookList =  contact.getAddressbookList();
        if(contactAddressbookList != null) {
            ContactAddressbook[] contactAddressbooks = contactAddressbookList.getAddressbook();
            if(contactAddressbooks != null && contactAddressbooks.length > 0) {
                ContactAddressbook contactAddressbook = contactAddressbooks[0];
                Address address = contactAddressbook.getAddressbookAddress();
                if(address != null) {
                    addressValues.put("addr1", address.getAddr1());
                    addressValues.put("addr2", address.getAddr2());
                    addressValues.put("addr3", address.getAddr3());
                    addressValues.put("city", address.getCity());
                    addressValues.put("state", address.getState());
                    addressValues.put("zip", address.getZip());
                    Country country = address.getCountry();
                    String countryString = country != null ? country.getValue() : "";
                    addressValues.put("country", countryString);
                }
            }
        }
        return addressValues;
    }

    public static Map<String, Contact> groupContactsByInternalId(List<Record> contacts) {
        Map<String, Contact> groupedRecords = new HashMap<>();

        for (Record contact : contacts) {
            String internalId = ((Contact)contact).getInternalId();
            groupedRecords.computeIfAbsent(internalId, k -> (Contact)contact);
        }

        return groupedRecords;
    }

    public static Map<String, Customer> groupCustomersByInternalId(List<Record> customers) {
        Map<String, Customer> groupedRecords = new HashMap<>();

        for (Record customer : customers) {
            String internalId = ((Customer)customer).getInternalId();
            groupedRecords.computeIfAbsent(internalId, k -> (Customer)customer);
        }

        return groupedRecords;
    }

    public void updateCustomerWithAltName(List<EntityData> result, SearchResults results) {
        Map<String, Customer> map = groupCustomersByInternalId(results.getRecords());
        for(EntityData entityData: result) {
            if(map.containsKey(entityData.getId())) {
                Customer customer = map.get(entityData.getId());
                entityData.addValue("altName", customer.getAltName());
            }
        }
    }

    private static Map<String, RecordType> RECORD_TYPES = new HashMap<>();

    static {
        Arrays.stream(RecordType.class.getDeclaredFields()).forEach(f -> {
            try {
                if (f.getType().equals(RecordType.class)) {
                    RECORD_TYPES.put(f.getName().toLowerCase(), (RecordType) f.get(RecordType.class));
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        });

    }

    @Getter
    @AllArgsConstructor
    public static class SelectValues {
        int totalRecords;
        int totalPages;
        int currentPage;
        List<EntityData> recordsInCurrentPage = new ArrayList<>();

        public SelectValues() {
        }
    }

    /**
     * @param client
     * @param entityName
     * @param apiName
     * @param pageNumber starts at 1
     * @return
     */
    public SelectValues getSelectValues(WsClient client, String entityName, String apiName, int pageNumber, long lastModified) {
        try {
            GetSelectValueFieldDescription getSelectValueFieldDescription = getSelectValueFieldDescription(client, entityName, apiName);
            GetSelectValueResult getSelectValueResult = client.callGetSelectValue(getSelectValueFieldDescription, pageNumber);
            final Status status = getSelectValueResult.getStatus();

            if (status.isIsSuccess()) {
                //handle pagination correctly. The SOAP API doesnt care about pagenumber and will return the same values all the time
                //if no pagination was required for this picklist
                if (getSelectValueResult.getTotalPages() < pageNumber) {
                    return new SelectValues();
                }
                final BaseRefList baseRefList = getSelectValueResult.getBaseRefList();
                if (baseRefList == null || baseRefList.getBaseRef() == null) {
                    return new SelectValues();
                }
                return new SelectValues(
                        getSelectValueResult.getTotalRecords(),
                        getSelectValueResult.getTotalPages(),
                        pageNumber,
                        Arrays.stream(baseRefList.getBaseRef()).map(r -> toEntityData(r, lastModified, entityName, apiName)).collect(Collectors.toList())
                );
            }
            throw new NonRetriableException(ErrorCodes.API_ERROR, toReadableString(status), "API_ERROR");
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    private String toReadableString(Status status) {
        StringBuilder builder = new StringBuilder();
        Arrays.stream(status.getStatusDetail()).forEach(detail -> {
            builder.append(" StatusType:")
                    .append(detail.getType())
                    .append(", StatusCode:")
                    .append(detail.getCode())
                    .append(", Message:")
                    .append(detail.getMessage())
                    .append("\n");
        });
        return builder.toString();
    }

    private EntityData toEntityData(BaseRef selectValue, long lastModified, String entityName, String apiName) {
        final EntityData value = new EntityData(NetsuiteSeed.PICKLIST_VALUES_ENTITY);
        try {
            final String internalId = Objects.toString(selectValue.getClass().getMethod("getInternalId").invoke(selectValue), null);
            final String externalId = Objects.toString(selectValue.getClass().getMethod("getExternalId").invoke(selectValue), null);
            final String name = selectValue.getName();
            final String selectValueId = format("%s_%s_%s", entityName, apiName, internalId);
            value.setId(selectValueId)
                    .setLastModified(lastModified)
                    .setCreatedAt(lastModified)
                    .addValue("id", selectValueId)
                    .addValue("internalId", internalId)
                    .addValue("externalId", externalId)
                    .addValue("name", name)
                    .addValue("entityName", entityName)
                    .addValue("fieldName", apiName)
                    .addValue("lastModified", lastModified);
            return value;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private GetSelectValueFieldDescription getSelectValueFieldDescription(WsClient client, String entityName, String apiName) throws RemoteException {
        RecordType recordType = RECORD_TYPES.get(entityName.toLowerCase());
        if (recordType != null) {
            return new GetSelectValueFieldDescription(recordType, null, null, null, apiName, null, null, null);
        } else {
            final GetSelectValueFieldDescription customRef = getSelectValueFDForCustomType(client, entityName, apiName);
            if (customRef != null) return customRef;
            final GetSelectValueFieldDescription customTxn = getSelectValueFDForCustomTxn(client, entityName, apiName);
            if (customTxn != null) return customTxn;
        }
        throw new NonRetriableException(ErrorCodes.BAD_REQUEST, "Could not find record type for " + entityName, "BAD_REQUEST");
    }

    private static GetSelectValueFieldDescription getSelectValueFDForCustomTxn(WsClient client, String entityName, String apiName) throws RemoteException {
        final List<CustomizationRef> customTransactions = client.getCustomizationId(GetCustomizationType.customTransactionType, false);
        for (CustomizationRef customRef : customTransactions) {
            if (customRef.getName().equalsIgnoreCase(entityName)) {
                return new GetSelectValueFieldDescription(null, null, customRef, null, apiName, null, null, null);
            }
        }
        return null;
    }

    private static GetSelectValueFieldDescription getSelectValueFDForCustomType(WsClient client, String entityName, String apiName) throws RemoteException {
        final List<CustomizationRef> customRecords = client.getCustomizationId(GetCustomizationType.customRecordType, false);
        for (CustomizationRef customRef : customRecords) {
            if (customRef.getName().equalsIgnoreCase(entityName)) {
                return new GetSelectValueFieldDescription(null, customRef, null, null, apiName, null, null, null);
            }
        }
        return null;
    }

    @FunctionalInterface
    interface RemoteExceptionThrowingSupplier<T> {
        T get() throws RemoteException;
    }

    private <T> T withErrorHandling(RemoteExceptionThrowingSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (InvalidCredentialsFault | InsufficientPermissionFault | InvalidAccountFault | InvalidSessionFault
                | InvalidVersionFault | UnexpectedErrorFault e) {
            log.error(e.getFaultReason(), e);
            throw new NonRetriableException(e.getFaultCode().getLocalPart(), e.getFaultReason(), e.getFaultCode().getLocalPart());
        } catch (ExceededConcurrentRequestLimitFault | ExceededRecordCountFault | ExceededRequestLimitFault | ExceededUsageLimitFault e) {
            log.error(e.getFaultReason(), e);
            throw new RetriableException(e.getFaultCode().getLocalPart(), e.getFaultReason(), e.getFaultCode().getLocalPart());
        } catch (AxisFault e) {
            log.error(e.getFaultReason(), e);
            Throwable cause = e.getCause();
            if (cause instanceof SocketTimeoutException) {
                throw new RetriableException(e.getFaultCode().getLocalPart(), e.getFaultReason(), e.getFaultCode().getLocalPart());
            } else {
                throw new NonRetriableException(e.getFaultCode().getLocalPart(), e.getFaultReason(), e.getFaultCode().getLocalPart());
            }
        } catch (RemoteException e) {
            log.error(e.getMessage(), e);
            throw new NonRetriableException("unknown_error", e.getMessage(), "unknown_error");
        }
    }

    public TestConnectionResponse testConnection(ConnectorInfo config, List<String> entityNames) {
        TestConnectionResponse response = new TestConnectionResponse();
        try {
            WsClient client = getClient(config);
            withErrorHandling(() -> client.getAllRecords(GetAllRecordType.currency));
            log.info(format("Successfully authenticated NetSuite SOAP connection for %s", config.getName()));
            return response;
        } catch (NonRetriableException | RetriableException e) {
            response = new TestConnectionResponse(e.getMessage(), e.getErrorCode(),
                    Arrays.asList(e.getMessage()));
            log.error("NetSuite authentication error with message: " + e.getMessage(), e);
            return response;
        }
    }

    public static String getInternalId(Record record){
        try {
            Method getInternalId = record.getClass().getMethod("getInternalId");
            Object internalId = getInternalId.invoke(record);
            return internalId==null? null : internalId.toString();
        }catch (Exception e){
            log.error(e.getMessage(),e);
            throw new NonRetriableException("unknown_error",e.getMessage(),"unknown_error");
        }

    }

    private List<String> getFileReferences(String entityName, WsClient client, SearchRecord search, int pageNumber){
        SearchResult searchResult =withErrorHandling(() ->
                pageNumber==0 ? client.callSearch(search) : client.callSearchMoreWithId(pageNumber+1));
        List<String> fileIds = new ArrayList<>();
        if(searchResult.getStatus().isIsSuccess()) {
            if(searchResult.getSearchRowList() != null) {
                SearchRow[] rows = searchResult.getSearchRowList().getSearchRow();
                for (int i = 0; i < rows.length; i++) {
                    switch (entityName) {
                        case "opportunity":
                            OpportunitySearchRow row = (OpportunitySearchRow) rows[i];
                            if (row.getFileJoin() != null) {
                                fileIds.add(row.getFileJoin().getInternalId(0).getSearchValue().getInternalId());
                            }
                            break;
                        default:
                            TransactionSearchRow txnRow = (TransactionSearchRow) rows[i];
                            if (txnRow.getFileJoin() != null) {
                                fileIds.add(txnRow.getFileJoin().getInternalId(0).getSearchValue().getInternalId());
                            }
                    }
                }
            }
        }else{
            throw new NonRetriableException("ns_search_failed",searchResult.getStatus().getStatusDetail(0).getMessage(),
                searchResult.getStatus().getStatusDetail(0).getCode().getValue());
        }
        return fileIds;
    }


    private SearchResults search(WsClient client, SearchRecord search, int pageNumber){
        log.debug("Starting search with request {}", search);
        SearchResult searchResult =withErrorHandling(() ->
                pageNumber==0 ? client.callSearch(search) : client.callSearchMoreWithId(pageNumber+1));
        log.debug("Search finished with status {}", searchResult.getStatus().isIsSuccess());
        List<String> internalIds = new ArrayList<>();
        if(searchResult.getStatus().isIsSuccess()) {
            List<Record> recordsList = new ArrayList<>();
            if(searchResult.getRecordList() != null && searchResult.getRecordList().getRecord()!=null) {
                for (Record record : searchResult.getRecordList().getRecord()) {
                    internalIds.add(getInternalId(record));
                }
                Record[] records = searchResult.getRecordList().getRecord();
                recordsList = records == null ? List.of() : Arrays.asList(records);
            }
            if(internalIds.isEmpty() && searchResult.getSearchRowList() != null && searchResult.getSearchRowList().getSearchRow() != null) {
                for (SearchRow searchRow : searchResult.getSearchRowList().getSearchRow()) {
                    if(searchRow instanceof TransactionSearchRow) {
                        TransactionSearchRowBasic transactionSearchRowBasic = ((TransactionSearchRow) searchRow).getBasic();
                        var internalId = transactionSearchRowBasic.getInternalId();
                        for(SearchColumnSelectField internalIdField: internalId) {
                            if(internalIdField.getSearchValue() != null && internalIdField.getSearchValue().getInternalId() != null) {
                                internalIds.add(internalIdField.getSearchValue().getInternalId());
                            }
                        }
                    }
                }
            }
            return new SearchResults(internalIds, pageNumber, searchResult.getTotalRecords(), recordsList);
        }else{
            for(StatusDetail statusDetail: searchResult.getStatus().getStatusDetail()) {
                if(statusDetail.getMessage().equalsIgnoreCase("Invalid search page index.")) {
                    int currentPage = searchResult.getPageIndex();
                    int totalPages = searchResult.getTotalPages();
                    if(currentPage > totalPages) {
                        // We have exhausted the pages
                        return new SearchResults(List.of(), pageNumber, searchResult.getTotalRecords(), List.of());
                    }
                }
            }
            throw new NonRetriableException("ns_search_failed",searchResult.getStatus().getStatusDetail(0).getMessage(),
                    searchResult.getStatus().getStatusDetail(0).getCode().getValue()
                    );
        }
    }
    public List<AttributeSchema> getCustomizations(ConnectorInfo connector, String entityName){

        WsClient client = getClient(connector);
        client.setSearchPreferences(false,0,false);

        withErrorHandling(()-> {
            Map<String, List<AttributeSchema>> entityToFieldsMap = new HashMap<>();
            List<CustomizationRef> recordRefs = new ArrayList<>();
            List<Record> customRecords = getCustomRecords(client);
            Map<String, CustomRecordType> recordMap = customRecords.stream().filter(c->c!=null).collect(Collectors.toMap(l -> CustomRecordType.class.cast(l).getInternalId(), l -> CustomRecordType.class.cast(l)));

            //client.getCustomizationId(GetCustomizationType.customTransactionType, false);
            List<Record> customLists = getCustomLists(client);
            Map<String, CustomList> listMap = customLists.stream().collect(Collectors.toMap(l -> CustomList.class.cast(l).getInternalId(), l -> CustomList.class.cast(l)));
            addTransactionBodyFields(client, entityToFieldsMap,recordMap,listMap);
            addTransactionColumnFields(client, entityToFieldsMap,recordMap,listMap);
            addEntityFields(client, entityToFieldsMap,recordMap,listMap);
            addCrmFields(client, entityToFieldsMap,recordMap,listMap);
            return entityToFieldsMap.get(entityName);
        });
        return List.of();
    }

    public List<Record> getCustomRecords(WsClient client) {
        try {
            return client.getRecords(client.getCustomizationId(GetCustomizationType.customRecordType, false));
        }catch (Exception e){
            log.error(e.getMessage(),e);
            return List.of();
        }
    }

    public List<Record> getCustomLists(WsClient client) {
        try {
            return client.getRecords(client.getCustomizationId(GetCustomizationType.customList, false));
        }catch (Exception e){
            log.error(e.getMessage(),e);
            return List.of();
        }
    }

    private void addTransactionBodyFields(WsClient client, Map<String, List<AttributeSchema>> entityToFieldsMap, Map<String, CustomRecordType> recordMap, Map<String, CustomList> listMap) throws RemoteException {
        try {
            List<Record> customTxnBodyFields = client.getRecords(client.getCustomizationId(GetCustomizationType.transactionBodyCustomField, false));
            customTxnBodyFields.forEach(f -> {
                TransactionBodyCustomField txnBodyField = TransactionBodyCustomField.class.cast(f);
                AttributeSchema attr = new AttributeSchema();
                attr.setApiName(txnBodyField.getScriptId());
                attr.setDisplayName(txnBodyField.getLabel());
                attr.setDefaultValue(txnBodyField.getDefaultValue());
                attr.setNillable(!txnBodyField.getIsMandatory());
                attr.setDataType(toSyncariDataType(txnBodyField.getFieldType()));
                if (txnBodyField.getSelectRecordType() != null) {
                    String internalId = txnBodyField.getSelectRecordType().getInternalId();
                    if (STANDARD_OBJECTS.containsKey(internalId)) {
                        attr.setDataType("reference");
                        attr.setReferenceTo(STANDARD_OBJECTS.get(internalId));
                        attr.setReferenceTargetField("internalId");
                    } else if (listMap.containsKey(internalId)) {
                        attr.setDataType("picklist");
                    } else if (recordMap.containsKey(internalId)) {
                        attr.setDataType("reference");
                        attr.setReferenceTo(recordMap.get(internalId).getScriptId());
                        attr.setReferenceTargetField("internalId");
                    } else {
                        //skip this attr
                        attr.setDataType(null);
                    }
                }
                //TODO: Complete this list
                if (txnBodyField.getBodyOpportunity()) {
                    updateFields("opportunity", entityToFieldsMap, attr);
                }
                if (txnBodyField.getBodySale()) {
                    updateFields("sale", entityToFieldsMap, attr);
                }
                if (txnBodyField.getBodyJournal()) {
                    updateFields("journal", entityToFieldsMap, attr);
                }
                if (txnBodyField.getBodyPurchase()) {
                    updateFields("purchase", entityToFieldsMap, attr);
                }
            });
        }catch (Exception e){
            log.error(e.getMessage(),e);
        }
    }
    private String toSyncariDataType(CustomizationFieldType type){
        if(type == CustomizationFieldType._checkBox){
            return "boolean";
        }else if(type == CustomizationFieldType._currency){
            return "double";
        }else if(type == CustomizationFieldType._date){
            return "date";
        }else if(type == CustomizationFieldType._datetime){
            return "datetime";
        }else if(type == CustomizationFieldType._decimalNumber){
            return "double";
        }else if(Set.of(_freeFormText,_eMailAddress,_document,_textArea,_longText,_help,_hyperlink,_password, _phoneNumber).contains(type)){
            return "string";
        }else if(type == CustomizationFieldType._integerNumber) {
            return "integer";
        }else if(Set.of(_listRecord,_multipleSelect).contains(type)){
            return "reference";
        }else{
            return "string";
        }

    }
    private void addEntityFields(WsClient client, Map<String, List<AttributeSchema>> entityToFieldsMap, Map<String, CustomRecordType> recordMap, Map<String, CustomList> listMap) throws RemoteException {
        try {
            List<Record> customTxnBodyFields = client.getRecords(client.getCustomizationId(GetCustomizationType.entityCustomField, false));

            customTxnBodyFields.forEach(f -> {
                if (f != null) {
                    AttributeSchema attr = new AttributeSchema();
                    EntityCustomField entityField = EntityCustomField.class.cast(f);
                    attr.setApiName(entityField.getScriptId());
                    attr.setDisplayName(entityField.getLabel());
                    attr.setDefaultValue(entityField.getDefaultValue());
                    attr.setNillable(!entityField.getIsMandatory());
                    attr.setDataType(toSyncariDataType(entityField.getFieldType()));
                    if (entityField.getSelectRecordType() != null) {
                        String internalId = entityField.getSelectRecordType().getInternalId();
                        if (STANDARD_OBJECTS.containsKey(internalId)) {
                            attr.setDataType("reference");
                            attr.setReferenceTo(STANDARD_OBJECTS.get(internalId));
                            attr.setReferenceTargetField("internalId");
                        } else if (listMap.containsKey(internalId)) {
                            attr.setDataType("picklist");
                        } else if (recordMap.containsKey(internalId)) {
                            attr.setDataType("reference");
                            attr.setReferenceTo(recordMap.get(internalId).getScriptId());
                            attr.setReferenceTargetField("internalId");
                        } else {
                            //skip this attr
                            attr.setDataType(null);
                        }
                    }
                    //TODO: Complete this list
                    if (entityField.getAppliesToContact()) {
                        updateFields("contact", entityToFieldsMap, attr);
                    }
                    if (entityField.getAppliesToCustomer()) {
                        updateFields("customer", entityToFieldsMap, attr);
                    }
                    if (entityField.getAppliesToEmployee()) {
                        updateFields("employee", entityToFieldsMap, attr);
                    }
                    if (entityField.getAppliesToPartner()) {
                        updateFields("partner", entityToFieldsMap, attr);
                    }
                    if (entityField.getAppliesToVendor()) {
                        updateFields("vendor", entityToFieldsMap, attr);
                    }
                    if (entityField.getAppliesToPriceList()) {
                        updateFields("priceList", entityToFieldsMap, attr);
                    }
                }
            });
        }catch (Exception e){
            log.error(e.getMessage(),e);
        }
    }
    private void addCrmFields(WsClient client, Map<String, List<AttributeSchema>> entityToFieldsMap,Map<String, CustomRecordType> recordMap, Map<String, CustomList> listMap) throws RemoteException {
        try {
            List<Record> customTxnBodyFields = client.getRecords(client.getCustomizationId(GetCustomizationType.crmCustomField, false));
            customTxnBodyFields.forEach(f -> {
                if (f != null) {
                    CrmCustomField entityField = CrmCustomField.class.cast(f);
                    AttributeSchema attr = new AttributeSchema();
                    attr.setApiName(entityField.getScriptId());
                    attr.setDisplayName(entityField.getLabel());
                    attr.setDefaultValue(entityField.getDefaultValue());
                    attr.setNillable(!entityField.getIsMandatory());
                    attr.setDataType(toSyncariDataType(entityField.getFieldType()));
                    if (entityField.getSelectRecordType() != null) {
                        String internalId = entityField.getSelectRecordType().getInternalId();
                        if (STANDARD_OBJECTS.containsKey(internalId)) {
                            attr.setDataType("reference");
                            attr.setReferenceTo(STANDARD_OBJECTS.get(internalId));
                            attr.setReferenceTargetField("internalId");
                        } else if (listMap.containsKey(internalId)) {
                            attr.setDataType("picklist");
                        } else if (recordMap.containsKey(internalId)) {
                            attr.setDataType("reference");
                            attr.setReferenceTo(recordMap.get(internalId).getScriptId());
                            attr.setReferenceTargetField("internalId");
                        } else {
                            //skip this attr
                            attr.setDataType(null);
                        }
                    }
                    //TODO: Complete this list
                    if (entityField.getAppliesToCampaign()) {
                        updateFields("campaign", entityToFieldsMap, attr);
                    }
                    if (entityField.getAppliesToCase()) {
                        updateFields("case", entityToFieldsMap, attr);
                    }
                    if (entityField.getAppliesToEvent()) {
                        updateFields("event", entityToFieldsMap, attr);
                    }
                    if (entityField.getAppliesToTask()) {
                        updateFields("task", entityToFieldsMap, attr);
                    }
                    if (entityField.getAppliesToIssue()) {
                        updateFields("issue", entityToFieldsMap, attr);
                    }
                }
            });
        }catch (Exception e){
            log.error(e.getMessage(),e);
        }
    }
    private void addTransactionColumnFields(WsClient client, Map<String, List<AttributeSchema>> entityToFieldsMap, Map<String, CustomRecordType> recordMap, Map<String, CustomList> listMap) throws RemoteException {
        try {
            List<Record> customTxnColFields = client.getRecords(client.getCustomizationId(GetCustomizationType.transactionColumnCustomField, false));
            customTxnColFields.forEach(f ->{
                if(f!=null) {
                    TransactionColumnCustomField txnColField = TransactionColumnCustomField.class.cast(f);
                    AttributeSchema attr = new AttributeSchema();
                    attr.setApiName(txnColField.getScriptId());
                    attr.setDisplayName(txnColField.getLabel());
                    attr.setDefaultValue(txnColField.getDefaultValue());
                    attr.setNillable(!txnColField.getIsMandatory());
                    attr.setDataType(toSyncariDataType(txnColField.getFieldType()));
                    if (txnColField.getSelectRecordType() != null) {
                        String internalId = txnColField.getSelectRecordType().getInternalId();
                        if (STANDARD_OBJECTS.containsKey(internalId)) {
                            attr.setDataType("reference");
                            attr.setReferenceTo(STANDARD_OBJECTS.get(internalId));
                            attr.setReferenceTargetField("internalId");
                        } else if (listMap.containsKey(internalId)) {
                            attr.setDataType("picklist");
                        } else if (recordMap.containsKey(internalId)) {
                            attr.setDataType("reference");
                            attr.setReferenceTo(recordMap.get(internalId).getScriptId());
                            attr.setReferenceTargetField("internalId");
                        } else {
                            //skip this attr
                            attr.setDataType(null);
                        }
                    }
                    //TODO: Complete this list
                    if (txnColField.getColOpportunity()) {
                        updateFields("opportunity", entityToFieldsMap, attr);
                    }
                    if (txnColField.getColSale()) {
                        updateFields("sale", entityToFieldsMap, attr);
                    }
                    if (txnColField.getColJournal()) {
                        updateFields("journal", entityToFieldsMap, attr);
                    }
                    if (txnColField.getColPurchase()) {
                        updateFields("purchase", entityToFieldsMap, attr);
                    }
                }
            });
        }catch(Exception e){
            log.error(e.getMessage(),e);
        }

    }

    public void attachContactToOppty(WsClient client,String contactId, String opptyId){
        try {
            boolean results = client.attachContact(new RecordRef("opportunity", opptyId, null, RecordType.opportunity)
                    , new RecordRef("contact", contactId, null, RecordType.contact));
            log.info("Attached contact {} to oppty {} : {}", contactId,opptyId,results);
        } catch (RemoteException e) {
            log.error(e.getMessage(),e);
            throw new NonRetriableException(ErrorCodes.UNKNOWN_ERROR, e.getMessage(),ErrorCodes.UNKNOWN_ERROR.toString());
        }
    }
    private void updateFields(String entityName,Map<String, List<AttributeSchema>> entityToFieldsMap, AttributeSchema attributeSchema) {
        List<AttributeSchema> customFields = entityToFieldsMap.getOrDefault(entityName,new ArrayList<>());
        customFields.add(attributeSchema);
        entityToFieldsMap.put(entityName,customFields);
    }

    public SearchResults listByIds(WsClient wsClient, SyncRequest request) {
        String entityName= request.getEntityName();
        List<EntityData> records = request.getData().getOrDefault(request.getConnector().getId(),List.of());
        int pageNumber =0;
        if (ITEM_OBJECTS.contains(entityName)) {
            return listByItemIds(entityName, records, pageNumber, wsClient, request);
        }

        EntitySchema entitySchema = request.getEntitySchema();

        if (entitySchema.isCustom()){
            CustomRecordSearchBasic search = getCustomRecordSearchBasic(entityName);

            Optional<SearchMultiSelectField> searchField = getSearchField(records, entityName, RecordType.customRecordType);
            return searchField.map(s->{
                search.setInternalId(s);
                return search(wsClient, search, pageNumber);
            }).orElse(SearchResults.emptyResults());
        }
        switch (entityName) {
            case "opportunity": {
                Optional<SearchMultiSelectField> searchField = getSearchField(records, entityName, RecordType.opportunity);
                return searchField.map(s->{
                    OpportunitySearchBasic opportunitySearchBasic = new OpportunitySearchBasic();
                    opportunitySearchBasic.setInternalId(s);
                    SearchResults oppties = search(wsClient, opportunitySearchBasic, pageNumber);
                    updateOpptyContacts(request, oppties);
                    return oppties;
                }).orElse(SearchResults.emptyResults());
            }
            case "subsidiary": {
                Optional<SearchMultiSelectField> searchField = getSearchField(records, entityName, RecordType.subsidiary);
                return searchField.map(s->{
                    SubsidiarySearchBasic subsidiarySearchBasic = new SubsidiarySearchBasic();
                    subsidiarySearchBasic.setInternalId(s);
                    SearchResults oppties = search(wsClient, subsidiarySearchBasic, pageNumber);
                    return oppties;
                }).orElse(SearchResults.emptyResults());
            }
            case "customer": {
                wsClient.setSearchPreferences(false, request.getPageSize(), true);
                Optional<SearchMultiSelectField> searchField = getSearchField(records, entityName, RecordType.customer);
                CustomerSearchBasic search = new CustomerSearchBasic();
                return searchField.map(s->{
                    search.setInternalId(s);
                    return search(wsClient,search,pageNumber);
                }).orElse(SearchResults.emptyResults());
            }
            case "contact": {
                Optional<SearchMultiSelectField> searchField = getSearchField(records, entityName, RecordType.contact);
                wsClient.setSearchPreferences(false, request.getPageSize(), true);
                return searchField.map(s->{
                    ContactSearchBasic search = new ContactSearchBasic();
                    search.setInternalId(s);
                    return search(wsClient,search,pageNumber);
                }).orElse(SearchResults.emptyResults());

            }
            case "task": {
                Optional<SearchMultiSelectField> searchField = getSearchField(records, entityName, RecordType.task);
                wsClient.setSearchPreferences(false, request.getPageSize(), true);
                return searchField.map(s->{
                    TaskSearchBasic search = new TaskSearchBasic();
                    search.setInternalId(s);
                    return search(wsClient,search,pageNumber);
                }).orElse(SearchResults.emptyResults());

            }
            case "journalEntry": {
                Optional<SearchMultiSelectField> searchField = getSearchField(records, entityName, RecordType.journalEntry);
                return searchField.map(s-> {
                    TransactionSearchBasic search = new TransactionSearchBasic();
                    search.setInternalId(s);
                    SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
                    transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
                    transactionLineType.setSearchValue(new String[]{TransactionType.__journal});
                    search.setType(transactionLineType);
                    return search(wsClient, search, pageNumber);
                }).orElse(SearchResults.emptyResults());
            }
            case "employee": {


                Optional<SearchMultiSelectField> searchField = getSearchField(records, entityName, RecordType.employee);
                return searchField.map(s-> {
                    EmployeeSearchBasic search = new EmployeeSearchBasic();
                    search.setInternalId(s);
                    return search(wsClient,search, pageNumber);
                }).orElse(SearchResults.emptyResults());

            }
            case "vendor": {
                Optional<SearchMultiSelectField> searchField = getSearchField(records, entityName, RecordType.vendor);
                return searchField.map(s -> {
                    VendorSearchBasic search = new VendorSearchBasic();
                    search.setInternalId(s);
                    return search(wsClient, search, pageNumber);
                }).orElse(SearchResults.emptyResults());
            }
            case "partner": {
                Optional<SearchMultiSelectField> searchField = getSearchField(records, entityName, RecordType.partner);
                return searchField.map(s -> {
                    PartnerSearchBasic search = new PartnerSearchBasic();
                    search.setInternalId(s);
                    return search(wsClient, search, pageNumber);
                }).orElse(SearchResults.emptyResults());
            }
            case "supportcase": {
                Optional<SearchMultiSelectField> searchField = getSearchField(records, entityName, RecordType.supportCase);
                return searchField.map(s -> {
                    SupportCaseSearchBasic search = new SupportCaseSearchBasic();
                    search.setInternalId(s);
                    return search(wsClient, search, pageNumber);
                }).orElse(SearchResults.emptyResults());
            }
            case "salesorder": {
                Optional<SearchMultiSelectField> searchField = getSearchField(records, entityName, RecordType.salesOrder);
                return searchField.map(s -> {
                    TransactionSearchBasic search = new TransactionSearchBasic();
                    search.setInternalId(s);
                    SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
                    transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
                    transactionLineType.setSearchValue(new String[]{TransactionType.__salesOrder});
                    search.setType(transactionLineType);
                    wsClient.setSearchPreferences(false, 0, false);
                    return search(wsClient, search, pageNumber);
                }).orElse(SearchResults.emptyResults());
            }
            case "purchaseorder": {
                Optional<SearchMultiSelectField> searchField = getSearchField(records, entityName, RecordType.purchaseOrder);
                return searchField.map(s-> {
                    TransactionSearchBasic search = new TransactionSearchBasic();
                    search.setInternalId(s);
                    SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
                    transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
                    transactionLineType.setSearchValue(new String[]{TransactionType.__purchaseOrder});
                    search.setType(transactionLineType);
                    return search(wsClient, search, pageNumber);
                }).orElse(SearchResults.emptyResults());
            }
            case "cashsale": {
                Optional<SearchMultiSelectField> searchField = getSearchField(records, entityName, RecordType.cashSale);
                return searchField.map(s-> {
                    TransactionSearchBasic search = new TransactionSearchBasic();
                    search.setInternalId(s);
                    SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
                    transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
                    transactionLineType.setSearchValue(new String[]{TransactionType.__cashSale});
                    search.setType(transactionLineType);
                    return search(wsClient, search, pageNumber);
                }).orElse(SearchResults.emptyResults());
            }
            case "cashrefund": {
                Optional<SearchMultiSelectField> searchField = getSearchField(records, entityName, RecordType.cashRefund);
                return searchField.map(s-> {
                    TransactionSearchBasic search = new TransactionSearchBasic();
                    search.setInternalId(s);
                    SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
                    transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
                    transactionLineType.setSearchValue(new String[]{TransactionType.__cashRefund});
                    search.setType(transactionLineType);
                    return search(wsClient, search, pageNumber);
                }).orElse(SearchResults.emptyResults());
            }
            case "creditmemo": {
                Optional<SearchMultiSelectField> searchField = getSearchField(records, entityName, RecordType.creditMemo);
                return searchField.map(s-> {
                    TransactionSearchBasic search = new TransactionSearchBasic();
                    search.setInternalId(s);
                    SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
                    transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
                    transactionLineType.setSearchValue(new String[]{TransactionType.__creditMemo});
                    search.setType(transactionLineType);
                    return search(wsClient, search, pageNumber);
                }).orElse(SearchResults.emptyResults());
            }
            case "customerrefund": {
                Optional<SearchMultiSelectField> searchField = getSearchField(records, entityName, RecordType.customerRefund);
                return searchField.map(s-> {
                    TransactionSearchBasic search = new TransactionSearchBasic();
                    search.setInternalId(s);
                    SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
                    transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
                    transactionLineType.setSearchValue(new String[]{TransactionType.__customerRefund});
                    search.setType(transactionLineType);
                    return search(wsClient, search, pageNumber);
                }).orElse(SearchResults.emptyResults());
            }
            case "customerdeposit": {
                Optional<SearchMultiSelectField> searchField = getSearchField(records, entityName, RecordType.customerDeposit);
                return searchField.map(s-> {
                    TransactionSearchBasic search = new TransactionSearchBasic();
                    search.setInternalId(s);
                    SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
                    transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
                    transactionLineType.setSearchValue(new String[]{TransactionType.__customerDeposit});
                    search.setType(transactionLineType);
                    return search(wsClient, search, pageNumber);
                }).orElse(SearchResults.emptyResults());
            }
            case "estimate": {
                Optional<SearchMultiSelectField> searchField = getSearchField(records, entityName, RecordType.estimate);
                return searchField.map(s-> {
                    TransactionSearchBasic search = new TransactionSearchBasic();
                    search.setInternalId(s);
                    SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
                    transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
                    transactionLineType.setSearchValue(new String[]{TransactionType.__estimate});
                    search.setType(transactionLineType);
                    wsClient.setSearchPreferences(false,0,false);
                    return search(wsClient, search, pageNumber);
                }).orElse(SearchResults.emptyResults());
            }
            case "noninventorysaleitem": {
                Optional<SearchMultiSelectField> searchField = getSearchField(records, entityName, RecordType.nonInventorySaleItem);
                return searchField.map(s-> {
                    ItemSearchBasic search = new ItemSearchBasic();
                    search.setInternalId(s);
                    return search(wsClient,search, pageNumber);
                }).orElse(SearchResults.emptyResults());
            }
            case "invoice": {
                Optional<SearchMultiSelectField> searchField = getSearchField(records, entityName, RecordType.invoice);
                return searchField.map(s-> {
                    TransactionSearchBasic search = new TransactionSearchBasic();
                    search.setInternalId(s);
                    SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
                    transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
                    transactionLineType.setSearchValue(new String[]{TransactionType.__invoice});
                    search.setType(transactionLineType);
                    wsClient.setSearchPreferences(false,0,false);
                    return search(wsClient, search, pageNumber);
                }).orElse(SearchResults.emptyResults());
            }
            case "customerpayment": {
                Optional<SearchMultiSelectField> searchField = getSearchField(records, entityName, RecordType.customerPayment);
                return searchField.map(s-> {
                    TransactionSearchBasic search = new TransactionSearchBasic();
                    search.setInternalId(s);
                    SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
                    transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
                    transactionLineType.setSearchValue(new String[]{TransactionType.__invoice});
                    search.setType(transactionLineType);
                    return search(wsClient, search, pageNumber);
                }).orElse(SearchResults.emptyResults());
            }
            case "classification": {
                Optional<SearchMultiSelectField> searchField = getSearchField(records, entityName, RecordType.classification);
                return searchField.map(s-> {
                    ClassificationSearchBasic classSearch = new ClassificationSearchBasic();
                    classSearch.setInternalId(s);
                    return search(wsClient,classSearch,pageNumber);
                }).orElse(SearchResults.emptyResults());
            }
            case "department": {
                Optional<SearchMultiSelectField> searchField = getSearchField(records, entityName, RecordType.department);
                return searchField.map(s-> {
                    DepartmentSearchBasic classSearch = new DepartmentSearchBasic();
                    classSearch.setInternalId(s);
                    return search(wsClient,classSearch,pageNumber);
                }).orElse(SearchResults.emptyResults());
            }
            case "assemblybuild": {
              Optional<SearchMultiSelectField> searchField = getSearchField(records, entityName, RecordType.assemblyBuild);
              return searchField.map(s-> {
                  TransactionSearchBasic search = new TransactionSearchBasic();
                  search.setInternalId(s);
                  SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
                  transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
                  transactionLineType.setSearchValue(new String[]{TransactionType.__assemblyBuild});
                  search.setType(transactionLineType);
                  return search(wsClient, search, pageNumber);
              }).orElse(SearchResults.emptyResults());
            }
            case "assemblyunbuild": {
              Optional<SearchMultiSelectField> searchField = getSearchField(records, entityName, RecordType.assemblyUnbuild);
              return searchField.map(s-> {
                  TransactionSearchBasic search = new TransactionSearchBasic();
                  search.setInternalId(s);
                  SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
                  transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
                  transactionLineType.setSearchValue(new String[]{TransactionType.__assemblyUnbuild});
                  search.setType(transactionLineType);
                  return search(wsClient, search, pageNumber);
              }).orElse(SearchResults.emptyResults());
            }
            case "bintransfer": {
              Optional<SearchMultiSelectField> searchField = getSearchField(records, entityName, RecordType.binTransfer);
              return searchField.map(s-> {
                  TransactionSearchBasic search = new TransactionSearchBasic();
                  search.setInternalId(s);
                  SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
                  transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
                  transactionLineType.setSearchValue(new String[]{TransactionType.__binTransfer});
                  search.setType(transactionLineType);
                  return search(wsClient, search, pageNumber);
              }).orElse(SearchResults.emptyResults());
            }
            case "binworksheet": {
              Optional<SearchMultiSelectField> searchField = getSearchField(records, entityName, RecordType.binWorksheet);
              return searchField.map(s-> {
                  TransactionSearchBasic search = new TransactionSearchBasic();
                  search.setInternalId(s);
                  SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
                  transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
                  transactionLineType.setSearchValue(new String[]{TransactionType.__binWorksheet});
                  search.setType(transactionLineType);
                  return search(wsClient, search, pageNumber);
              }).orElse(SearchResults.emptyResults());
            }
            case "check": {
              Optional<SearchMultiSelectField> searchField = getSearchField(records, entityName, RecordType.check);
              return searchField.map(s-> {
                  TransactionSearchBasic search = new TransactionSearchBasic();
                  search.setInternalId(s);
                  SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
                  transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
                  transactionLineType.setSearchValue(new String[]{TransactionType.__check});
                  search.setType(transactionLineType);
                  return search(wsClient, search, pageNumber);
              }).orElse(SearchResults.emptyResults());
            }
            case "deposit": {
              Optional<SearchMultiSelectField> searchField = getSearchField(records, entityName, RecordType.deposit);
              return searchField.map(s-> {
                  TransactionSearchBasic search = new TransactionSearchBasic();
                  search.setInternalId(s);
                  SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
                  transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
                  transactionLineType.setSearchValue(new String[]{TransactionType.__deposit});
                  search.setType(transactionLineType);
                  return search(wsClient, search, pageNumber);
              }).orElse(SearchResults.emptyResults());
            }
            case "depositapplication": {
              Optional<SearchMultiSelectField> searchField = getSearchField(records, entityName, RecordType.depositApplication);
              return searchField.map(s-> {
                  TransactionSearchBasic search = new TransactionSearchBasic();
                  search.setInternalId(s);
                  SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
                  transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
                  transactionLineType.setSearchValue(new String[]{TransactionType.__depositApplication});
                  search.setType(transactionLineType);
                  return search(wsClient, search, pageNumber);
              }).orElse(SearchResults.emptyResults());
            }
            case "expensereport": {
              Optional<SearchMultiSelectField> searchField = getSearchField(records, entityName, RecordType.expenseReport);
              return searchField.map(s-> {
                  TransactionSearchBasic search = new TransactionSearchBasic();
                  search.setInternalId(s);
                  SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
                  transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
                  transactionLineType.setSearchValue(new String[]{TransactionType.__expenseReport});
                  search.setType(transactionLineType);
                  return search(wsClient, search, pageNumber);
              }).orElse(SearchResults.emptyResults());
            }
            case "intercompanyjournalentry": {
              Optional<SearchMultiSelectField> searchField = getSearchField(records, entityName, RecordType.interCompanyJournalEntry);
              return searchField.map(s-> {
                  TransactionSearchBasic search = new TransactionSearchBasic();
                  search.setInternalId(s);
                  SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
                  transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
                  transactionLineType.setSearchValue(new String[]{"_interCompanyJournalEntry"});
                  search.setType(transactionLineType);
                  return search(wsClient, search, pageNumber);
              }).orElse(SearchResults.emptyResults());
            }
            case "inventoryadjustment": {
              Optional<SearchMultiSelectField> searchField = getSearchField(records, entityName, RecordType.inventoryAdjustment);
              return searchField.map(s-> {
                  TransactionSearchBasic search = new TransactionSearchBasic();
                  search.setInternalId(s);
                  SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
                  transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
                  transactionLineType.setSearchValue(new String[]{TransactionType.__inventoryAdjustment});
                  search.setType(transactionLineType);
                  return search(wsClient, search, pageNumber);
              }).orElse(SearchResults.emptyResults());
            }
            case "inventorycostrevaluation": {
              Optional<SearchMultiSelectField> searchField = getSearchField(records, entityName, RecordType.inventoryCostRevaluation);
              return searchField.map(s-> {
                  TransactionSearchBasic search = new TransactionSearchBasic();
                  search.setInternalId(s);
                  SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
                  transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
                  transactionLineType.setSearchValue(new String[]{TransactionType.__inventoryCostRevaluation});
                  search.setType(transactionLineType);
                  return search(wsClient, search, pageNumber);
              }).orElse(SearchResults.emptyResults());
            }
            case "inventorytransfer": {
              Optional<SearchMultiSelectField> searchField = getSearchField(records, entityName, RecordType.inventoryTransfer);
              return searchField.map(s-> {
                  TransactionSearchBasic search = new TransactionSearchBasic();
                  search.setInternalId(s);
                  SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
                  transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
                  transactionLineType.setSearchValue(new String[]{TransactionType.__inventoryTransfer});
                  search.setType(transactionLineType);
                  return search(wsClient, search, pageNumber);
              }).orElse(SearchResults.emptyResults());
            }
            case "itemfulfillment": {
              Optional<SearchMultiSelectField> searchField = getSearchField(records, entityName, RecordType.itemFulfillment);
              return searchField.map(s-> {
                  TransactionSearchBasic search = new TransactionSearchBasic();
                  search.setInternalId(s);
                  SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
                  transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
                  transactionLineType.setSearchValue(new String[]{TransactionType.__itemFulfillment});
                  search.setType(transactionLineType);
                  return search(wsClient, search, pageNumber);
              }).orElse(SearchResults.emptyResults());
            }
            case "itemreceipt": {
              Optional<SearchMultiSelectField> searchField = getSearchField(records, entityName, RecordType.itemReceipt);
              return searchField.map(s-> {
                  TransactionSearchBasic search = new TransactionSearchBasic();
                  search.setInternalId(s);
                  SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
                  transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
                  transactionLineType.setSearchValue(new String[]{TransactionType.__itemReceipt});
                  search.setType(transactionLineType);
                  return search(wsClient, search, pageNumber);
              }).orElse(SearchResults.emptyResults());
            }
            case "paycheckjournal": {
              Optional<SearchMultiSelectField> searchField = getSearchField(records, entityName, RecordType.paycheckJournal);
              return searchField.map(s-> {
                  TransactionSearchBasic search = new TransactionSearchBasic();
                  search.setInternalId(s);
                  SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
                  transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
                  transactionLineType.setSearchValue(new String[]{TransactionType.__paycheckJournal});
                  search.setType(transactionLineType);
                  return search(wsClient, search, pageNumber);
              }).orElse(SearchResults.emptyResults());
            }
            case "returnauthorization": {
              Optional<SearchMultiSelectField> searchField = getSearchField(records, entityName, RecordType.returnAuthorization);
              return searchField.map(s-> {
                  TransactionSearchBasic search = new TransactionSearchBasic();
                  search.setInternalId(s);
                  SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
                  transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
                  transactionLineType.setSearchValue(new String[]{TransactionType.__returnAuthorization});
                  search.setType(transactionLineType);
                  return search(wsClient, search, pageNumber);
              }).orElse(SearchResults.emptyResults());
            }
            case "statisticaljournalentry": {
              Optional<SearchMultiSelectField> searchField = getSearchField(records, entityName, RecordType.statisticalJournalEntry);
              return searchField.map(s-> {
                  TransactionSearchBasic search = new TransactionSearchBasic();
                  search.setInternalId(s);
                  SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
                  transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
                  transactionLineType.setSearchValue(new String[]{TransactionType.__journal});
                  search.setStatistical(new SearchBooleanField(true));
                  search.setType(transactionLineType);
                  return search(wsClient, search, pageNumber);
              }).orElse(SearchResults.emptyResults());
            }
            case "transferorder": {
              Optional<SearchMultiSelectField> searchField = getSearchField(records, entityName, RecordType.transferOrder);
              return searchField.map(s-> {
                  TransactionSearchBasic search = new TransactionSearchBasic();
                  search.setInternalId(s);
                  SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
                  transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
                  transactionLineType.setSearchValue(new String[]{TransactionType.__transferOrder});
                  search.setType(transactionLineType);
                  return search(wsClient, search, pageNumber);
              }).orElse(SearchResults.emptyResults());
            }
            case "vendorbill": {
              Optional<SearchMultiSelectField> searchField = getSearchField(records, entityName, RecordType.vendorBill);
              return searchField.map(s-> {
                  TransactionSearchBasic search = new TransactionSearchBasic();
                  search.setInternalId(s);
                  SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
                  transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
                  transactionLineType.setSearchValue(new String[]{TransactionType.__vendorBill});
                  search.setType(transactionLineType);
                  return search(wsClient, search, pageNumber);
              }).orElse(SearchResults.emptyResults());
            }
            case "vendorcredit": {
              Optional<SearchMultiSelectField> searchField = getSearchField(records, entityName, RecordType.vendorCredit);
              return searchField.map(s-> {
                  TransactionSearchBasic search = new TransactionSearchBasic();
                  search.setInternalId(s);
                  SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
                  transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
                  transactionLineType.setSearchValue(new String[]{TransactionType.__vendorCredit});
                  search.setType(transactionLineType);
                  return search(wsClient, search, pageNumber);
              }).orElse(SearchResults.emptyResults());
            }
            case "vendorpayment": {
              Optional<SearchMultiSelectField> searchField = getSearchField(records, entityName, RecordType.vendorPayment);
              return searchField.map(s-> {
                  TransactionSearchBasic search = new TransactionSearchBasic();
                  search.setInternalId(s);
                  SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
                  transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
                  transactionLineType.setSearchValue(new String[]{TransactionType.__vendorPayment});
                  search.setType(transactionLineType);
                  return search(wsClient, search, pageNumber);
              }).orElse(SearchResults.emptyResults());
            }
            case "vendorreturnauthorization": {
              Optional<SearchMultiSelectField> searchField = getSearchField(records, entityName, RecordType.vendorReturnAuthorization);
              return searchField.map(s-> {
                  TransactionSearchBasic search = new TransactionSearchBasic();
                  search.setInternalId(s);
                  SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
                  transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
                  transactionLineType.setSearchValue(new String[]{TransactionType.__vendorReturnAuthorization});
                  search.setType(transactionLineType);
                  return search(wsClient, search, pageNumber);
              }).orElse(SearchResults.emptyResults());
            }
            case "workorder": {
              Optional<SearchMultiSelectField> searchField = getSearchField(records, entityName, RecordType.workOrder);
              return searchField.map(s-> {
                  TransactionSearchBasic search = new TransactionSearchBasic();
                  search.setInternalId(s);
                  SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
                  transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
                  transactionLineType.setSearchValue(new String[]{TransactionType.__workOrder});
                  search.setType(transactionLineType);
                  return search(wsClient, search, pageNumber);
              }).orElse(SearchResults.emptyResults());
            }
            case "workorderclose": {
              Optional<SearchMultiSelectField> searchField = getSearchField(records, entityName, RecordType.workOrderClose);
              return searchField.map(s-> {
                  TransactionSearchBasic search = new TransactionSearchBasic();
                  search.setInternalId(s);
                  SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
                  transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
                  transactionLineType.setSearchValue(new String[]{TransactionType.__workOrderClose});
                  search.setType(transactionLineType);
                  return search(wsClient, search, pageNumber);
              }).orElse(SearchResults.emptyResults());
            }
            case "workordercompletion": {
              Optional<SearchMultiSelectField> searchField = getSearchField(records, entityName, RecordType.workOrderCompletion);
              return searchField.map(s-> {
                  TransactionSearchBasic search = new TransactionSearchBasic();
                  search.setInternalId(s);
                  SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
                  transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
                  transactionLineType.setSearchValue(new String[]{TransactionType.__workOrderCompletion});
                  search.setType(transactionLineType);
                  return search(wsClient, search, pageNumber);
              }).orElse(SearchResults.emptyResults());
            }
            case "workorderissue": {
              Optional<SearchMultiSelectField> searchField = getSearchField(records, entityName, RecordType.workOrderIssue);
              return searchField.map(s-> {
                  TransactionSearchBasic search = new TransactionSearchBasic();
                  search.setInternalId(s);
                  SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
                  transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
                  transactionLineType.setSearchValue(new String[]{TransactionType.__workOrderIssue});
                  search.setType(transactionLineType);
                  return search(wsClient, search, pageNumber);
              }).orElse(SearchResults.emptyResults());
            }
//            case "cashsaletaxdetails": {
//              Optional<SearchMultiSelectField> searchField = getSearchField(records, entityName, RecordType.cashSale);
//              return searchField.map(s-> {
//                  TransactionSearchBasic search = new TransactionSearchBasic();
//                  search.setInternalId(s);
//                  SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
//                  transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
//                  transactionLineType.setSearchValue(new String[]{TransactionType.__assemblyUnbuild});
//                  search.setType(transactionLineType);
//                  return search(wsClient, search, pageNumber);
//              }).orElse(SearchResults.emptyResults());
//            }

            default:
                throw new NonRetriableException("unsupported_entity", String.format(I18n.i18n("netsuite_unsupported_entity"), entityName), "unsupported_entity");

        }
    }

    public SearchResults listByItemIds(String entityName, List<EntityData> records, int pageNumber, WsClient wsClient, SyncRequest request) {
        Optional<SearchMultiSelectField> searchField = null;
        SearchEnumMultiSelectField inventorySubType = new SearchEnumMultiSelectField();
        inventorySubType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
        SearchEnumMultiSelectField inventoryType = new SearchEnumMultiSelectField();
        inventoryType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
        switch (entityName) {
            case "assemblyitem":
                searchField = getSearchField(records, entityName, RecordType.assemblyItem);
                inventoryType.setSearchValue(new String[]{ItemType.__assembly});
                break;
            case "inventoryitem":
                searchField = getSearchField(records, entityName, RecordType.inventoryItem);
                inventoryType.setSearchValue(new String[]{ItemType.__inventoryItem});
                break;
            case "itemgroup":
                searchField = getSearchField(records, entityName, RecordType.itemGroup);
                inventoryType.setSearchValue(new String[]{ItemType.__itemGroup});
                break;
            case "noninventorypurchaseitem":
                searchField = getSearchField(records, entityName, RecordType.nonInventoryPurchaseItem);
                inventoryType.setSearchValue(new String[]{ItemType.__nonInventoryItem});
                inventorySubType.setSearchValue(new String[]{ItemSubType.__forPurchase});
                break;
            case "noninventoryresaleitem":
                searchField = getSearchField(records, entityName, RecordType.nonInventoryResaleItem);
                inventoryType.setSearchValue(new String[]{ItemType.__nonInventoryItem});
                inventorySubType.setSearchValue(new String[]{ItemSubType.__forResale});
                break;
            case "noninventorysaleitem":
                searchField = getSearchField(records, entityName, RecordType.nonInventorySaleItem);
                inventoryType.setSearchValue(new String[]{ItemType.__nonInventoryItem});
                inventorySubType.setSearchValue(new String[]{ItemSubType.__forSale});
                break;
            case "kititem":
                searchField = getSearchField(records, entityName, RecordType.kitItem);
                inventoryType.setSearchValue(new String[]{ItemType.__kit});
                break;
            case "otherchargepurchaseitem":
                searchField = getSearchField(records, entityName, RecordType.otherChargePurchaseItem);
                inventoryType.setSearchValue(new String[]{ItemType.__otherCharge});
                inventorySubType.setSearchValue(new String[]{ItemSubType.__forPurchase});
                break;
            case "otherchargeresaleitem":
                searchField = getSearchField(records, entityName, RecordType.otherChargeResaleItem);
                inventoryType.setSearchValue(new String[]{ItemType.__otherCharge});
                inventorySubType.setSearchValue(new String[]{ItemSubType.__forResale});
                break;
            case "otherchargesaleitem":
                searchField = getSearchField(records, entityName, RecordType.otherChargeSaleItem);
                inventoryType.setSearchValue(new String[]{ItemType.__otherCharge});
                inventorySubType.setSearchValue(new String[]{ItemSubType.__forSale});
                break;
            case "paymentitem":
                searchField = getSearchField(records, entityName, RecordType.paymentItem);
                inventoryType.setSearchValue(new String[]{ItemType.__payment});
                break;
            case "servicepurchaseitem":
                searchField = getSearchField(records, entityName, RecordType.servicePurchaseItem);
                inventoryType.setSearchValue(new String[]{ItemType.__service});
                inventorySubType.setSearchValue(new String[]{ItemSubType.__forPurchase});
                break;
            case "serviceresaleitem":
                searchField = getSearchField(records, entityName, RecordType.serviceResaleItem);
                inventoryType.setSearchValue(new String[]{ItemType.__service});
                inventorySubType.setSearchValue(new String[]{ItemSubType.__forResale});
                break;
            case "servicesaleitem":
                searchField = getSearchField(records, entityName, RecordType.serviceSaleItem);
                inventoryType.setSearchValue(new String[]{ItemType.__service});
                inventorySubType.setSearchValue(new String[]{ItemSubType.__forSale});
                break;
            case "descriptionitem":
                searchField = getSearchField(records, entityName, RecordType.descriptionItem);
                inventoryType.setSearchValue(new String[]{ItemType.__description});
                break;
            case "discountitem":
                searchField = getSearchField(records, entityName, RecordType.discountItem);
                inventoryType.setSearchValue(new String[]{ItemType.__discount});
                break;
            case "giftcertificateitem":
                searchField = getSearchField(records, entityName, RecordType.giftCertificateItem);
                inventoryType.setSearchValue(new String[]{ItemType.__giftCertificateItem});
                break;
            case "markupitem":
                searchField = getSearchField(records, entityName, RecordType.markupItem);
                inventoryType.setSearchValue(new String[]{ItemType.__markup});
                break;
            case "subtotalitem":
                searchField = getSearchField(records, entityName, RecordType.subtotalItem);
                inventoryType.setSearchValue(new String[]{ItemType.__subtotal});
                break;
            default:
                throw new NonRetriableException("unsupported_entity", 
                    String.format(I18n.i18n("netsuite_unsupported_entity"), entityName), "unsupported_entity");
        }

        return searchField.map(s-> {
            ItemSearchBasic search = new ItemSearchBasic();
            search.setInternalId(s);
            search.setType(inventoryType);
            if (inventorySubType.getSearchValue() != null && inventorySubType.getSearchValue().length > 0) {
                search.setSubType(inventorySubType);
            }
            return search(wsClient,search, pageNumber);
        }).orElse(SearchResults.emptyResults());
    }

    private void updateOpptyContacts(SyncRequest request, SearchResults oppties) {
        for (Record oppty : oppties.getRecords()) {
            ContactSearch contactSearch = new ContactSearch();
            OpportunitySearchBasic opportunityJoin = new OpportunitySearchBasic();
            String opptyId = Opportunity.class.cast(oppty).getInternalId();
            Long internalId = Long.valueOf(opptyId);
            opportunityJoin.setInternalIdNumber(new SearchLongField(internalId, null, SearchLongFieldOperator.equalTo));
            contactSearch.setOpportunityJoin(opportunityJoin);
            WsClient contactClient = getClient(request.getConnector());
            SearchResults opptyContacts = search(contactClient, contactSearch, 0);
            oppties.setReferences(opptyId, "contact", opptyContacts.getRecords());
        }
    }

    public void getDocumentFiles(SyncRequest request, List<EntityData> nsTxns) {

        SearchColumnSelectField[] fileId = new SearchColumnSelectField[1];
        fileId[0] = new SearchColumnSelectField();
        SearchColumnStringField[] fileName = new SearchColumnStringField[1];
        fileName[0] = new SearchColumnStringField();

        FileSearchRowBasic fileJoin = new FileSearchRowBasic();
        fileJoin.setInternalId(fileId);
        fileJoin.setName(fileName);

        for (EntityData nsTxn: nsTxns) {
            switch (request.getEntityName()) {
                case "opportunity":
                    OpportunitySearchAdvanced advanced = new OpportunitySearchAdvanced();
                    OpportunitySearch search = new OpportunitySearch();
                    OpportunitySearchRow tsRow = new OpportunitySearchRow();
                    OpportunitySearchBasic basic = new OpportunitySearchBasic();
                    basic.setInternalIdNumber(new SearchLongField(Long.valueOf(nsTxn.getId()), null, SearchLongFieldOperator.equalTo));
                    search.setBasic(basic);
                    tsRow.setFileJoin(fileJoin);
                    advanced.setColumns(tsRow);
                    advanced.setCriteria(search);
                    WsClient oppSearchClient = getClient(request.getConnector());
                    // TODO: This maybe ok for now, but the real way to do this is to paginate the files.
                    List<String> fileReferences = getFileReferences(request.getEntityName(), oppSearchClient, advanced, 0);
                    nsTxn.addValue(EntityData.SYNCARI_FILE_REFERENCE_FIELD_NAME, fileReferences);
                    break;
                default:
                    TransactionSearchAdvanced advSearch = new TransactionSearchAdvanced();
                    TransactionSearch txnSearch = new TransactionSearch();
                    TransactionSearchRow txnRow = new TransactionSearchRow();
                    TransactionSearchBasic txnBasic = new TransactionSearchBasic();
                    txnBasic.setInternalIdNumber(new SearchLongField(Long.valueOf(nsTxn.getId()), null, SearchLongFieldOperator.equalTo));
                    // Get the files as if from the top level.
                    txnBasic.setMainLine(new SearchBooleanField(Boolean.TRUE));
                    txnSearch.setBasic(txnBasic);
                    txnRow.setFileJoin(fileJoin);
                    advSearch.setColumns(txnRow);
                    advSearch.setCriteria(txnSearch);
                    WsClient txnSearchClient = getClient(request.getConnector());
                    // TODO: This maybe ok for now, but the real way to do this is to paginate the files.
                    List<String> txnFileReferences = getFileReferences(request.getEntityName(), txnSearchClient, advSearch, 0);
                    nsTxn.addValue(EntityData.SYNCARI_FILE_REFERENCE_FIELD_NAME, txnFileReferences);
            }            
        }
    }

    private Optional<SearchMultiSelectField> getSearchField(List<EntityData> records, String entityName, RecordType opportunity) {
        RecordRef[] recordRefs = getRecordRefs(records, entityName, opportunity);
        if(recordRefs.length > 0) {
            SearchMultiSelectField internalIds = new SearchMultiSelectField();
            internalIds.setSearchValue(recordRefs);
            internalIds.setOperator(SearchMultiSelectFieldOperator.anyOf);
            return Optional.of(internalIds);
        }
        return Optional.empty();
    }

    private RecordRef[] getRecordRefs(List<EntityData> ids, String entityName, RecordType recordType) {
        List<RecordRef> recordRefList = ids.stream().filter(record -> NumberUtils.isCreatable(record.getId())).map(record -> new RecordRef(entityName, record.getId(), null, recordType)).collect(Collectors.toList());
        return recordRefList.toArray(new RecordRef[recordRefList.size()]);
    }

    public SearchResults list(WsClient wsClient, SyncRequest request, WatermarkInfo wm, int pageSize, int pageNumber) {
        String entityName = request.getEntityName();
        //Make sure the Netsuite user has the TZ set to GMT/UTC
        Calendar watermark = getCalendar(wm.getStart());
        Calendar watermarkEnd = getCalendar(wm.hasEnd()? wm.getEnd(): Instant.now().toEpochMilli());

        SearchDateField watermarkSearch = new SearchDateField();
        watermarkSearch.setSearchValue(watermark);
        watermarkSearch.setOperator(SearchDateFieldOperator.within);
        watermarkSearch.setSearchValue2(watermarkEnd);
        if(pageNumber==0) {
            wsClient.setPageSize(request.getPageSize() == 0 ? pageSize : request.getPageSize());
        }

        if (ITEM_OBJECTS.contains(entityName)) {
            return listItem(entityName, wsClient, watermarkSearch, pageNumber);
        }

        EntitySchema entitySchema = request.getEntitySchema();

        if (entitySchema.isCustom()){
            CustomRecordSearchBasic search = getCustomRecordSearchBasic(entityName);
            search.setLastModified(watermarkSearch);
            return search(wsClient, search, pageNumber);
        }

        switch (entityName) {
            case "opportunity": {
                OpportunitySearchBasic opportunitySearchBasic = new OpportunitySearchBasic();
                opportunitySearchBasic.setLastModifiedDate(watermarkSearch);

                SearchResults oppties = search(wsClient, opportunitySearchBasic, pageNumber);
                updateOpptyContacts(request, oppties);
                return oppties;
            }
            case "customer": {
                wsClient.setSearchPreferences(false, request.getPageSize(), true);
                CustomerSearchBasic search = new CustomerSearchBasic();
                search.setLastModifiedDate(watermarkSearch);
                return search(wsClient, search, pageNumber);
            }
            case "contact": {
                wsClient.setSearchPreferences(false, request.getPageSize(), true);
                ContactSearchBasic search = new ContactSearchBasic();
                search.setLastModifiedDate(watermarkSearch);
                return search(wsClient, search, pageNumber);
            }
            case "supportcase": {
                SupportCaseSearchBasic search = new SupportCaseSearchBasic();
                search.setLastModifiedDate(watermarkSearch);
                return search(wsClient, search, pageNumber);
            }
            case "employee": {
                EmployeeSearchBasic search = new EmployeeSearchBasic();
                search.setLastModifiedDate(watermarkSearch);
                return search(wsClient, search, pageNumber);
            }
            case "task": {
                TaskSearchBasic search = new TaskSearchBasic();
                search.setLastModifiedDate(watermarkSearch);
                return search(wsClient, search, pageNumber);
            }
            case "campaign": {
                CampaignSearchBasic search = new CampaignSearchBasic();
                search.setLastModifiedDate(watermarkSearch);
                return search(wsClient, search, pageNumber);
            }
            case "journalEntry": {
                TransactionSearchBasic search = new TransactionSearchBasic();
                SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
                transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
                transactionLineType.setSearchValue(new String[]{TransactionType.__journal});
                search.setType(transactionLineType);
                search.setLastModifiedDate(watermarkSearch);
                return search(wsClient, search, pageNumber);
            }
            case "vendor": {
                VendorSearchBasic search = new VendorSearchBasic();
                search.setLastModifiedDate(watermarkSearch);
                return search(wsClient, search, pageNumber);
            }
            case "partner": {
                PartnerSearchBasic search = new PartnerSearchBasic();
                search.setLastModifiedDate(watermarkSearch);
                return search(wsClient, search, pageNumber);
            }
            case "salesorder": {
                // Create the search criteria for Sales Orders
                TransactionSearchBasic searchBasic = new TransactionSearchBasic();
                searchBasic.setLastModifiedDate(watermarkSearch);
                searchBasic.setMemorized(new SearchBooleanField(false));

                // Add the mainLine filter to only get mainline transactions
                SearchBooleanField mainLineField = new SearchBooleanField();
                mainLineField.setSearchValue(true); // Set to true to get only main lines
                searchBasic.setMainLine(mainLineField);

                // Define the transaction type as sales order
                SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
                transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
                transactionLineType.setSearchValue(new String[]{TransactionType.__salesOrder});
                searchBasic.setType(transactionLineType);

                // Create a TransactionSearch and set its basic criteria
                TransactionSearch transactionSearch = new TransactionSearch();
                transactionSearch.setBasic(searchBasic);

                // Create a TransactionSearchRow to specify the fields you want to retrieve
                TransactionSearchRow searchRow = new TransactionSearchRow();
                TransactionSearchRowBasic searchRowBasic = new TransactionSearchRowBasic();

                // Specify that you want to retrieve only the internalId field using SearchColumnLongField
                SearchColumnSelectField internalIdColumn = new SearchColumnSelectField();
                internalIdColumn.setCustomLabel("internalId");

                // Add the internalId column to the search row
                searchRowBasic.setInternalId(new SearchColumnSelectField[]{internalIdColumn});
                searchRow.setBasic(searchRowBasic);

                // Combine the search and search row in a TransactionSearchAdvanced
                TransactionSearchAdvanced searchAdvanced = new TransactionSearchAdvanced();
                searchAdvanced.setCriteria(transactionSearch);
                searchAdvanced.setColumns(searchRow);

                // Set the search preferences
                wsClient.setSearchPreferences(true, pageSize, true);

                // Execute the search with the combined advanced search object
                return search(wsClient, searchAdvanced, pageNumber);
            }
            case "customerdeposit": {
                TransactionSearchBasic search = new TransactionSearchBasic();
                search.setLastModifiedDate(watermarkSearch);
                SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
                transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
                transactionLineType.setSearchValue(new String[]{TransactionType.__customerDeposit});
                search.setType(transactionLineType);
                return search(wsClient, search, pageNumber);
            }
            case "estimate": {
                TransactionSearchBasic search = new TransactionSearchBasic();
                search.setLastModifiedDate(watermarkSearch);
                search.setMemorized(new SearchBooleanField(false));
                SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
                transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
                transactionLineType.setSearchValue(new String[]{TransactionType.__estimate});
                search.setType(transactionLineType);
                wsClient.setSearchPreferences(false,0,false);
                return search(wsClient, search, pageNumber);
            }
            case "invoice": {
                TransactionSearchBasic search = new TransactionSearchBasic();
                search.setLastModifiedDate(watermarkSearch);
                search.setMemorized(new SearchBooleanField(false));
                SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
                transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
                transactionLineType.setSearchValue(new String[]{TransactionType.__invoice});
                search.setType(transactionLineType);
                wsClient.setSearchPreferences(false,0,false);
                return search(wsClient, search, pageNumber);
            }
            case "customerpayment": {
                TransactionSearchBasic search = new TransactionSearchBasic();
                search.setLastModifiedDate(watermarkSearch);
                SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
                transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
                transactionLineType.setSearchValue(new String[]{TransactionType.__customerPayment});
                search.setType(transactionLineType);
                return search(wsClient, search, pageNumber);
            }
            case "cashsale": {
                TransactionSearchBasic search = new TransactionSearchBasic();
                search.setLastModifiedDate(watermarkSearch);
                search.setMemorized(new SearchBooleanField(false));
                SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
                transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
                transactionLineType.setSearchValue(new String[]{TransactionType.__cashSale});
                search.setType(transactionLineType);
                return search(wsClient, search, pageNumber);
            }
            case "cashrefund": {
                TransactionSearchBasic search = new TransactionSearchBasic();
                search.setLastModifiedDate(watermarkSearch);
                SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
                transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
                transactionLineType.setSearchValue(new String[]{TransactionType.__cashRefund});
                search.setType(transactionLineType);
                return search(wsClient, search, pageNumber);
            }
            case "customerrefund": {
                TransactionSearchBasic search = new TransactionSearchBasic();
                search.setLastModifiedDate(watermarkSearch);
                SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
                transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
                transactionLineType.setSearchValue(new String[]{TransactionType.__customerRefund});
                search.setType(transactionLineType);
                return search(wsClient, search, pageNumber);
            }
            case "creditmemo": {
                TransactionSearchBasic search = new TransactionSearchBasic();
                search.setLastModifiedDate(watermarkSearch);
                SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
                transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
                transactionLineType.setSearchValue(new String[]{TransactionType.__creditMemo});
                search.setType(transactionLineType);
                return search(wsClient, search, pageNumber);
            }
            case "purchaseorder": {
                TransactionSearchBasic search = new TransactionSearchBasic();
                search.setLastModifiedDate(watermarkSearch);
                SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
                transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
                transactionLineType.setSearchValue(new String[]{TransactionType.__purchaseOrder});
                search.setType(transactionLineType);
                return search(wsClient, search, pageNumber);
            }
            case "file": {
                FileSearchBasic search = new FileSearchBasic();
                search.setModified(watermarkSearch);
                SearchEnumMultiSelectField fileType = new SearchEnumMultiSelectField();
                fileType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
                fileType.setSearchValue(supportedDocumentTypes);
                search.setFileType(fileType);
                return search(wsClient, search, pageNumber);
            }
            case "assemblybuild": {
              TransactionSearchBasic search = new TransactionSearchBasic();
              search.setLastModifiedDate(watermarkSearch);
              SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
              transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
              transactionLineType.setSearchValue(new String[]{TransactionType.__assemblyBuild});
              search.setType(transactionLineType);
              return search(wsClient, search, pageNumber);
              
            }
            case "assemblyunbuild": {
              TransactionSearchBasic search = new TransactionSearchBasic();
              search.setLastModifiedDate(watermarkSearch);
              SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
              transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
              transactionLineType.setSearchValue(new String[]{TransactionType.__assemblyBuild});
              search.setType(transactionLineType);
              return search(wsClient, search, pageNumber);
            }
            case "bintransfer": {
              TransactionSearchBasic search = new TransactionSearchBasic();
              search.setLastModifiedDate(watermarkSearch);
              SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
              transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
              transactionLineType.setSearchValue(new String[]{TransactionType.__binTransfer});
              search.setType(transactionLineType);
              return search(wsClient, search, pageNumber);
            }
            case "binworksheet": {
              TransactionSearchBasic search = new TransactionSearchBasic();
              search.setLastModifiedDate(watermarkSearch);
              SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
              transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
              transactionLineType.setSearchValue(new String[]{TransactionType.__binWorksheet});
              search.setType(transactionLineType);
              return search(wsClient, search, pageNumber);
            }
            case "check": {
              TransactionSearchBasic search = new TransactionSearchBasic();
              search.setLastModifiedDate(watermarkSearch);
              search.setMemorized(new SearchBooleanField(false));
              SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
              transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
              transactionLineType.setSearchValue(new String[]{TransactionType.__check});
              search.setType(transactionLineType);
              return search(wsClient, search, pageNumber);
            }
            case "deposit": {
              TransactionSearchBasic search = new TransactionSearchBasic();
              search.setLastModifiedDate(watermarkSearch);
              SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
              transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
              transactionLineType.setSearchValue(new String[]{TransactionType.__deposit});
              search.setType(transactionLineType);
              return search(wsClient, search, pageNumber);
            }
            case "depositapplication": {
              TransactionSearchBasic search = new TransactionSearchBasic();
              search.setLastModifiedDate(watermarkSearch);
              SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
              transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
              transactionLineType.setSearchValue(new String[]{TransactionType.__depositApplication});
              search.setType(transactionLineType);
              return search(wsClient, search, pageNumber);
            }
            case "expensereport": {
              TransactionSearchBasic search = new TransactionSearchBasic();
              search.setLastModifiedDate(watermarkSearch);
              SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
              transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
              transactionLineType.setSearchValue(new String[]{TransactionType.__expenseReport});
              search.setType(transactionLineType);
              return search(wsClient, search, pageNumber);
            }
            case "intercompanyjournalentry": {
              TransactionSearchBasic search = new TransactionSearchBasic();
              search.setLastModifiedDate(watermarkSearch);
              SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
              transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
              transactionLineType.setSearchValue(new String[]{"_interCompanyJournalEntry"});
              search.setType(transactionLineType);
              
            }
            case "inventoryadjustment": {
              TransactionSearchBasic search = new TransactionSearchBasic();
              search.setLastModifiedDate(watermarkSearch);
              SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
              transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
              transactionLineType.setSearchValue(new String[]{TransactionType.__inventoryAdjustment});
              search.setType(transactionLineType);
              return search(wsClient, search, pageNumber);
            }
            case "inventorycostrevaluation": {
              TransactionSearchBasic search = new TransactionSearchBasic();
              search.setLastModifiedDate(watermarkSearch);
              SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
              transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
              transactionLineType.setSearchValue(new String[]{TransactionType.__inventoryCostRevaluation});
              search.setType(transactionLineType);
              return search(wsClient, search, pageNumber);
            }
            case "inventorytransfer": {
              TransactionSearchBasic search = new TransactionSearchBasic();
              search.setLastModifiedDate(watermarkSearch);
              SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
              transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
              transactionLineType.setSearchValue(new String[]{TransactionType.__inventoryTransfer});
              search.setType(transactionLineType);
              return search(wsClient, search, pageNumber);
            }
            case "itemfulfillment": {
              TransactionSearchBasic search = new TransactionSearchBasic();
              search.setLastModifiedDate(watermarkSearch);
              SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
              transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
              transactionLineType.setSearchValue(new String[]{TransactionType.__itemFulfillment});
              search.setType(transactionLineType);
              return search(wsClient, search, pageNumber);
            }
            case "itemreceipt": {
              TransactionSearchBasic search = new TransactionSearchBasic();
              search.setLastModifiedDate(watermarkSearch);
              SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
              transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
              transactionLineType.setSearchValue(new String[]{TransactionType.__itemReceipt});
              search.setType(transactionLineType);
              return search(wsClient, search, pageNumber);
            }
            case "paycheckjournal": {
              TransactionSearchBasic search = new TransactionSearchBasic();
              search.setLastModifiedDate(watermarkSearch);
              SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
              transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
              transactionLineType.setSearchValue(new String[]{TransactionType.__paycheckJournal});
              search.setType(transactionLineType);
              return search(wsClient, search, pageNumber);
            }
            case "returnauthorization": {
              TransactionSearchBasic search = new TransactionSearchBasic();
              search.setLastModifiedDate(watermarkSearch);
              SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
              transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
              transactionLineType.setSearchValue(new String[]{TransactionType.__returnAuthorization});
              search.setType(transactionLineType);
              return search(wsClient, search, pageNumber);
            }
            case "statisticaljournalentry": {
              TransactionSearchBasic search = new TransactionSearchBasic();
              search.setLastModifiedDate(watermarkSearch);
              search.setStatistical(new SearchBooleanField(true));
              SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
              transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
              transactionLineType.setSearchValue(new String[]{TransactionType.__journal});
              search.setType(transactionLineType);
              return search(wsClient, search, pageNumber);
            }
            case "transferorder": {
              TransactionSearchBasic search = new TransactionSearchBasic();
              search.setLastModifiedDate(watermarkSearch);
              SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
              transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
              transactionLineType.setSearchValue(new String[]{TransactionType.__transferOrder});
              search.setType(transactionLineType);
              return search(wsClient, search, pageNumber);
            }
            case "vendorbill": {
              TransactionSearchBasic search = new TransactionSearchBasic();
              search.setLastModifiedDate(watermarkSearch);
              SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
              transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
              transactionLineType.setSearchValue(new String[]{TransactionType.__vendorBill});
              search.setType(transactionLineType);
              return search(wsClient, search, pageNumber);
            }
            case "vendorcredit": {
              TransactionSearchBasic search = new TransactionSearchBasic();
              search.setLastModifiedDate(watermarkSearch);
              SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
              transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
              transactionLineType.setSearchValue(new String[]{TransactionType.__vendorCredit});
              search.setType(transactionLineType);
              return search(wsClient, search, pageNumber);
            }
            case "vendorpayment": {
              TransactionSearchBasic search = new TransactionSearchBasic();
              search.setLastModifiedDate(watermarkSearch);
              SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
              transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
              transactionLineType.setSearchValue(new String[]{TransactionType.__vendorPayment});
              search.setType(transactionLineType);
              return search(wsClient, search, pageNumber);
            }
            case "vendorreturnauthorization": {
              TransactionSearchBasic search = new TransactionSearchBasic();
              search.setLastModifiedDate(watermarkSearch);
              SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
              transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
              transactionLineType.setSearchValue(new String[]{TransactionType.__vendorReturnAuthorization});
              search.setType(transactionLineType);
              return search(wsClient, search, pageNumber);
            }
            case "workorder": {
              TransactionSearchBasic search = new TransactionSearchBasic();
              search.setLastModifiedDate(watermarkSearch);
              SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
              transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
              transactionLineType.setSearchValue(new String[]{TransactionType.__workOrder});
              search.setType(transactionLineType);
              return search(wsClient, search, pageNumber);
            }
            case "workorderclose": {
              TransactionSearchBasic search = new TransactionSearchBasic();
              search.setLastModifiedDate(watermarkSearch);
              SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
              transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
              transactionLineType.setSearchValue(new String[]{TransactionType.__workOrder});
              search.setType(transactionLineType);
              return search(wsClient, search, pageNumber);
            }
            case "workordercompletion": {
              TransactionSearchBasic search = new TransactionSearchBasic();
              search.setLastModifiedDate(watermarkSearch);
              SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
              transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
              transactionLineType.setSearchValue(new String[]{TransactionType.__workOrderCompletion});
              search.setType(transactionLineType);
              return search(wsClient, search, pageNumber);
            }
            case "workorderissue": {
              TransactionSearchBasic search = new TransactionSearchBasic();
              search.setLastModifiedDate(watermarkSearch);
              SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
              transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
              transactionLineType.setSearchValue(new String[]{TransactionType.__workOrderIssue});
              search.setType(transactionLineType);
              return search(wsClient, search, pageNumber);
            }
//            case "cashsaletaxdetails": {
//              Optional<SearchMultiSelectField> searchField = getSearchField(records, entityName, RecordType.cashSale);
//              return searchField.map(s-> {
//                  TransactionSearchBasic search = new TransactionSearchBasic();
//                  search.setInternalId(s);
//                  SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
//                  transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
//                  transactionLineType.setSearchValue(new String[]{TransactionType.__assemblyUnbuild});
//                  search.setType(transactionLineType);
//                  return search(wsClient, search, pageNumber);
//              }).orElse(SearchResults.emptyResults());
//            }
            default:
                throw new NonRetriableException("unsupported_entity", String.format(I18n.i18n("netsuite_unsupported_entity"), entityName), "unsupported_entity");
        }
    }

    public SearchResults listItem(String entityName, WsClient wsClient, SearchDateField watermarkSearch, int pageNumber) {
        ItemSearchBasic search = new ItemSearchBasic();
        SearchEnumMultiSelectField inventoryType = new SearchEnumMultiSelectField();
        inventoryType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);

        SearchEnumMultiSelectField inventorySubType = new SearchEnumMultiSelectField();
        inventorySubType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
        switch (entityName) {
            case "assemblyitem":
                inventoryType.setSearchValue(new String[]{ItemType.__assembly});
                break;
            case "inventoryitem":
                inventoryType.setSearchValue(new String[]{ItemType.__inventoryItem});
                break;
            case "itemgroup":
                inventoryType.setSearchValue(new String[]{ItemType.__itemGroup});
                break;
            case "noninventorypurchaseitem":
                inventoryType.setSearchValue(new String[]{ItemType.__nonInventoryItem});
                inventorySubType.setSearchValue(new String[]{ItemSubType.__forPurchase});
                break;
            case "noninventoryresaleitem":
                inventoryType.setSearchValue(new String[]{ItemType.__nonInventoryItem});
                inventorySubType.setSearchValue(new String[]{ItemSubType.__forResale});
                break;
            case "noninventorysaleitem":
                inventoryType.setSearchValue(new String[]{ItemType.__nonInventoryItem});
                inventorySubType.setSearchValue(new String[]{ItemSubType.__forSale});
                break;
            case "kititem":
                inventoryType.setSearchValue(new String[]{ItemType.__kit});
                break;
            case "otherchargepurchaseitem":
                inventoryType.setSearchValue(new String[]{ItemType.__otherCharge});
                inventorySubType.setSearchValue(new String[]{ItemSubType.__forPurchase});
                break;
            case "otherchargeresaleitem":
                inventoryType.setSearchValue(new String[]{ItemType.__otherCharge});
                inventorySubType.setSearchValue(new String[]{ItemSubType.__forResale});
                break;
            case "otherchargesaleitem":
                inventoryType.setSearchValue(new String[]{ItemType.__otherCharge});
                inventorySubType.setSearchValue(new String[]{ItemSubType.__forSale});
                break;
            case "paymentitem":
                inventoryType.setSearchValue(new String[]{ItemType.__payment});
                break;
            case "servicepurchaseitem":
                inventoryType.setSearchValue(new String[]{ItemType.__service});
                inventorySubType.setSearchValue(new String[]{ItemSubType.__forPurchase});
                break;
            case "serviceresaleitem":
                inventoryType.setSearchValue(new String[]{ItemType.__service});
                inventorySubType.setSearchValue(new String[]{ItemSubType.__forResale});
                break;
            case "servicesaleitem":
                inventoryType.setSearchValue(new String[]{ItemType.__service});
                inventorySubType.setSearchValue(new String[]{ItemSubType.__forSale});
                break;
            case "descriptionitem":
                inventoryType.setSearchValue(new String[]{ItemType.__description});
                break;
            case "discountitem":
                inventoryType.setSearchValue(new String[]{ItemType.__discount});
                break;
            case "giftcertificateitem":
                inventoryType.setSearchValue(new String[]{ItemType.__giftCertificateItem});
                break;
            case "markupitem":
                inventoryType.setSearchValue(new String[]{ItemType.__markup});
                break;
            case "subtotalitem":
                inventoryType.setSearchValue(new String[]{ItemType.__subtotal});
                break;
            default:
                throw new NonRetriableException("unsupported_entity", String.format(I18n.i18n("netsuite_unsupported_entity"), entityName), "unsupported_entity");
        }
        search.setType(inventoryType);
        // If has subtype additionally filter by that.
        if (inventorySubType.getSearchValue() != null && inventorySubType.getSearchValue().length > 0) {
            search.setSubType(inventorySubType);
        }
        search.setLastModifiedDate(watermarkSearch);
        return search(wsClient, search, pageNumber);
    }

    private CustomRecordSearchBasic getCustomRecordSearchBasic(String entityName){
        CustomRecordSearchBasic search = new CustomRecordSearchBasic();

        CustomizationRef customizationRef = new CustomizationRef();
        customizationRef.setScriptId(entityName);
        customizationRef.setType(RecordType.customRecordType);

        search.setRecType(customizationRef);
        return search;
    }

    public long findFirst(WsClient wsClient, SyncRequest request, WatermarkInfo wm) {
        String entityName = request.getEntityName();
        //Make sure the Netsuite user has the TZ set to GMT/UTC

        SearchResults results=null;
        long start = wm.getStart();
        long end = wm.hasEnd()? wm.getEnd(): Instant.now().toEpochMilli();


        while(start >= 0) {
            long mid = start + (end - start)/2 +1;
            Calendar watermark = getCalendar(mid);

            SearchDateField watermarkSearch = new SearchDateField();
            watermarkSearch.setSearchValue(watermark);
            watermarkSearch.setOperator(SearchDateFieldOperator.onOrBefore);
            //5 is the min page size
            wsClient.setPageSize(5);
            int pageNumber = 0;

            if (ITEM_OBJECTS.contains(entityName)) {
                results = listItem(entityName, wsClient, watermarkSearch, pageNumber);
            } else if (request.getEntitySchema().isCustom()){
                    CustomRecordSearchBasic search = getCustomRecordSearchBasic(entityName);
                    search.setLastModified(watermarkSearch);
                    results = search(wsClient, search, pageNumber);
            } else {
                switch (entityName) {
                    case "opportunity": {
                        OpportunitySearchBasic opportunitySearchBasic = new OpportunitySearchBasic();
                        opportunitySearchBasic.setLastModifiedDate(watermarkSearch);
                        results = search(wsClient, opportunitySearchBasic, pageNumber);
                        break;
                    }
                    case "customer": {
                        CustomerSearchBasic search = new CustomerSearchBasic();
                        AccountSearchBasic accountSearchBasic = new AccountSearchBasic();
                        search.setLastModifiedDate(watermarkSearch);
                        results = search(wsClient, search, pageNumber);
                        break;
                    }
                    case "contact": {
                        ContactSearchBasic search = new ContactSearchBasic();
                        search.setLastModifiedDate(watermarkSearch);
                        results = search(wsClient, search, pageNumber);
                        break;
                    }
                    case "partner": {
                        PartnerSearchBasic search = new PartnerSearchBasic();
                        search.setLastModifiedDate(watermarkSearch);
                        results = search(wsClient, search, pageNumber);
                        break;
                    }
                    case "employee": {
                        EmployeeSearchBasic search = new EmployeeSearchBasic();
                        search.setLastModifiedDate(watermarkSearch);
                        results = search(wsClient, search, pageNumber);
                        break;
                    }
                    case "journalEntry": {
                        TransactionSearchBasic search = new TransactionSearchBasic();
                        SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
                        transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
                        transactionLineType.setSearchValue(new String[]{TransactionType.__journal});
                        search.setType(transactionLineType);
                        search.setLastModifiedDate(watermarkSearch);
                        results = search(wsClient, search, pageNumber);
                        break;
                    }
                    case "vendor": {
                        VendorSearchBasic search = new VendorSearchBasic();
                        search.setLastModifiedDate(watermarkSearch);
                        results = search(wsClient, search, pageNumber);
                        break;
                    }
                    case "task": {
                        TaskSearchBasic search = new TaskSearchBasic();
                        search.setLastModifiedDate(watermarkSearch);
                        results = search(wsClient, search, pageNumber);
                        break;
                    }
                    case "salesorder": {
                        TransactionSearchBasic search = new TransactionSearchBasic();
                        // Add the mainLine filter to only get mainline transactions
                        SearchBooleanField mainLineField = new SearchBooleanField();
                        mainLineField.setSearchValue(true); // Set to true to get only main lines
                        search.setMainLine(mainLineField);
                        search.setLastModifiedDate(watermarkSearch);
                        SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
                        transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
                        transactionLineType.setSearchValue(new String[]{TransactionType.__salesOrder});
                        search.setType(transactionLineType);

                        // Create a TransactionSearch and set its basic criteria
                        TransactionSearch transactionSearch = new TransactionSearch();
                        transactionSearch.setBasic(search);

                        // Create a TransactionSearchRow to specify the fields you want to retrieve
                        TransactionSearchRow searchRow = new TransactionSearchRow();
                        TransactionSearchRowBasic searchRowBasic = new TransactionSearchRowBasic();

                        // Specify that you want to retrieve the internalId field using SearchColumnSelectField
                        SearchColumnSelectField internalIdColumn = new SearchColumnSelectField();
                        internalIdColumn.setCustomLabel("internalId");

                        // Add the internalId column to the search row
                        searchRowBasic.setInternalId(new SearchColumnSelectField[]{internalIdColumn});
                        searchRow.setBasic(searchRowBasic);

                        // Combine the search and search row in a TransactionSearchAdvanced
                        TransactionSearchAdvanced searchAdvanced = new TransactionSearchAdvanced();
                        searchAdvanced.setCriteria(transactionSearch);
                        searchAdvanced.setColumns(searchRow);

                        // Set the search preferences
                        wsClient.setSearchPreferences(true, 5, true);
                        results = search(wsClient, searchAdvanced, pageNumber);
                        break;
                    }
                    case "customerdeposit": {
                        TransactionSearchBasic search = new TransactionSearchBasic();
                        search.setLastModifiedDate(watermarkSearch);
                        SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
                        transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
                        transactionLineType.setSearchValue(new String[]{TransactionType.__customerDeposit});
                        search.setType(transactionLineType);
                        results = search(wsClient, search, pageNumber);
                        break;
                    }
                    case "estimate": {
                        TransactionSearchBasic search = new TransactionSearchBasic();
                        search.setLastModifiedDate(watermarkSearch);
                        SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
                        transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
                        transactionLineType.setSearchValue(new String[]{TransactionType.__estimate});
                        search.setType(transactionLineType);
                        results = search(wsClient, search, pageNumber);
                        break;
                    }
                    case "invoice": {
                        TransactionSearchBasic search = new TransactionSearchBasic();
                        search.setLastModifiedDate(watermarkSearch);
                        SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
                        transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
                        transactionLineType.setSearchValue(new String[]{TransactionType.__invoice});
                        search.setType(transactionLineType);
                        results = search(wsClient, search, pageNumber);
                        break;
                    }
                    case "customerpayment": {
                        TransactionSearchBasic search = new TransactionSearchBasic();
                        search.setLastModifiedDate(watermarkSearch);
                        SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
                        transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
                        transactionLineType.setSearchValue(new String[]{TransactionType.__customerPayment});
                        search.setType(transactionLineType);
                        results = search(wsClient, search, pageNumber);
                        break;
                    }
                    case "cashsale": {
                        results = getSearchResults(watermarkSearch, TransactionType.__cashSale, wsClient, pageNumber);
                        break;
                    }
                    case "cashrefund": {
                        results = getSearchResults(watermarkSearch, TransactionType.__cashRefund, wsClient, pageNumber);
                        break;
                    }
                    case "creditmemo": {
                        results = getSearchResults(watermarkSearch, TransactionType.__creditMemo, wsClient, pageNumber);
                        break;
                    }
                    case "purchaseorder": {
                        results = getSearchResults(watermarkSearch, TransactionType.__purchaseOrder, wsClient, pageNumber);
                        break;
                    }
                    case "customerrefund": {
                        results = getSearchResults(watermarkSearch, TransactionType.__customerRefund, wsClient, pageNumber);
                        break;
                    }
                    case "file": {
                        FileSearchBasic search = new FileSearchBasic();
                        search.setModified(watermarkSearch);
                        SearchEnumMultiSelectField fileType = new SearchEnumMultiSelectField();
                        fileType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
                        fileType.setSearchValue(supportedDocumentTypes);
                        search.setFileType(fileType);
                        results = search(wsClient, search, pageNumber);
                        break;
                    }
                    case "assemblybuild": {
                      results = getSearchResults(watermarkSearch, TransactionType.__assemblyBuild, wsClient, pageNumber);
                      break;
                    }
                    case "assemblyunbuild": {
                      results = getSearchResults(watermarkSearch, TransactionType.__assemblyUnbuild, wsClient, pageNumber);
                      break;
                    }
                    case "bintransfer": {
                      results = getSearchResults(watermarkSearch, TransactionType.__binTransfer, wsClient, pageNumber);
                      break;
                    }
                    case "binworksheet": {
                      results = getSearchResults(watermarkSearch, TransactionType.__binWorksheet, wsClient, pageNumber);
                      break;
                    }
                    case "check": {
                      results = getSearchResults(watermarkSearch, TransactionType.__check, wsClient, pageNumber);
                      break;
                    }
                    case "deposit": {
                      results = getSearchResults(watermarkSearch, TransactionType.__deposit, wsClient, pageNumber);
                      break;
                    }
                    case "depositapplication": {
                      results = getSearchResults(watermarkSearch, TransactionType.__depositApplication, wsClient, pageNumber);
                      break;
                    }
                    case "expensereport": {
                      results = getSearchResults(watermarkSearch, TransactionType.__expenseReport, wsClient, pageNumber);
                      break;
                    }
                    case "intercompanyjournalentry": {
                      results = getSearchResults(watermarkSearch, "_interCompanyJournalEntry", wsClient, pageNumber);
                      break;
                    }
                    case "inventoryadjustment": {
                      results = getSearchResults(watermarkSearch, TransactionType.__inventoryAdjustment, wsClient, pageNumber);
                      break;
                    }
                    case "inventorycostrevaluation": {
                      results = getSearchResults(watermarkSearch, TransactionType.__inventoryCostRevaluation, wsClient, pageNumber);
                      break;
                    }
                    case "inventorytransfer": {
                      results = getSearchResults(watermarkSearch, TransactionType.__inventoryTransfer, wsClient, pageNumber);
                      break;
                    }
                    case "itemfulfillment": {
                      results = getSearchResults(watermarkSearch, TransactionType.__itemFulfillment, wsClient, pageNumber);
                      break;
                    }
                    case "itemreceipt": {
                      results = getSearchResults(watermarkSearch, TransactionType.__itemReceipt, wsClient, pageNumber);
                      break;
                    }
                    case "paycheckjournal": {
                      results = getSearchResults(watermarkSearch, TransactionType.__paycheckJournal, wsClient, pageNumber);
                      break;
                    }
                    case "returnauthorization": {
                      results = getSearchResults(watermarkSearch, TransactionType.__returnAuthorization, wsClient, pageNumber);
                      break;
                    }
                    case "statisticaljournalentry": {
                      TransactionSearchBasic search = new TransactionSearchBasic();
                      search.setLastModifiedDate(watermarkSearch);
                      search.setMemorized(new SearchBooleanField(false));
                      SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
                      transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
                      transactionLineType.setSearchValue(new String[]{TransactionType.__journal});
                      search.setType(transactionLineType);
                      search.setStatistical(new SearchBooleanField(true));
                      results =  search(wsClient, search, pageNumber);
                      break;
                    }
                    case "transferorder": {
                      results = getSearchResults(watermarkSearch, TransactionType.__transferOrder, wsClient, pageNumber);
                      break;
                    }
                    case "vendorbill": {
                      results = getSearchResults(watermarkSearch, TransactionType.__vendorBill, wsClient, pageNumber);
                      break;
                    }
                    case "vendorcredit": {
                      results = getSearchResults(watermarkSearch, TransactionType.__vendorCredit, wsClient, pageNumber);
                      break;
                    }
                    case "vendorpayment": {
                      results = getSearchResults(watermarkSearch, TransactionType.__vendorPayment, wsClient, pageNumber);
                      break;
                    }
                    case "vendorreturnauthorization": {
                      results = getSearchResults(watermarkSearch, TransactionType.__vendorReturnAuthorization, wsClient, pageNumber);
                      break;
                    }
                    case "workorder": {
                      results = getSearchResults(watermarkSearch, TransactionType.__workOrder, wsClient, pageNumber);
                      break;
                    }
                    //TODO Edit from here      --------------->
                    case "workorderclose": {
                      results = getSearchResults(watermarkSearch, TransactionType.__workOrderClose, wsClient, pageNumber);
                      break;
                    }
                    case "workordercompletion": {
                      results = getSearchResults(watermarkSearch, TransactionType.__workOrderCompletion, wsClient, pageNumber);
                      break;
                    }
                    case "workorderissue": {
                      results = getSearchResults(watermarkSearch, TransactionType.__workOrderIssue, wsClient, pageNumber);
                      break;
                    }
//                    case "cashsaletaxdetails": {
//                      results = getSearchResults(watermarkSearch, TransactionType.__cashsaletaxdetails, wsClient, pageNumber);
//                      break;
//                    }
                    default:
                        throw new NonRetriableException("unsupported_entity", String.format("Entity: %s", entityName), "unsupported_entity");

                }
            }
            if(end < start) {
                log.debug("Stopping search since end < start");
                break;
            }
            else if(results.internalIds.isEmpty()){
                log.info("No Records. Using {}, start is {} and end is {} and mid is {}", watermark.toString(), start, end, mid);
                start = mid;
            } else{
                //if we found a range smaller than 1 day within which we found the first record, return that as the start date
                if(mid - start <   24 * 60 * 60 *1000){
                    log.info("Found Records {}, start is {} and end is {} and mid is {}", watermark.toString(), start, end, mid);
                    return start;
                }
                log.info("Found Records.Range Still big {}, start is {} and end is {} and mid is {}", watermark.toString(), start, end, mid);
                end = mid;
            }
        }
        return start;
    }

    private SearchResults getSearchResults(SearchDateField watermarkSearch, String txnType, WsClient wsClient, int pageNumber) {
        TransactionSearchBasic search = new TransactionSearchBasic();
        search.setLastModifiedDate(watermarkSearch);
        search.setMemorized(new SearchBooleanField(false));
        SearchEnumMultiSelectField transactionLineType = new SearchEnumMultiSelectField();
        transactionLineType.setOperator(SearchEnumMultiSelectFieldOperator.anyOf);
        transactionLineType.setSearchValue(new String[]{txnType});
        search.setType(transactionLineType);
        return search(wsClient, search, pageNumber);
    }

    protected Calendar getCalendar(long start) {
        Instant startDate = Instant.ofEpochMilli(start);
        ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(startDate, ZoneOffset.UTC);
        return GregorianCalendar.from(zonedDateTime);
    }

    public List<EntityData> toFileEntityData(WsClient wsClient, SearchResults results) {
        List<EntityData> files = new ArrayList<>();
        results.getRecords().forEach(f -> {
            File file = (File) f;
            EntityData edFile = new EntityData("file");
            edFile.setId(file.getInternalId());
            edFile.setDeleted((Boolean) file.getIsInactive());
            edFile.setLastModified(((GregorianCalendar) file.getLastModifiedDate()).getTimeInMillis());
            edFile.setCreatedAt(((GregorianCalendar) file.getCreatedDate()).getTimeInMillis());
            edFile.addValue("folder", file.getFolder().getInternalId());
            edFile.addValue("fileType", file.getFileType());
            edFile.addValue("fileSize", file.getFileSize());
            edFile.addValue("attachFrom", file.getAttachFrom());
            edFile.addValue("url", file.getUrl());
            edFile.addValue("description", file.getDescription());
            edFile.addValue("name", file.getName());
            edFile.addValue("ownerId", file.getOwner());
            files.add(edFile);
        });
        return files;
    }
    
    public List<EntityData> toPaycheckJournalData(WsClient wsClient, SearchResults results) {
      List<EntityData> journals = new ArrayList<>();
      results.getRecords().forEach(j -> {
          PaycheckJournal journal = (PaycheckJournal) j;
          EntityData edJournal = new EntityData("paycheckjournal");
          edJournal.setId(journal.getInternalId());
          edJournal.setLastModified(((GregorianCalendar) journal.getLastModifiedDate()).getTimeInMillis());
          edJournal.setCreatedAt(((GregorianCalendar) journal.getCreatedDate()).getTimeInMillis());
          edJournal.addValue("account",journal.getAccount() == null ? null : journal.getAccount().getInternalId());
          edJournal.addValue("class",journal.get_class() == null ? null : journal.get_class().getInternalId());
          edJournal.addValue("companyContributionList",journal.getCompanyContributionList());
          edJournal.addValue("companyTaxList",journal.getCompanyTaxList());
          edJournal.addValue("customFieldList",journal.getCustomFieldList());
          edJournal.addValue("customForm",journal.getCustomForm() == null ? null : journal.getCustomForm().getInternalId());
          edJournal.addValue("deductionList",journal.getDeductionList());
          edJournal.addValue("department",journal.getDepartment() == null ? null : journal.getDepartment().getInternalId());
          edJournal.addValue("earningList",journal.getEarningList());
          edJournal.addValue("employee",journal.getEmployee() == null ? null : journal.getEmployee().getInternalId());
          edJournal.addValue("employeeTaxList",journal.getEmployeeTaxList());
          edJournal.addValue("exchangeRate",journal.getExchangeRate());
          edJournal.addValue("location",journal.getLocation() == null ? null : journal.getLocation().getInternalId());
          edJournal.addValue("postingPeriod",journal.getPostingPeriod() == null ? null : journal.getPostingPeriod().getInternalId());
          edJournal.addValue("subsidiary",journal.getSubsidiary() == null ? null : journal.getSubsidiary().getInternalId());
          edJournal.addValue("tranDate", journal.getTranDate() == null ? null : journal.getTranDate().getTimeInMillis());
          edJournal.addValue("tranId",journal.getTranId());
          journals.add(edJournal);
      });
      return journals;
    }
  
    public List<EntityData> toBinWorksheetData(WsClient wsClient, SearchResults results) {
      List<EntityData> journals = new ArrayList<>();
      results.getRecords().forEach(j -> {
          BinWorksheet journal = (BinWorksheet) j;
          EntityData edJournal = new EntityData("binworksheet");
          edJournal.setId(journal.getInternalId());
          edJournal.setLastModified(((GregorianCalendar) journal.getLastModifiedDate()).getTimeInMillis());
          edJournal.setCreatedAt(((GregorianCalendar) journal.getCreatedDate()).getTimeInMillis());
          edJournal.addValue("customFieldList",journal.getCustomFieldList());
          edJournal.addValue("itemList",journal.getItemList());
          edJournal.addValue("location",journal.getLocation() == null ? null : journal.getLocation().getInternalId());
          edJournal.addValue("memo",journal.getMemo());
          edJournal.addValue("tranDate", journal.getTranDate() == null ? null : journal.getTranDate().getTimeInMillis());
          edJournal.addValue("tranId",journal.getTranId());
          journals.add(edJournal);
          
      });
      return journals;
    }

    public InputStream getFileContents(WsClient wsClient, EntityData file) {
        RecordRef fileRef = new RecordRef();
        fileRef.setInternalId(file.getId());
        fileRef.setType(RecordType.file);
        ReadResponse readResponse;
        try {
            readResponse = (ReadResponse) wsClient.getPort().get(fileRef);
        } catch (Exception e) {
            ExceptionUtils.printRootCauseStackTrace(e);
            throw new RuntimeException("Failed to download file from netsuite due to " + e.getMessage(), e);
        }
        if (!readResponse.getStatus().isIsSuccess()) {
            if (readResponse.getStatus().getStatusDetail() != null) {
                throw new RuntimeException("Failed to download file from netsuite due to: " +
                    readResponse.getStatus().getStatusDetail()[0].getMessage());
            } else {
                throw new RuntimeException("Failed to download file from netsuite.");
            }
        }
        File f = (File) readResponse.getRecord();
        if (!Arrays.asList(supportedDocumentTypes).contains(f.getFileType().toString())) {
            throw new RuntimeException("Failed to download file from netsuite due to unsupported file type.");
        }
        return new ByteArrayInputStream(f.getContent());
    }

    public List<RecordRef> getTransactionSavedSearches(WsClient client) {
        client.setSearchPreferences(false, 500, true);
        List<RecordRef> recordRefs = new ArrayList<>();
        GetSavedSearchRecord record = new GetSavedSearchRecord();
        record.setSearchType(SearchRecordType.transaction);
        try {
            GetSavedSearchResult savedSearchResult = client.callGetSavedSearch(record);
            recordRefs.addAll(Arrays.asList(savedSearchResult.getRecordRefList().getRecordRef()));
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
        return recordRefs;
    }

    public List<RecordRef> getCustomSavedSearches(WsClient client) {
        client.setSearchPreferences(false, 500, true);
        List<RecordRef> recordRefs = new ArrayList<>();
        GetSavedSearchRecord record = new GetSavedSearchRecord();
        record.setSearchType(SearchRecordType.customRecord);
        try {
            GetSavedSearchResult savedSearchResult = client.callGetSavedSearch(record);
            recordRefs.addAll(Arrays.asList(savedSearchResult.getRecordRefList().getRecordRef()));
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
        return recordRefs;
    }

    public List<AttributeSchema> getSavedSearchAttributes(WsClient client, String internalId, boolean custom) {
        client.setPageSize(1000);
        Map<String, AttributeSchema> schemaMap = new HashMap<>();
        SearchRecord searchRecord;
        if (custom) {
            CustomRecordSearchAdvanced search = new CustomRecordSearchAdvanced();
            search.setSavedSearchId(internalId);
            searchRecord = search;
        } else {
            TransactionSearchAdvanced search = new TransactionSearchAdvanced();
            search.setSavedSearchId(internalId);
            searchRecord = search;
        }
        List<?> result = null;
        try {
            result = client.search(searchRecord);
            if(result != null && !result.isEmpty()) {
                for(int i = 0; i < result.size(); i++ ) {
                    Map<String, Object> map;
                    if(custom) {
                        map = extractCustomRecordRow(result, i);
                    } else {
                        map = extractTransactionRow(result, i);
                    }
                    map.forEach((key, value) -> {
                        if (!schemaMap.containsKey(key)) {
                            AttributeSchema schema = new AttributeSchema();
                            schema.setApiName(key);
                            schema.setDisplayName(key);
                            schema.setDataType(value != null ? value.getClass().getSimpleName().toLowerCase() : "string");
                            schemaMap.put(key, schema);
                        }
                    });
                }
            }
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
        schemaMap.put("lastModifiedDate", new AttributeSchema("lastModifiedDate", "datetime").setDisplayName("Last Modified Date").setWatermarkField(true));
        return new ArrayList<>(schemaMap.values());
    }

    private static Map<String, Object> extractTransactionRow(List<?> result, int i) {
        TransactionSearchRow transactionSearchRow = (TransactionSearchRow) result.get(i);
        TransactionSearchRowBasic transactionSearchRowBasic = transactionSearchRow.getBasic();
        Map<String, Object> map = new HashMap<>();
        Field[] fields = TransactionSearchRowBasic.class.getDeclaredFields();

        for (Field field : fields) {
            try {
                field.setAccessible(true);
                Object value = field.get(transactionSearchRowBasic);

                if (value instanceof SearchColumnCustomFieldList) {
                    extractCustomFieldList(map, (SearchColumnCustomFieldList) value);
                } else if (value != null && value.getClass().isArray()) {
                    Object[] array = (Object[]) value;
                    if (array.length > 0) {
                        Field searchValue = array[0].getClass().getDeclaredField("searchValue");
                        searchValue.setAccessible(true);
                        Object searchValueField = searchValue.get(array[0]);
                        map.put(field.getName(), formatSearchValue(searchValueField));
                    }
                }
            } catch (Exception e) {
                log.error("Error accessing fields: " + e.getMessage());
            }
        }
        return map;
    }

    private static Map<String, Object> extractCustomRecordRow(List<?> result, int i) {
        CustomRecordSearchRow customRecordSearchRow = (CustomRecordSearchRow) result.get(i);
        CustomRecordSearchRowBasic customRecordSearchRowBasic = customRecordSearchRow.getBasic();
        Map<String, Object> map = new HashMap<>();
        Field[] fields = CustomRecordSearchRowBasic.class.getDeclaredFields();

        for (Field field : fields) {
            try {
                field.setAccessible(true);
                Object value = field.get(customRecordSearchRowBasic);

                if (value instanceof SearchColumnCustomFieldList) {
                    extractCustomFieldList(map, (SearchColumnCustomFieldList) value);
                } else if (value != null && value.getClass().isArray()) {
                    Object[] array = (Object[]) value;
                    if (array.length > 0) {
                        Field searchValue = array[0].getClass().getDeclaredField("searchValue");
                        searchValue.setAccessible(true);
                        Object searchValueField = searchValue.get(array[0]);
                        map.put(field.getName(), formatSearchValue(searchValueField));
                    }
                }
            } catch (Exception e) {
                log.error("Error accessing fields: " + e.getMessage());
            }
        }

        return map;
    }

    private static void extractCustomFieldList(Map<String, Object> map, SearchColumnCustomFieldList customFieldList) {
        for (SearchColumnCustomField customField : customFieldList.getCustomField()) {
            try {
                Field searchValueField = customField.getClass().getDeclaredField("searchValue");
                searchValueField.setAccessible(true);
                Object searchValue = searchValueField.get(customField);
                map.put(customField.getScriptId(), formatSearchValue(searchValue));
            } catch (NoSuchFieldException | IllegalAccessException e) {
                System.out.println("Error accessing custom fields: " + e.getMessage());
            }
        }
    }

    private static Object formatSearchValue(Object searchValue) {
        if (searchValue instanceof GregorianCalendar) {
            return ((GregorianCalendar) searchValue).getTime();
        }
        if (searchValue instanceof RecordRef) {
            return ((RecordRef) searchValue).getInternalId();
        }
        if (searchValue instanceof ListOrRecordRef) {
            return ((ListOrRecordRef) searchValue).getInternalId();
        }
        return searchValue;
    }

    public DataWithCursor getSavedSearchRecords(WsClient client, String apiName, SyncRequest request, String cursor) {
        client.setPageSize(1000);
        List<EntityData> entityDataList = new ArrayList<>();
        String internalId = apiName.substring(SAVED_SEARCH_PREFIX.length());
        SearchRecord searchRecord;
        boolean custom = request.getEntitySchema().isCustom();
        if (custom) {
            CustomRecordSearchAdvanced search = new CustomRecordSearchAdvanced();
            search.setSavedSearchId(internalId);
            searchRecord = search;
        } else {
            TransactionSearchAdvanced search = new TransactionSearchAdvanced();
            search.setSavedSearchId(internalId);
            searchRecord = search;
        }
        SearchResult searchResult = null;
        String nextCursor = "";
        int pageNumber = 1;
        try {
            if(StringUtils.isBlank(cursor)) {
                searchResult = client.callSearch(searchRecord);
            } else {
                String[] split = cursor.split("#");
                String searchId = split[0];
                pageNumber = Integer.parseInt(split[1]);
                searchResult = client.callSearchMoreWithId(searchId, pageNumber);
            }
            var result = Utils.getSearchResults(searchResult);
            if(result != null && !result.isEmpty()) {
                extractResults(apiName, request, result, entityDataList);

                if(pageNumber <= searchResult.getTotalPages()) {
                    pageNumber++;
                    nextCursor = searchResult.getSearchId() + "#" + pageNumber;
                }
            }
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
        return new DataWithCursor(cursor, nextCursor, entityDataList);
    }

    public List<EntityData> getAllSavedSearchRecords(WsClient client, String apiName, SyncRequest request) {
        List<EntityData> entityDataList = new ArrayList<>();
        SearchRecord searchRecord;
        if(request.getEntitySchema().isCustom()) {
            CustomRecordSearchAdvanced search = new CustomRecordSearchAdvanced();
            search.setSavedSearchId(apiName.substring(SAVED_SEARCH_PREFIX.length()));
            searchRecord = search;
        } else {
            TransactionSearchAdvanced search = new TransactionSearchAdvanced();
            search.setSavedSearchId(apiName.substring(SAVED_SEARCH_PREFIX.length()));
            searchRecord = search;
        }

        try {
            var results = client.searchAll(searchRecord);
            extractResults(apiName, request, results, entityDataList);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
        return entityDataList;
    }

    private static void extractResults(String apiName, SyncRequest request, List<?> result, List<EntityData> entityDataList) {
        String idField = request.getEntitySchema().getIdField().getApiName();
        for(int i = 0; i < result.size(); i++) {
            Map<String, Object> map;
            if(request.getEntitySchema().isCustom()) {
                map = extractCustomRecordRow(result, i);
            } else {
                map = extractTransactionRow(result, i);
            }
            EntityData record = new EntityData(apiName);
            record.setValues(map);
            record.setLastModified(request.getWatermark() != null ? request.getWatermark().getEnd() : Instant.now().toEpochMilli());
            record.setId((String) map.get(idField));
            entityDataList.add(record);
        };
    }

}

@Data
@Accessors(chain = true)
class SearchResults{

    public static SearchResults emptyResults(){
        return new SearchResults(List.of(),0,0,List.of());
    }
    public SearchResults(List<String> internalIds, int currentPage,int totalRecords, List<Record> records) {
        this.internalIds = internalIds;
        this.currentPage = currentPage;
        this.totalRecords = totalRecords;
        this.records = records;
    }

    List<String> internalIds;
    int currentPage;

    int totalRecords;
    List<Record> records;
    //parent record id to reference mapping. Reference is a map of recordType and list of records of that type)
    Map<String, Map<String,List<Record>>> references = new HashMap<>();

    public List<Record> getReferences(String id, String refType){
        return references.containsKey(id) && references.get(id).containsKey(refType) ? references.get(id).get(refType):List.of();
    }

    public void setReferences(String id, String refType, List<Record> refs){
        Map<String, List<Record>> existingRefs = references.getOrDefault(id, new HashMap<>());
        existingRefs.put(refType, refs);
        references.put(id, existingRefs);
    }
    public boolean hasMore(int pageSize){
        return currentPage * pageSize < totalRecords;
    }
}
