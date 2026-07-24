package com.syncari.connector.data.iterator.hubspot;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.WatermarkInfo;
import com.syncari.connector.data.iterator.AbstractEntityDataBatchIterator;
import com.syncari.connector.data.iterator.Offset;
import com.syncari.connector.rest.HubspotRestClient;
import com.syncari.connector.service.HubspotService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;

import java.util.*;
import java.util.function.Supplier;

import static com.syncari.utils.ExceptionUtils.rethrow;

@Slf4j
public class HubspotFormSubmissionIterator extends AbstractEntityDataBatchIterator {
    private WatermarkInfo initialWatermark;
    static HashFunction HASH = Hashing.murmur3_128();
    private List<EntityData> forms;
    private int pageSize;
    private int currentFormIndex;
    private String pageOffset;
    private ConnectorInfo connector;
    private Supplier<AuthConfig> tokenHandler;
    private boolean formsDone;

    ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    HubspotRestClient client;

    public HubspotFormSubmissionIterator(WatermarkInfo initialWatermark, int pageSize, ConnectorInfo connector,
            HubspotRestClient client, List<EntityData> forms, Supplier<AuthConfig> tokenHandler) {
        this.forms = forms;
        this.client = client;
        //Pagesize is pinned to a maximum of 100, and a minimum of maxRecords (if maxRecords < (pageSize || 100))
        this.pageSize = pageSize == 0 ? 100 : Math.min(Math.min(100, pageSize), this.maxRecords);
        this.connector = connector;
        this.tokenHandler = tokenHandler;
        this.initialWatermark = initialWatermark;
    }

    @Override
    public long getLastOffset() {
        return 0;
    }

    @Override
    public Offset getOffsetInfo() {
        return new Offset(Offset.OffsetType.CUSTOM, pageSize);
    }

    @Override
    public boolean hasNext() {
        if (formsDone) {
            log.info("Iterator exhaused all forms");
			return false;
        }
        return true;
    }

    @Override
    public List<EntityData> next() {
        List<EntityData> submissions = new ArrayList<>();
        String formId = forms.get(currentFormIndex).getId();
        String url = String.format(HubspotService.API_HOST + "/form-integrations/v1/submissions/forms/%s?after=%s", formId, pageOffset);

        log.debug("Fetching formsubmission for watermark {} and form {}", pageOffset, formId);
        ResponseEntity<String> responseEntity = client.getResponse(url, connector, tokenHandler);

        final var tempRespEntity = responseEntity;
        Map<String, Object> resp = new HashMap<>();
        if (tempRespEntity != null) {
            resp = rethrow(() -> mapper.readValue(tempRespEntity.getBody(), Map.class));
        }
        List<Map<String, Object>> formSubmissions = (List<Map<String, Object>>) resp.get("results");
        for (int i = 0; i < formSubmissions.size(); i++) {
            Map formSubmission = (Map) ((Map) formSubmissions.get(i));
            String submittedAt = formSubmission.get("submittedAt").toString();
            if(Long.parseLong(submittedAt) > initialWatermark.getStart() || initialWatermark.isResync()) {
                submissions.add(extractFormSubmission(formSubmission, connector.getId()));
            }
        }

        // the submission api gives records in a reverse choro order, so stop when we hit the initial start wm
        if(submissions.isEmpty() || !resp.containsKey("paging") || (submissions.get(submissions.size()-1).getLastModified() < initialWatermark.getStart())) {
            pageOffset = null;
            // all form submissions for this form are exhaused, move the currentFormIndex
            if(currentFormIndex == forms.size() - 1) {
                // all forms completed
                formsDone = true;
            } else {
                currentFormIndex = currentFormIndex + 1;
            }
        } else {
            Map next = (Map) ((Map) resp.get("paging")).get("next");
            pageOffset = next.get("after").toString();
        }

        return submissions;
    }

    private EntityData extractFormSubmission(Map formSubmission, String connectorId) {
        EntityData form = forms.get(currentFormIndex);
        var data = new EntityData();
        data.setConnectorId(connectorId);
        data.setName(Constants.FORM_SUBMISSION);
        String submittedAt = formSubmission.get("submittedAt").toString();
        data.setLastModified(Long.parseLong(submittedAt));
        data.setCreatedAt(Long.parseLong(submittedAt));
        data.addValue("pageUrl", formSubmission.get("pageUrl"));
        data.addValue("formId", form.getId());
        data.addValue("formName", form.getValue("name"));
        data.addValue("formType", form.getValue("formType"));
        data.addValue("submittedAt", submittedAt);
        List<Map<String, Object>> values = (List<Map<String, Object>>) formSubmission.get("values");
        String appended = "";
        Map<String, Object> valuesMap = new HashMap<>();
        for(Map<String, Object> entry : values) {
            if(entry.containsKey("name") && entry.get("name") != null) {
                String key = entry.get("name").toString();
                Object value = entry.get("value");
                valuesMap.put(key, value);

                appended = appended.concat(key).concat(value == null ? "" : value.toString());
            }
        }
        data.addValue("values", valuesMap);
        data.setId(form.getId() + "_" + submittedAt+"_" + HASH.hashBytes(appended.getBytes()));
        data.addValue("submissionId", data.getId());
        return data;
    }
}
