package com.syncari.connector.sap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.JsonPathException;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.ReadContext;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.config.JsonParserConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.impartner.ImpartnerService;
import com.syncari.connector.rest.SyncariEntityDataRestClient;
import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONArray;
import net.snowflake.client.jdbc.ErrorCode;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

import static com.syncari.connector.ConnectorHelper.withBackoffAndErrorHandling;
import static java.time.ZoneOffset.UTC;

@Slf4j
public class SapRestClient  extends SyncariEntityDataRestClient {

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(DateTimeFormatter.ISO_OFFSET_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));

    private static final String X_CSRF_TOKEN_URL = "/sap/c4c/odata/v1/c4codataapi/";

    public SapRestClient(JsonParserConfig parserConfig){
        super(parserConfig);
    }

    public SapRestClient(JsonParserConfig parserConfig, ObjectMapper objectMapper) {
        super(parserConfig, objectMapper);
    }

    public SapRestClient() { }

    public HttpHeaders getHeaders(AuthConfig authConf) {
        HttpHeaders headers = new HttpHeaders();
        HttpHeaders authHeaders = getAuthHeaders(authConf);
        String authString = authConf.getUserName() + ":" + authConf.getPassword();
        byte[] authEncBytes = Base64.encodeBase64(authString.getBytes());
        String authStringEnc = new String(authEncBytes);
        headers.set("Authorization", "Basic " + authStringEnc);
        headers.addAll(authHeaders);
        return headers;
    }

    public HttpHeaders getWriteHeaders(AuthConfig authConf) {
        HttpHeaders headers = new HttpHeaders();

        // set header data for the fetch
        headers.add("Content-Type", "application/json");
        headers.add("x-csrf-token", "fetch");
        String authString = authConf.getUserName() + ":" + authConf.getPassword();
        byte[] authEncBytes = Base64.encodeBase64(authString.getBytes());
        String authStringEnc = new String(authEncBytes);
        headers.add("Authorization", "Basic " + authStringEnc);

        // call to get the x-crsf-token and session
        ResponseEntity<String> data = getResponse(headers, StringUtils.join(authConf.getEndpoint(), X_CSRF_TOKEN_URL), authConf);

        // remove the x-csrf-token for fetch and allow the value from the header to be set
        headers.remove("x-csrf-token");

        // parse header to get values for x-crsf-token and cookie session
        String cookies = data.getHeaders().get("set-cookie").toString();
        String session = cookies.substring(cookies.indexOf("SAP_SESSION"));
        session = session.substring(0, session.indexOf(";"));
        String csrf = data.getHeaders().get("x-csrf-token").toString();
        csrf = StringUtils.substringBetween(csrf, "[", "]");

        // update the header
        headers.add("x-csrf-token", csrf);
        headers.add("cookie", session);

        return headers;
    }

    public DataWithOffset getDataWithOffset(String url, Long prevOffset, SyncRequest request) {
        HttpHeaders headers = getHeaders(request.getConnector().getAuthConfig());
        ResponseEntity<String> response = getResponse(headers, url, request.getConnector().getAuthConfig(), null);
        ReadContext ctx = JsonPath.parse(response.getBody());
        List<EntityData> results = getResults(ctx, request);
       if (results.isEmpty()) {
            return DataWithOffset.emptyWithOffsets(prevOffset, prevOffset);
        }
        List<String> errors = new ArrayList<>();
        request.getWatermark().setOffset(prevOffset + results.size());

        return new DataWithOffset(prevOffset, prevOffset + results.size(), results, errors);
    }

    private List<EntityData> getResults(ReadContext ctx, SyncRequest request) {
        List<EntityData> results = new ArrayList<>();
        EntitySchema schema = request.getEntitySchema();
        JSONArray data = ctx.read("$.d.results");
        for(int i = 0; i < data.size(); i++) {
            String pathPrefix = String.format("$.d.results[%d].", i);
            results.add(parseJSON(ctx, schema, pathPrefix, Optional.of(request)));
        }
        return results;
    }

    public EntityData parseJSON(ReadContext ctx, EntitySchema schema, String pathPrefix, Optional<SyncRequest> request) {
        EntityData ed = new EntityData(schema.getApiName());
        schema.getAttributes().forEach(attr -> {
            String path = pathPrefix + attr.getApiName();
            try {
                Object value = ctx.read(path);
                if(!attr.isReference()) {
                    ed.addValue(attr.getApiName(), value);
                    if (attr.isIdField()) {
                        ed.setId((String) value);
                    }
                } else if (value instanceof Map && attr.isReference()) {
                    Map<String, Object> valueMap = (Map<String, Object>) value;
                    ed.addValue(attr.getApiName(), valueMap.get("ObjectID"));
                } else {
                    ed.addValue(attr.getApiName(),value);
                }
            } catch (PathNotFoundException e) {
                // attribute not included in response
                log.debug("Attribute {} not found in response json", attr);
            }
        });
        handleNonWMEntities(ed, request);
        return ed;
    }

    private void handleNonWMEntities(EntityData ed, Optional<SyncRequest> request) {
        if(request.isPresent() && request.get().getWatermark() != null) {
            if (ed.getLastModified() == 0) {
                ed.setLastModified(request.get().getWatermark().getEnd());
            }
            if (ed.getCreatedAt() == 0) {
                ed.setCreatedAt(request.get().getWatermark().getEnd());
                ed.addValue("created", request.get().getWatermark().getEnd());
            }
        }
    }

    public DataWithOffset getData(String url, Long prevOffset, SyncRequest request, boolean isSingleObject) {
        ResponseEntity<String> response = getResponse(url, request.getConnector().getAuthConfig());
        return processResponse(response, prevOffset, request);
    }

    public SyncResponse upsertRecords(String url, HttpMethod method, SyncRequest request) {
        SyncResponse response = new SyncResponse();
        for (EntityData ed : request.getData().get(request.getConnector().getId())) {
            try {
                response = doPostRecords(url, method, ed, request, response);
            } catch (Exception e) {
                Result errResult = new Result(false, ed.getId(), ed.getSyncariEntityId());
                response.getResults().add(errResult.addError(e.getMessage()));
            }
        }
        return response;
    }

    private SyncResponse doPostRecords(String url, HttpMethod method, EntityData ed, SyncRequest request, SyncResponse response) {
        Map<String, Object> edMap = ed.getValues();
        String json = "";
        try {
            json = objectMapper.writeValueAsString(edMap);
        } catch (JsonProcessingException ex) {
            throw new NonRetriableException(ErrorCode.INVALID_PARAMETER_VALUE.toString(),
                    String.format("Failed to serialize payload for %s operation. Payload: %s ", method, edMap),
                    ErrorCode.INVALID_PARAMETER_VALUE.toString());
        }
        ResponseEntity<String> cuResponse = new ResponseEntity<String>(HttpStatus.INTERNAL_SERVER_ERROR);
        try {
            if (HttpMethod.POST == method || HttpMethod.PATCH == method) {
                HttpHeaders headers = getWriteHeaders(request.getConnector().getAuthConfig());
                if (HttpMethod.POST == method) {
                    cuResponse = postRaw(headers, url, json, request.getConnector().getAuthConfig());
                    ed.setId(parseCreateResponseId(cuResponse));
                }
                if (HttpMethod.PATCH == method)
                    cuResponse = patch(headers, url + "('" + ed.getId()+"')", json, request.getConnector().getAuthConfig());
                response.getResults().add(new Result(true, ed.getId(), ed.getSyncariEntityId()));
                if (cuResponse.getStatusCode().isError()) {
                    Result errResult = new Result(false, ed.getId(), ed.getSyncariEntityId());
                    if (!cuResponse.getHeaders().get("content-type").toString().contains(MediaType.APPLICATION_XML.toString())) {
                        String errorMessage = getXmlErrorMessage(cuResponse.getBody());
                        response.getResults().add(errResult.addError(errorMessage));
                    } else {
                        response.getResults().add(errResult.addError("SAP Internal Error"));
                    }
                }
            } else {
                throw new NonRetriableException(ErrorCodes.BAD_ENDPOINT.toString(), String.format("Unsupported httpmethod %s", method),
                        ErrorCodes.BAD_ENDPOINT.toString());
            }
        } catch (Exception e) {
            Result errResult = new Result(false, ed.getId(), ed.getSyncariEntityId());
            String errorMessage = getErrorMessage(e.getMessage());
            response.getResults().add(errResult.addError(errorMessage));
        }
        return response;
    }

    public DataWithOffset processResponse(ResponseEntity<String> response, Long prevOffset, SyncRequest request) {
        HttpHeaders headers = getHeaders(request.getConnector().getAuthConfig());
        ReadContext ctx = JsonPath.parse(response.getBody());
        List<EntityData> results = getResults(ctx, request);

        List<String> errors = new ArrayList<>();
        return new DataWithOffset(prevOffset, prevOffset + results.size(), results, errors);
    }

    public SyncResponse deleteRecords(String crudURL, SyncRequest request) {
        SyncResponse response = new SyncResponse();
        HttpHeaders headers = getWriteHeaders(request.getConnector().getAuthConfig());
        request.getData().get(request.getConnector().getId()).forEach(ed -> {
            String url = StringUtils.join(crudURL , "('" , ed.getId(),"')");
            try {
                ResponseEntity<String> dResp = delete(headers, url, request.getConnector().getAuthConfig());
                if (dResp.getStatusCode().equals("204")) {
                    response.getResults().add(new Result(true, ed.getId(), ed.getSyncariEntityId()));
                } else {
                    Result errResult = new Result(false, ed.getId(), ed.getSyncariEntityId());
                    String errorMessage = getXmlErrorMessage(dResp.getBody());
                    response.getResults().add(errResult.addError(errorMessage));
                }
            } catch (Exception e) {
                Result errResult = new Result(false, ed.getId(), ed.getSyncariEntityId());
                response.getResults().add(errResult.addError(getErrorMessage(e.getMessage())));
            }
        });
        return response;
    }

    private String getXmlErrorMessage(String xmlString) {
        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            InputSource src = new InputSource();
            src.setCharacterStream(new StringReader(xmlString));

            Document doc = builder.parse(src);
            return doc.getElementsByTagName("message").item(0).getTextContent();
        } catch (Exception e) {
            return "SAP Internal Error";
        }
    }

    private String getErrorMessage(String error) {
        try {
            error = error.substring(error.indexOf("<message xml:lang=\"en\">") + 23);
            error = error.substring(0, error.indexOf("</message>"));

            return error;
        } catch (Exception e) {
            return "SAP Internal Error";
        }
    }


    private ZonedDateTime convertToZonedDateTime(Object date, String zoneId) {
        if (date == null) return null;
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                LocalDateTime ltc = LocalDateTime.parse(date.toString(), format);
                return ltc.atZone(StringUtils.isEmpty(zoneId) ? ZoneId.systemDefault() : ZoneId.of(zoneId));
            } catch (DateTimeParseException ex) {
            }
        }
        log.error("Could not parse date {}", date);
        throw new NonRetriableException(ErrorCodes.UNKNOWN_ERROR, "Could not parse date " + date, ErrorCodes.UNKNOWN_ERROR.toString());
    }

    private String parseCreateResponseId(ResponseEntity cuResponse) {
        String objectId = null;
        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(cuResponse.getBody().toString())));
            XPath xPath = XPathFactory.newInstance().newXPath();
            String expression = "/entry/content/properties/ObjectID";
            NodeList nodeListXP = (NodeList) xPath.compile(expression).evaluate(doc, XPathConstants.NODESET);
            for (int i = 0; i < nodeListXP.getLength(); i++) {
                Node node = nodeListXP.item(i);
                objectId = node.getTextContent();
            }
        } catch (Exception e) {
        }
        return objectId;
    }
}
