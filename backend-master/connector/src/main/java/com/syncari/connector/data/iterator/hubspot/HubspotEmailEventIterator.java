package com.syncari.connector.data.iterator.hubspot;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.iterator.AbstractEntityDataBatchIterator;
import com.syncari.connector.data.iterator.Offset;
import com.syncari.connector.rest.HubspotRestClient;
import com.syncari.connector.service.HubspotService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static com.syncari.utils.ExceptionUtils.rethrow;

@Slf4j
public class HubspotEmailEventIterator extends AbstractEntityDataBatchIterator {
    private int pageSize;
    private String pageOffset;
    private ConnectorInfo connector;
    private Supplier<AuthConfig> tokenHandler;
    private boolean hasMore = true;

    ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    HubspotRestClient client;

    public HubspotEmailEventIterator(int pageSize, ConnectorInfo connector,
                                     HubspotRestClient client, Supplier<AuthConfig> tokenHandler) {
        this.client = client;
        this.maxRecords = 1000;
        //Pagesize is pinned to a maximum of 100, and a minimum of maxRecords (if maxRecords < (pageSize || 100))
        this.pageSize = pageSize == 0 ? 100 : Math.min(Math.min(100, pageSize), this.maxRecords);
        this.connector = connector;
        this.tokenHandler = tokenHandler;
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
        if (!hasMore) {
            log.info("Iterator exhaused all events");
			return false;
        }
        return true;
    }

    @Override
    public List<EntityData> next() {
        String offset = pageOffset == null ? "" :"?offset=" + pageOffset;
        String url = String.format(HubspotService.API_HOST + "/email/public/v1/events%s", offset);

        List<EntityData> results = new ArrayList<>();
        log.info("Fetching email events for watermark {}", offset);
        ResponseEntity<String> responseEntity = client.getResponse(url, connector, tokenHandler);

        Map<String, Object> resp = rethrow(() -> mapper.readValue(responseEntity.getBody(), Map.class));
        if (resp.containsKey("events")) {
            List events = mapper.convertValue(resp.get("events"), new TypeReference<List<Map<String, Object>>>(){});
            for (int i = 0; i < events.size(); i++) {
                Map o = (Map) events.get(i);
                var data = new EntityData();
                data.setId(o.get("id").toString());
                data.setName(Constants.EMAIL_EVENT);
                data.setConnectorId(connector.getId());
                data.setLastModified(Long.parseLong(o.get("created").toString()));
                data.setCreatedAt(Long.parseLong(o.get("created").toString()));
                data.setValues(o);
                results.add(data);
            }
            hasMore = mapper.convertValue(resp.get("hasMore"), Boolean.class);
            if (hasMore) {
                pageOffset = mapper.convertValue(resp.get("offset"), String.class);
            } else {
                pageOffset = "0";
            }
        }
        if(results.isEmpty() || results.size() < pageSize) {
            hasMore = false;
        }
        return results;
    }
}
