package com.syncari.connector.sap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.connector.*;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.config.JsonParserConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.data.iterator.DefaultDataOffsetIterator;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import com.syncari.connector.service.def.OauthAuthenticationService;
import com.syncari.connector.service.def.SynapseInfoService;
import com.syncari.utils.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.jooq.lambda.function.Function3;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.events.*;
import java.io.ByteArrayInputStream;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import static java.lang.String.format;

@Slf4j
@Component(Constants.SAP)
public class SapService implements OauthAuthenticationService, CommonDataService, MetadataService, SynapseInfoService {
    @Autowired
    ObjectMapper mapper;
    @Autowired
    DateUtil dateUtil;

    private static final String BASE_URL = "/sap/c4c/odata/v1/c4codataapi/";
    private static final String GET_BY_WATERMARK = "?$filter=EntityLastChangedOn gt datetimeoffset'%s' & EntityLastChangedOn le datetimeoffset'%s' &$orderby=EntityLastChangedOn";
    private static final int API_MAX_PAGESIZE = 200;

    // CRUD APIs
    private static final String CRUD_ID_URL = "%s/sap/c4c/odata/v1/c4codataapi/%s?$filter=ObjectID eq '%s' &$format=json";
    private static final String CRUD_URL = "%s/sap/c4c/odata/v1/c4codataapi/%s";


    private static final String DESCRIBE_URL = "%s/sap/c4c/odata/v1/c4codataapi/$metadata";
    private static final String TEST_URL = "%s/sap/c4c/odata/v1/c4codataapi/BusinessAttributeCollection?$top=1&$format=json";

    private static final String dateFormat = "yyyy-MM-dd'T'HH:mm:ss.000'Z'";

    public static final List<String> UNSUPPORTED_DELETES = List.of("ContactCollection", "CorporateAccountCollection", "EmployeeCollection", "TargetGroupCollection", "IndividualCustomerCollection");
    public static final List<String> UNSUPPORTED_CREATES = List.of("DealRegistrationCollection");
    public static final List<String> ACCOUNT_REQ_UI_FIELDS = List.of("Name", "RoleCode", "CountryCode", "CountryCodeText");
    public static final List<String> LEAD_REQ_UI_FIELDS = List.of("ContactLastName", "Company");


