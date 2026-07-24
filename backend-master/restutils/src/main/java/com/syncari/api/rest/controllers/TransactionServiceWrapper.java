package com.syncari.api.rest.controllers;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.syncari.analytics.QueryEngine;
import com.syncari.connector.Constants;
import com.syncari.core.schema.Schema;
import com.syncari.core.service.SchemaService;
import com.syncari.core.service.TransactionLogService;
import com.syncari.utils.DateUtil;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class TransactionServiceWrapper {
    @Autowired
    TransactionLogService transactionLogService;

    @Autowired
    QueryEngine queryEngine;

    @Autowired
    DateUtil dateUtil;
    @Autowired
    SchemaService schemaService;

    public Map<String, Object> countByDayByRange(Date start, Date end) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        Date tempStart = clipToStart(start);
        while (tempStart.before(end)) {
            Long countByRange = queryEngine.transactionCountByRange(toInstant(tempStart), toInstant(plusOneDay(tempStart)));
            result.put(String.format("%s/%s", dateUtil.getMonth(tempStart), dateUtil.getDay(tempStart)), countByRange);
            tempStart = plusOneDay(tempStart);
        }
        log.info("Found {} records for countByDayByRange", result.size());
        return result;
    }

    private Instant toInstant(Date tempStart) {
        return Instant.ofEpochMilli(tempStart.getTime());
    }

    public Long countByRange(Date start, Date end, String entityName) {
        if (hasEntityNameFilter(entityName)) {
            return queryEngine.transactionCountByEntityNameAndRange(entityName, toInstant(clipToStart(start)), toInstant(plusOneDay(end)));
        }
        return queryEngine.transactionCountByRange(toInstant(clipToStart(start)), toInstant(plusOneDay(end)));
    }

    public Long countNewByRange(Date start, Date end, String entityName) {
        if (hasEntityNameFilter(entityName)) {
            return queryEngine.transactionCountNewByEntityNameAndRange(entityName, toInstant(clipToStart(start)), toInstant( plusOneDay(end)));
        }
        return queryEngine.transactionCountNewByRange(toInstant(clipToStart(start)), toInstant(plusOneDay(end)));
    }

    public Long countUpdateByRange(Date start, Date end, String entityName) {
        if (hasEntityNameFilter(entityName)) {
            return queryEngine.transactionCountUpdateByEntityNameAndRange(entityName, toInstant(clipToStart(start)),toInstant(plusOneDay(end)));
        }
        return queryEngine.transactionCountUpdateByRange(toInstant(clipToStart(start)),toInstant(plusOneDay(end)));
    }

    public String mostActiveEntity(Date start, Date end) {
        String entity = queryEngine.mostActiveEntity(toInstant(clipToStart(start)),toInstant(plusOneDay(end)));
        log.info("Found {} as most active entity", entity);
        return StringUtils.isBlank(entity) ? "-" : StringUtils.capitalize(entity);
    }
    public Optional<String> mostActiveSynapse(Date start, Date end) {
        return queryEngine.mostActiveSynapse(toInstant(start), toInstant(end));
    }

    public Map<String, Long> topActiveEntitiesWithCount(Date start, Date end) {
        Schema syncariSchema = schemaService.getSyncariSchema();
        Map<String, Long> result = queryEngine.topActiveEntitiesWithCount(toInstant( clipToStart(start)), toInstant(plusOneDay(end)));
        Map<String, Long> resultByName = new HashMap<>();
        result.forEach((apiName, count)->{
            resultByName.put(syncariSchema.findEntityByName(apiName).map(e->e.getDisplayName()).orElse(apiName),count);
        });
        List<String> defaultEntities = List.of(Constants.ACCOUNT, Constants.CONTACT, Constants.LEAD, Constants.OPPORTUNITY);
        if(resultByName.size() < 4) {
            for (String e : defaultEntities) {
                if(!resultByName.containsKey(e)) {
                    resultByName.put(e, 0l);
                }
            }
        }
        return resultByName;
    }

    private boolean hasEntityNameFilter(String entityName) {
        return !(StringUtils.isBlank(entityName));
    }
    private Date plusOneDay(Date end) {
        return clipToStart(new DateTime(end.getTime()).plusDays(1).toDate());
    }

    private Date clipToStart(Date end) {
        return new DateTime(end.getTime()).withTimeAtStartOfDay().toDate();
    }


}
