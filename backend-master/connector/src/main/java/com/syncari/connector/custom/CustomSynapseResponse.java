package com.syncari.connector.custom;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.connector.EntityData;
import com.syncari.connector.data.EntitySchema;
import com.syncari.connector.data.Result;
import com.syncari.connector.data.SynapseInfo;
import com.syncari.connector.exception.*;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.syncari.connector.custom.RequestType.GET_BY_ID;
import static com.syncari.connector.custom.RequestType.SEARCH;

@Data
@Slf4j
public class CustomSynapseResponse {
    private RequestType type;
    private Object response;
    private static final Set<Integer> retryableErrorCodes = Set.of(429, 502, 503, 504);

    private static final ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final ObjectMapper errorMapper = new ObjectMapper();

    public Object unpack() {
        Object unpacked = null;

        if (StringUtils.isEmpty(this.response.toString())) {
            throw new RuntimeException("Encountered empty response from custom synapse call");
        }

        try{
            unpacked = errorMapper.readValue(this.response.toString(), SynapseErrorResponse.class);
            if (unpacked != null) {
                SynapseErrorResponse customSynapseError = (SynapseErrorResponse) unpacked;
                log.error("Encountered custom synapse error: {}", customSynapseError.getMessage());

				if (retryableErrorCodes.contains(customSynapseError.getStatus_code())) {
				    throw new RetriableException(String.valueOf(customSynapseError.getStatus_code()),
                            customSynapseError.toString(), String.valueOf(customSynapseError.getStatus_code()));
				} else if (customSynapseError.getStatus_code() == 405) {
				    throw new NotSupportedException(customSynapseError.getMessage());
				} else {
                    if(type.equals(GET_BY_ID) && customSynapseError.getStatus_code() == 404) {
                        log.debug("Received 404 error from custom synapse - {}", customSynapseError);
                        return new ArrayList<>();
                    }
					throw new NonRetriableException(String.valueOf(customSynapseError.getStatus_code()),
							customSynapseError.toString(), String.valueOf(customSynapseError.getStatus_code()));
				}
            }
        } catch (IOException e) {
            // nothing to do, not an errorresponse.
        }

        try {
            switch (type) {
                case DESCRIBE:
                    List<String> list = mapper.readValue(this.response.toString(), List.class);
                    List<EntitySchema> entitySchemas = new ArrayList<>();
                    for(String entityResponse: list) {
                        EntitySchema schema = mapper.readValue(entityResponse, EntitySchema.class);
                        entitySchemas.add(schema);
                    }
                    unpacked = entitySchemas;
                    break;
                case TEST:
                    unpacked = mapper.readValue(this.response.toString(), Connection.class);
                    break;
                case SYNAPSE_INFO:
                    unpacked = mapper.readValue(this.response.toString(), SynapseInfo.class);
                    break;
                case GET_ACCESS_TOKEN:
                case REFRESH_TOKEN:
                case HTTP_POST:
                case HTTP_PUT:
                case HTTP_DELETE:
                    unpacked = mapper.readValue(this.response.toString(), Map.class);
                    break;
                case READ:
                    unpacked = mapper.readValue(this.response.toString(), ReadResponse.class);
                    break;
                case GET_BY_ID:
                case SEARCH:
                    List<Object> records = mapper.readValue(this.response.toString(), List.class);
                    List<EntityData> recordValues = new ArrayList<>();
                    for(Object entityResponse: records) {
                        EntityData ed = mapper.readValue(entityResponse.toString(), EntityData.class);
                        recordValues.add(ed);
                    }
                    unpacked = recordValues;
                    break;
                case CREATE:
                case UPDATE:
                case DELETE:
                    List<Object> respObjects = mapper.readValue(this.response.toString(), List.class);
                    List<Result> results = new ArrayList<>();
                    for(Object entityResponse: respObjects) {
                        Result ed = mapper.readValue(entityResponse.toString(), Result.class);
                        // When an empty error list is sent from cloud function synapses, we set this to null and framework has issues with it.
                        if (ed.getErrors() == null) {
                            ed.setErrors(new ArrayList<>());
                        }
                        results.add(ed);
                    }
                    unpacked = results;
                    break;
                case EXTRACT_WEBHOOK_IDENTIFIER:
                    return this.response.toString();
                case PROCESS_WEBHOOK:
                    log.debug("Resp: " + this.response);
                    List<Object> eventRecords = mapper.readValue(this.response.toString(), List.class);
                    List<EntityData> eventRecordValues = new ArrayList<>();
                    for(Object entityResponse: eventRecords) {
                        EntityData ed = mapper.readValue(entityResponse.toString(), EntityData.class);
                        eventRecordValues.add(ed);
                    }
                    unpacked = eventRecordValues;
                    break;
                case GET_HEADERS:
                    log.debug("Response is : " + this.response);
                    return mapper.readValue(StringUtils.stripEnd(this.response.toString(), "\n"), Map.class);
                default:
                    throw new NonRetriableException(ErrorCodes.BAD_REQUEST, "Unsupported requesttype " + type, HttpStatus.BAD_REQUEST.toString());
            }
        } catch (Exception e) {
            String errorMsg = "Failed to parse custom synapse response.";
            log.error(errorMsg, e);
            throw new NonRetriableException(ErrorCodes.BAD_REQUEST, errorMsg + " Detail: " + e.getMessage(), HttpStatus.BAD_REQUEST.toString());
        }
        return unpacked;
    }
}