    private String getAuthType(ConnectorInfo config) {
        return config.getMetaConfig().getOrDefault("authType", AuthType.UserPassword).toString();
    }

    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        return List.of(ConnectorHelper.getUserPwd());
    }

    @Override
    public List<AuthField> getConfigureFields() {
        AuthField endpointURL = ConnectorHelper.getEndpointField();
        return List.of(endpointURL, ConnectorHelper.getSupportedAuthPicker());
    }

    @Override
    public String getCategory() {
        return "CRM";
    }

    @Override
    public String getName() {
        return Constants.SAP;
    }

    public UIMetadata getUIMetadata() {
        return new UIMetadata().setIconPath("/assets/icons/logos/sap.svg")
                .setDisplayName("SAP")
                .setBackgroundColor("#F0FBFF")
                .setHelpUrl(helpArticlesBaseUrl + SYNAPSE_COMING_SOON_ARTICLE);
    }

    @Override
    public String getCapabilitiesArticleId() {
        return "";
    }

    @Override
    public TestConnectionResponse testConnection(ConnectorInfo config, List<String> entityNames) {
        TestConnectionResponse response = new TestConnectionResponse();
        try {
            SapRestClient sapRestClient = new SapRestClient();
            HttpHeaders headers = sapRestClient.getHeaders(config.getAuthConfig());
            response.setAuthConfig(config.getAuthConfig());
            ResponseEntity<String> data = sapRestClient.getResponse(headers, format(TEST_URL, config.getEndpoint()), config.getAuthConfig());

            if(data.getHeaders().get("content-type").toString().contains(MediaType.TEXT_HTML.toString()))
                throw new Exception("SAP authentication error");
        } catch (Exception e) {
            log.error("SAP testConnection failed due to " + e.getMessage(), e);
            handleAuthenticationErrorMessage(response, new Exception(StringUtils.substringBefore(e.getMessage(), ";")));
        }

        return response;
    }

    public SapRestClient getClient(AuthConfig config) {
        SapRestClient sapRestClient = new SapRestClient(getSingleJsonConfig(), mapper);
        sapRestClient.getHeaders(config);
        return sapRestClient;
    }


    private JsonParserConfig getSingleJsonConfig() {
        return new JsonParserConfig(null, null, null, "ObjectID", true, null);
    }

    @Override
    public AuthConfig refreshToken(ConnectorInfo connector) {
        throw new RuntimeException("SAP does not support refresh token");

    }

    @Override
    public Optional<EntitySchema> describe(DescribeRequest request) {
        List<EntitySchema> entitySchemas = describeAll(new DescribeAllRequest(request.getConnector(), List.of(request.getEntity())));
        if (entitySchemas.isEmpty()) return Optional.empty();
        return Optional.of(entitySchemas.get(0));
    }

    @Override
    public List<EntitySchema> describeAll(DescribeAllRequest request) {
        List<EntitySchema> entitySchemas = new ArrayList<>();

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Type", "application/xml");

            String describerUrl = format(DESCRIBE_URL, request.getConnector().getEndpoint());
            if (!request.getEntities().isEmpty())
                describerUrl = StringUtils.join(format(DESCRIBE_URL, request.getConnector().getEndpoint()), "?$filter=", StringUtils.replace(request.getEntities().get(0).toString(), "Collection", ""));

            ResponseEntity<String> data = getClient(request.getConnector().getAuthConfig())
                    .getResponse(describerUrl, request.getConnector().getAuthConfig());

            // the metadata comes back as xml. Because the response is large, we are using a stax parser to manage memory
            byte[] byteArray = data.getBody().getBytes("UTF-8");
            ByteArrayInputStream inputStream = new ByteArrayInputStream(byteArray);
            XMLInputFactory inputFactory = XMLInputFactory.newInstance();
            XMLEventReader eventReader = inputFactory.createXMLEventReader(inputStream);

            boolean bEntityType = false;
            String entityName = "";

            EntitySchema entitySchema = new EntitySchema();

            while(eventReader.hasNext()) {
                XMLEvent event = eventReader.nextEvent();

                switch(event.getEventType()) {

                    case XMLStreamConstants.START_ELEMENT:
                        StartElement startElement = event.asStartElement();
                        String qName = startElement.getName().getLocalPart();

                        // entity data starts with the EntityType elelment
                        if (qName.equalsIgnoreCase("EntityType")) {
                            entitySchema = new EntitySchema();

                                Iterator<Attribute> attributes = startElement.getAttributes();
                                entityName = attributes.next().getValue();

                            if (request.getEntities().isEmpty() || (!request.getEntities().isEmpty() && request.getEntities().contains(entityName)) ||
                                    (!request.getEntities().isEmpty() && request.getEntities().contains(entityName+"Collection"))) {
                                entitySchema.setDisplayName(entityName);
                                // entities are appended with Collection but in the metadata it is not there so we add it
                                entitySchema.setApiName(StringUtils.join(entityName, "Collection"));
                                entitySchemas.add(entitySchema);
                            }

                            // The Property element are the attributes for the EntityType
                        } else if (qName.equalsIgnoreCase("Property")) {

                            if (request.getEntities().isEmpty() || (!request.getEntities().isEmpty() && request.getEntities().contains(entityName)) ||
                                    (!request.getEntities().isEmpty() && request.getEntities().contains(entityName+"Collection"))) {

                                Iterator<Attribute> attributes = startElement.getAttributes();

                                AttributeSchema as = new AttributeSchema();
                                while (attributes.hasNext()) {
                                    as = getAttributes(attributes.next(), as, entityName);
                                }

                                if (as.getApiName() != null) {
                                    if (!(as.getApiName().equals("languageCode") && entityName.equals("MemoActivityText")))
                                        entitySchema.addField(as);
                                }
                            }
                        }
                        break;

                    case XMLStreamConstants.CHARACTERS:
                        if(bEntityType) {
                            bEntityType = false;
                        }
                        break;

                    case XMLStreamConstants.END_ELEMENT:
                        EndElement endElement = event.asEndElement();
                        break;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        // remove entities with no watermarks
        List<EntitySchema> validEntities = new ArrayList<>();
        List<String> validEntityNames = new ArrayList<>();
        for (EntitySchema entitySchema : entitySchemas) {
            if(entitySchema.hasWatermarkField()) {
                validEntities.add(entitySchema);
                validEntityNames.add(entitySchema.getApiName());
            }
        }

        // remove references to entities with no watermarks
        for (EntitySchema refEntity : validEntities) {
            for (AttributeSchema attributeSchema : refEntity.getAttributes()) {
                if (attributeSchema.getReferenceTo() != null) {
                    if(!validEntityNames.contains(attributeSchema.getReferenceTo())) {
                        attributeSchema.setReferenceTo(null);
                        attributeSchema.setReferenceTargetField(null);
                    } else {
                        attributeSchema.setDataType("reference");
                    }
                }
            }
        }

        return validEntities;
    }

    private AttributeSchema getAttributes(Attribute a, AttributeSchema as, String entityName) {
        String value = "";
        // the int datatype is appended with .<size>. We remove it to process the int datatype
        if (a.getName().toString().equals("Type")) {
            value = StringUtils.substringAfter(a.getValue().toString(), ".");
            if (value.startsWith("Int"))
                value = "Int";
        }

        switch (a.getName().toString()) {
            case "Name":
                as.setApiName(a.getValue());
                as.setDisplayName(a.getValue());
                break;
            case "Nullable":
                if(!as.isWatermarkField())
                    as.setNillable(BooleanUtils.toBoolean(a.getValue()));
                break;
            case "MaxLength":
                if (!a.getValue().equals("Max"))
                    as.setLength(Integer.parseInt(a.getValue()));
                break;
            case "Precision":
                as.setPrecision(Integer.parseInt(a.getValue()));
                break;
            case "{http://www.sap.com/Protocols/SAPData}creatable":
                as.setInitializable(BooleanUtils.toBoolean(a.getValue()));
                break;
            case "{http://www.sap.com/Protocols/SAPData}updatable":
                as.setUpdateable(BooleanUtils.toBoolean(a.getValue()));
                break;
            case "{http://www.sap.com/Protocols/C4CData}value-help":
                as.setReferenceTo(a.getValue());
                as.setReferenceTargetField("ObjectID");
                break;
            case "Type":
                switch (value) {
                    case "DateTimeOffset":
                        as.setDataType("timestamp");
                        break;
                    case "DateTime":
                        as.setDataType("datetime");
                        break;
                    case "Guid":
                        as.setDataType("string");
                        break;
                    case "Binary":
                        as.setDataType("string");
                        break;
                    default:
                        as.setDataType(value.toLowerCase());
                }
        }

        switch (a.getValue()) {
            case "ObjectID":
                as.setUnique(true);
                as.setIdField(true);
                break;
            case "EntityLastChangedOn":
                as.setWatermarkField(true);
                as.setNillable(false);
                break;
            case "LastChangedOn":
                as.setWatermarkField(true);
                as.setNillable(false);
                break;
        }

        // required fields in the metadata file are incomplete. Hard coding fields here.
        if(entityName.equals("CorporateAccount") && ACCOUNT_REQ_UI_FIELDS.contains(as.getApiName()))
            as.setNillable(false);

        // required fields in the metadata file are incomplete. Hard coding fields here.
        if(entityName.equals("Lead") && LEAD_REQ_UI_FIELDS.contains(as.getApiName()))
            as.setNillable(false);

        as.setCreateOnly(as.isInitializable() && !as.isUpdateable());

        return as;
    }

    @Override
    public FetchResponse getByWatermark(SyncRequest request) {
        Function3<WatermarkInfo, Integer, Long, DataWithOffset> generator = (wm, pageSize, offset) -> {
            ConnectorInfo connectorInfo = request.getConnector();

            String offsetString = (request.getWatermark().getOffset() == 0) ? "" : StringUtils.join("&$skip=", request.getWatermark().getOffset());

            if (null != request.getEntitySchema().getWatermarkField()) {
                String start = dateUtil.format(request.getWatermark().getStart(), dateFormat);
                String end = dateUtil.format(request.getWatermark().getEnd(), dateFormat);

                String url = StringUtils.join(request.getConnector().getEndpoint(), BASE_URL, request.getEntityName(),
                        format(GET_BY_WATERMARK, start, end), "&$top=", pageSize, offsetString, "&$format=json");

                log.info("Url to fetch data by watermark is {}", url);
                return getClient(connectorInfo.getAuthConfig()).getDataWithOffset(url, offset, request);
            } else {
                String url = StringUtils.join(request.getConnector().getEndpoint(), BASE_URL, request.getEntityName(),
                        "&$top=", pageSize, offsetString, "&$format=json");

                log.info("Url to fetch data by watermark is {}", url);
                return getClient(connectorInfo.getAuthConfig()).getDataWithOffset(url, offset, request);
            }
        };

        int pgSize = (request.getPageSize() <= 0) ? API_MAX_PAGESIZE : request.getPageSize();

        DefaultDataOffsetIterator iterator = new DefaultDataOffsetIterator(request.getWatermark(),
                request.getWatermark().getOffset(), generator, new ArrayList<>(),
                request.getEntitySchema().getWatermarkField(), pgSize, request.getWatermark().getLimit());
        return new FetchResponse(request.getWatermark(), iterator);
    }

    private DataWithOffset get(String url, long offset, SyncRequest request, boolean isSingleObject) {
        return getClient(request.getConnector().getAuthConfig()).getData(url, offset, request, isSingleObject);
    }

    @Override
    public List<EntityData> getByIds(SyncRequest request) {
        List<EntityData> data = new ArrayList<>();
        request.getIds().forEach(id -> {
            try {
                String url = String.format(CRUD_ID_URL, getHost(request.getConnector()), request.getEntityName(), id);
                data.addAll(get(url, 0l, request, true).getData());
            } catch (NonRetriableException e) {
                if (e.getMessage().contains("Record not found:")) {
                    log.error("Record with id {} not found", id, e);
                } else {
                    throw e;
                }
            }
        });
        return data;
    }

    @Override
    public long getFirstCreatedTime(SyncRequest request) {
        return 0L;
    }

    @Override
    public SyncResponse create(SyncRequest request) {
        if(UNSUPPORTED_CREATES.contains(request.getEntityName()))
            throw new RuntimeException(StringUtils.join("Create on "+request.getEntityName()+" are not supported in SAP"));

        String url = String.format(CRUD_URL, getHost(request.getConnector()), request.getEntityName());
        return getClient(request.getConnector().getAuthConfig()).upsertRecords(url, HttpMethod.POST, transformData(request));
    }

    @Override
    public SyncResponse update(SyncRequest request) {
        String url = String.format(CRUD_URL, getHost(request.getConnector()), request.getEntityName());
        return getClient(request.getConnector().getAuthConfig()).upsertRecords(url, HttpMethod.PATCH, transformData(request));
    }

    @Override
    public SyncResponse delete(SyncRequest request) {
        if(UNSUPPORTED_DELETES.contains(request.getEntityName()))
            throw new RuntimeException(StringUtils.join("Deletes on "+request.getEntityName()+" are not supported in SAP"));
        String url = String.format(CRUD_URL, getHost(request.getConnector()), request.getEntityName());
        return getClient(request.getConnector().getAuthConfig()).deleteRecords(url, request);
    }

    private Object getHost(ConnectorInfo config) {
        return config.getEndpoint();
    }



    @Override
    public AttributeSchema createField(CreateFieldRequest request) {
        throw new RuntimeException("SAP does not support create field");
    }

    @Override
    public void deleteField(DeleteFieldRequest request) {
        throw new RuntimeException("SAP does not support delete field");
    }

    @Override
    public EntitySchema createObject(CreateObjectRequest request) {
        throw new RuntimeException("createObject not supported in SAP yet");
    }

    @Override
    public Map<String, String> getEntityMappings() {
        return new HashMap<>();
    }

    @Override
    public Map<String, String> getAttributeMappings(String entityApiName) {
        return Map.of();
    }

    @Override
    public AuthConfig getAccessToken(OAuthRequest oAuthRequest) {
        throw new RuntimeException("OAuth Implicit Flow not supported by SAP");
    }

    @Override
    public String getOAuthUri(ConnectorInfo connector) {
        throw new RuntimeException("OAuth Implicit Flow not supported by SAP");
    }

    @Override
    public String getAuthHost(AuthConfig config) {
        return config.getEndpoint();
    }

    private SyncRequest transformData(SyncRequest syncRequest) {

        var schema = syncRequest.getEntitySchema();
        var data = syncRequest.getData().get(syncRequest.getConnector().getId());
        data.forEach(d -> d.getValues().forEach((k, v) -> {
            var field = schema.getField(k);
            field.ifPresent( f -> {
                if(f.getDataType().equals("date") && v instanceof Date) {
                    LocalDate locateDate = ((Date)v).toInstant().atZone(ZoneId.of("UTC")).toLocalDate();
                    d.getValues().put(k, locateDate.format(DateTimeFormatter.ISO_LOCAL_DATE));
                }
            });
        }));
        return syncRequest;
    }
}
