package com.syncari.connector.intacct;

import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.rest.SyncariEntityDataRestClient;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.security.InterfaceTypePermission;
import com.thoughtworks.xstream.security.NoTypePermission;
import com.thoughtworks.xstream.security.NullPermission;
import com.thoughtworks.xstream.security.PrimitiveTypePermission;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Slf4j
public class IntacctClient extends SyncariEntityDataRestClient {

    private static XStream requestMarshaller = new XStream();

    static {
        requestMarshaller.alias("request", IntacctRequest.class);
        requestMarshaller.alias("control", RequestControl.class);
        requestMarshaller.alias("operation", Operation.class);
        requestMarshaller.alias("authentication", Authentication.class);
        requestMarshaller.alias("login", Login.class);
        requestMarshaller.alias("content", Content.class);
        requestMarshaller.alias("function", Function.class);
        requestMarshaller.alias("getAPISession", getAPISession.class);
        requestMarshaller.alias("lookup", Lookup.class);
        requestMarshaller.alias("inspect", Inspect.class);
        requestMarshaller.alias("readByQuery", ReadByQuery.class);
        requestMarshaller.alias("create", Create.class);
        requestMarshaller.alias("update", Update.class);
        requestMarshaller.alias("delete", Delete.class);
        requestMarshaller.alias("query", Query.class);
        requestMarshaller.alias("filter", Filter.class);
        requestMarshaller.alias("between", Between.class);
        requestMarshaller.alias("select", Select.class);
        requestMarshaller.alias("orderby", OrderBy.class);
        requestMarshaller.alias("order", Order.class);
        requestMarshaller.alias("greaterthan", GreaterThan.class);
        requestMarshaller.alias("or", Or.class);
        requestMarshaller.alias("equalto", EqualTo.class);
        requestMarshaller.alias("readByName", ReadByName.class);
        requestMarshaller.registerConverter(new FunctionConverter(requestMarshaller.getMapper(),
                requestMarshaller.getConverterLookup().lookupConverterForType(Function.class),
                requestMarshaller.getReflectionProvider()));
        requestMarshaller.registerConverter(new CreateUpdateConverter(
                requestMarshaller.getConverterLookup().lookupConverterForType(CUDOperation.class)));
        requestMarshaller.registerConverter(new BetweenConverter());
        requestMarshaller.registerConverter(new SelectConverter());
        requestMarshaller.registerConverter(new OrConverter());
        requestMarshaller.processAnnotations(new Class[]{Inspect.class, Lookup.class, Content.class, Create.class, Update.class, Delete.class});
        requestMarshaller.ignoreUnknownElements();
        requestMarshaller.setClassLoader(Thread.currentThread().getContextClassLoader());
        requestMarshaller.addPermission(NoTypePermission.NONE);
        requestMarshaller.addPermission(NullPermission.NULL);
        requestMarshaller.addPermission(PrimitiveTypePermission.PRIMITIVES);
        requestMarshaller.addPermission(InterfaceTypePermission.INTERFACES);
        requestMarshaller.allowTypesByWildcard(new String[]{"java.**","com.syncari.**"});
    }

    private static XStream responseMarshaller = new XStream();

    static {
        responseMarshaller.alias("response", IntacctResponse.class);
        responseMarshaller.alias("control", ResponseControl.class);
        responseMarshaller.alias("operation", OperationResponse.class);
        responseMarshaller.alias("authentication", AuthenticationResponse.class);
        responseMarshaller.alias("result", Result.class);
        responseMarshaller.alias("error", Error.class);
        responseMarshaller.alias("Type", Type.class);
        responseMarshaller.alias("Field", Field.class);
        responseMarshaller.alias("Relationship", Relationship.class);
        responseMarshaller.alias("VALIDVALUE", String.class);

        responseMarshaller.registerConverter(new ResultConverter(
                responseMarshaller.getConverterLookup().lookupConverterForType(Result.class)
        ));
        responseMarshaller.processAnnotations(new Class[]{Type.class,Field.class,OperationResponse.class,Relationship.class,Result.class,InspectType.class,InspectField.class,Attribute.class});
        responseMarshaller.ignoreUnknownElements();
        responseMarshaller.setClassLoader(Thread.currentThread().getContextClassLoader());
        responseMarshaller.addPermission(NoTypePermission.NONE);
        responseMarshaller.addPermission(NullPermission.NULL);
        responseMarshaller.addPermission(PrimitiveTypePermission.PRIMITIVES);
        responseMarshaller.addPermission(InterfaceTypePermission.INTERFACES);
        responseMarshaller.allowTypesByWildcard(new String[]{"java.**","com.syncari.**"});

    }

    public static XStream getResponseMarshaller() {
        return responseMarshaller;
    }

    @Override
    public HttpHeaders getHeaders(AuthConfig authConf) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.TEXT_XML);
        return httpHeaders;
    }

    public IntacctResponse post(String endpoint, IntacctRequest request) {
        String body = requestMarshaller.toXML(request);
        log.debug("Calling Intacct with body={}", body);
        ResponseEntity<String> responseEntity = postRaw(endpoint, body, new AuthConfig());
        if(StringUtils.isBlank(responseEntity.getBody())){
            return null;
        }
        
        String responseXml = responseEntity.getBody();
        log.debug("Intacct response XML: {}", responseXml);
        
        try {
            return (IntacctResponse) responseMarshaller.fromXML(responseXml);
        } catch (Exception e) {
            // Mask sensitive data in request XML for logging
            String maskedRequestXml = maskSensitiveData(body);
            log.error("Failed to parse Intacct XML response. Request XML: {}", maskedRequestXml);
            log.error("Response XML: {}", responseXml);
            log.error("Parse error details", e);
            
            if(responseXml.contains("Object definition ") && responseXml.contains(" not found")) {
                throw new RuntimeException("Object definition not found for reference - " + responseXml);
            } else {
                throw e;
            }
        }
    }
    
    private String maskSensitiveData(String xml) {
        return xml.replaceAll("(<password>)(.*?)(</password>)", "$1***$3")
                 .replaceAll("(<sessionid>)(.*?)(</sessionid>)", "$1***$3");
    }
}
