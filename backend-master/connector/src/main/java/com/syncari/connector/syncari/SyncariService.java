package com.syncari.connector.syncari;

import com.syncari.connector.Capability;
import com.syncari.connector.Constants;
import com.syncari.connector.EntityData;
import com.syncari.connector.ListBasedIterator;
import com.syncari.connector.data.*;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.SynapseInfoService;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component(Constants.SYNCARI)
public class SyncariService implements SynapseInfoService, CommonDataService {
    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        return List.of();
    }

    @Override
    public List<AuthField> getConfigureFields() {
        return List.of();
    }

    @Override
    public Map<String, String> getEntityMappings() {
        return Map.of("timeTicker", "timeTicker");
    }

    @Override
    public Map<String, String> getAttributeMappings(String entityApiName) {
        return Map.of();
    }

    @Override
    public String getName() {
        return Constants.SYNCARI;
    }

    @Override
    public String getCategory() {
        return null;
    }

    @Override
    public UIMetadata getUIMetadata() {
        return new UIMetadata().setIconPath("/assets/icons/logos/syncari.svg")
                .setDisplayName("Syncari")
                .setBackgroundColor("#f2fbff")
                .setHelpUrl(helpArticlesBaseUrl + SYNAPSE_COMING_SOON_ARTICLE);
    }

    @Override
    public String getCapabilitiesArticleId() {
        return "";
    }

    @Override
    public List<Capability> getCapabilities() {
        return List.of(Capability.userEditableReadOnly);
    }

    @Override
    public FetchResponse getByWatermark(SyncRequest request) {
        if(!request.getWatermark().isResync() && request.getEntityName().equalsIgnoreCase("timeTicker")) {
            long currentTimestamp = System.currentTimeMillis();
            EntityData entityData =  getTimeTickerEntityData(currentTimestamp);
            entityData.setLastModified(request.getWatermark().getEnd());
            return new FetchResponse(request.getWatermark(), new ListBasedIterator(List.of(entityData), request.getWatermark()));
        }
        return new FetchResponse(request.getWatermark(), new ListBasedIterator(List.of(), request.getWatermark()));
    }

    private EntityData getTimeTickerEntityData(long currentTimestamp) {
        EntityData entityData = new EntityData("timeTicker");
        String timestampStr = String.valueOf(currentTimestamp);
        entityData.setId(timestampStr);
        Instant instant;
        try {
            if (timestampStr.length() == 10) {
                instant = Instant.ofEpochSecond(currentTimestamp);
            } else if (timestampStr.length() == 13) {
                instant = Instant.ofEpochMilli(currentTimestamp);
            } else {
                // Invalid timestamp
                throw new RuntimeException("Invalid timestamp");
            }
        } catch (Exception e) {
            throw new RuntimeException("Invalid timestamp");
        }
        ZonedDateTime dateTime = ZonedDateTime.ofInstant(instant, ZoneOffset.UTC);
        entityData.addValue("timestamp", instant);
        entityData.addValue("datetime", dateTime);
        entityData.addValue("date", Date.from(dateTime.toInstant()));

        entityData.addValue("year", dateTime.getYear());
        entityData.addValue("month", dateTime.getMonthValue());
        entityData.addValue("day", dateTime.getDayOfMonth());
        entityData.addValue("hour", dateTime.getHour());
        entityData.addValue("minute", dateTime.getMinute());
        entityData.addValue("second", dateTime.getSecond());
        entityData.addValue("millisecond", instant.getNano() / 1_000_000);
        return entityData;
    }

    @Override
    public long getFirstCreatedTime(SyncRequest request) {
        return 0;
    }

    @Override
    public List<EntityData> getByIds(SyncRequest request) {
        if(request.getEntityName().equalsIgnoreCase("timeTicker")) {
            return request.getIds().stream().map(id -> getTimeTickerEntityData(Long.valueOf(id))).collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    @Override
    public SyncResponse create(SyncRequest request) {
        return null;
    }

    @Override
    public SyncResponse update(SyncRequest request) {
        return null;
    }

    @Override
    public SyncResponse delete(SyncRequest request) {
        return null;
    }

    @Getter
    @Setter
    public static class TimestampObject {
        private long id;
        private long timestamp;
        private ZonedDateTime datetime;
        private Date date;
        private int year;
        private int month;
        private int day;
        private int hour;
        private int minute;
        private int second;
        private int millisecond;

        public TimestampObject(long id, long timestamp, ZonedDateTime datetime, Date date, int year, int month, int day, int hour, int minute, int second, int millisecond) {
            this.id = id;
            this.timestamp = timestamp;
            this.datetime = datetime;
            this.date = date;
            this.year = year;
            this.month = month;
            this.day = day;
            this.hour = hour;
            this.minute = minute;
            this.second = second;
            this.millisecond = millisecond;
        }
    }
}
