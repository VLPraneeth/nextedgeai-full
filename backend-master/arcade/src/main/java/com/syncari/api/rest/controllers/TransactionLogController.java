package com.syncari.api.rest.controllers;

import static com.syncari.core.security.Permissions.VIEW_TRANSACTIONS;
import static com.syncari.utils.I18n.i18n;
import static com.syncari.connector.Operation.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.core.datatype.DatatypeFactory;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.syncari.api.core.util.ObjectTransformer;
import com.syncari.api.rest.controllers.data.TransactionKpis;
import com.syncari.api.rest.controllers.data.TransactionsQueryResponse;
import com.syncari.restutils.data.DataQueryMetadata;
import com.syncari.api.rest.controllers.data.studio.DataQueryResponse;
import com.syncari.connector.EntityData;
import com.syncari.restutils.data.EntityRecord;
import com.syncari.connector.Operation;
import com.syncari.core.datatype.Datatype;
import com.syncari.core.datatype.StringType;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.MergeOperation;
import com.syncari.core.model.TransactionLog;
import com.syncari.core.model.pagination.Page;
import com.syncari.core.model.pagination.PageCursor;
import com.syncari.core.model.pagination.PageDirection;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.SchemaService;
import com.syncari.core.service.TransactionLogService;
import com.syncari.utils.DateUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping(value = "/api/v1/transaction")
public class TransactionLogController {
    @Autowired
    TransactionServiceWrapper transactionService;
    @Autowired
    TransactionLogService service;
    @Autowired
    SchemaService schemaService;
    @Autowired
    ObjectTransformer transformer;
    @Autowired
    ConnectorService connectorService;

    //@Secured(VIEW_TRANSACTIONS)
    @PreAuthorize("hasAnyAuthority('VIEW_TRANSACTIONS', 'READ_DATA_STUDIO')")
    @RequestMapping(method = RequestMethod.GET, value = "/")
    public TransactionsQueryResponse query(
        @RequestParam(defaultValue = "") String cursor,
        @RequestParam String direction,
        @RequestParam int count,
        @RequestParam Optional<String> startDate,
        @RequestParam Optional<String> endDate,
        @RequestParam Optional<String> entityName,
        @RequestParam Optional<String> syncariId,
        @RequestParam Optional<String> operation,
        @RequestParam Optional<String> entityId
    ) {

        if (cursor.isEmpty() && PageDirection.valueOf(direction) == PageDirection.previous) {
            throw new SyncariValidationException(i18n("cursor_needed_for_prev"));
        }

        var start = startDate.map(date -> DateUtil.parse(date, DateUtil.dateFormatMillis));
        var end = endDate.map(date -> DateUtil.parse(date, DateUtil.dateFormatMillis));

        Page<TransactionLog> page = service.query(
            new PageCursor(cursor, PageDirection.valueOf(direction), count),
            start,
            end,
            entityName,
            syncariId,
            operation,
            entityId,
            PageDirection.valueOf(direction).equals(PageDirection.previous)
        );
        return new TransactionsQueryResponse(tranformTransactionLogs(page.getRecords()), page.getPageInfo());
    }

    @Secured(VIEW_TRANSACTIONS)
    @RequestMapping(method = RequestMethod.POST, value = "/errors/{syncCycleId}/{nodeId}")
    public TransactionsQueryResponse query(
            @PathVariable String syncCycleId,
            @PathVariable String nodeId,
            @RequestParam(defaultValue = "") String cursor,
            @RequestParam String direction,
            @RequestParam int count,
            @RequestBody Map<String, String> errorDetails
    ) {
        if (cursor.isEmpty() && PageDirection.valueOf(direction) == PageDirection.previous) {
            throw new SyncariValidationException(i18n("cursor_needed_for_prev"));
        }

        if (errorDetails == null || !errorDetails.containsKey("message")) {
            throw new SyncariValidationException(i18n("error_message_required"));
        }

        Page<TransactionLog> page = service.query(
                syncCycleId,
                nodeId,
                errorDetails.get("message"),
                new PageCursor(cursor, PageDirection.valueOf(direction), count));
        return new TransactionsQueryResponse(tranformTransactionLogs(page.getRecords()), page.getPageInfo());
    }

    @Secured(VIEW_TRANSACTIONS)
    @RequestMapping(method = RequestMethod.GET, value = "/kpis")
    public TransactionKpis getKpis(
        @RequestParam String startDate,
        @RequestParam String endDate,
        @RequestParam(required = false) String entityName
    ) {
        Date start = DateUtil.parse(startDate, DateUtil.dateFormatMillis);
        Date end = DateUtil.parse(endDate, DateUtil.dateFormatMillis);

        return new TransactionKpis(
            transactionService.countByRange(start, end, entityName),
            transactionService.mostActiveEntity(start, end),
            mostActiveSynapse(start, end),
            transactionService.countNewByRange(start, end, entityName),
            transactionService.countUpdateByRange(start, end, entityName)
        );
    }
    
    @Secured(VIEW_TRANSACTIONS)
    @PostMapping("/download")
	public ResponseEntity<Resource> download(@RequestParam String startDate, @RequestParam String endDate,
			@RequestParam String operation, @RequestParam String entityName) {
    	
        if(StringUtils.isBlank(startDate) || StringUtils.isBlank(endDate)) {
        	throw new SyncariValidationException(i18n("missing_date"));
        }
    	var start = DateUtil.parse(startDate, DateUtil.dateFormatMillis);
        var end = DateUtil.parse(endDate, DateUtil.dateFormatMillis);
        if(StringUtils.isBlank(operation) || !(isEqual(merge, operation) || isEqual(merge_report_only, operation) || isEqual(merge_skip, operation))) {
        	throw new SyncariValidationException(i18n("unsupported_operation"));
        }
        if(StringUtils.isBlank(entityName) || "all_entities".equalsIgnoreCase(entityName)) {
        	throw new SyncariValidationException(i18n("entity_required"));
        }
        
        EntityDefinition entity = schemaService.getSyncariEntityByName(entityName).get();
        Set<String> columns = entity.getAttributes().stream().map(a -> a.getApiName()).collect(Collectors.toSet());
		InputStreamResource resource = new InputStreamResource(new QueryCSVInputStream(start, end, operation, entityName, columns));
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + entity.getApiName() + "_merge_txn.csv\"")
                .body(resource);
    }

    private String mostActiveSynapse(Date start, Date end) {
        return transactionService.mostActiveSynapse(start, end)
            .flatMap(connectorId -> connectorService.find(connectorId))
            .map(connector -> connector.getName())
            .orElse("-");
    }

    class QueryCSVInputStream extends InputStream {
        private final Date startDate;
        private final Date endDate;
        private final String operation;
        private final String entityId;
        private final Set<String> selectedColumns;
        private  String cursor;
        private ByteArrayInputStream bin;
        private final CSVPrinter csvPrinter;
        private final StringWriter csvBuffer;
        private boolean completed;

        public QueryCSVInputStream(Date start, Date end, String operation, String entityId, Set<String> selectedColumns){
            this.startDate = start;
            this.endDate = end;
            this.operation = operation;
            this.entityId = entityId;
            this.selectedColumns = selectedColumns;
            try {
                List<String> headers = new ArrayList<>();
                selectedColumns.add(ObjectTransformer.IS_WINNER);
                selectedColumns.add(ObjectTransformer.WINNER_ID);
                selectedColumns.add(ObjectTransformer.SYNCARI_ID);
                selectedColumns.forEach(c -> headers.add(c));
                csvBuffer = new StringWriter();
                csvPrinter = new CSVPrinter(csvBuffer, CSVFormat.DEFAULT
                        .withHeader(headers.toArray(new String[headers.size()])));
            }catch(IOException ex){
                throw new RuntimeException(ex);
            }
            readPage();
        }

		private void readPage(){
            Page<TransactionLog> page = service.query(
            		new PageCursor(cursor, PageDirection.next, 50),
            		Optional.of(startDate),
            		Optional.of(endDate),
            		Optional.of(entityId),
                    Optional.empty(),
                    Optional.of(operation)
                );
            DataQueryMetadata metadata = new DataQueryMetadata(selectedColumns, Map.of());
            List<EntityRecord> entityRecords = transformer.toTxnEntityRecords(page.getRecords());
            DataQueryResponse dataQueryResponse = new DataQueryResponse(entityRecords, page.getPageInfo(), metadata);
            writeToBuffer(dataQueryResponse);
            cursor = service.dateWithinAWeek(Optional.of(startDate)) ? dataQueryResponse.getPageInfo().getEnd() :
                    Integer.toString(dataQueryResponse.getPageInfo().getPageNumber() + 1);
        }

        private void writeToBuffer(DataQueryResponse dataQueryResponse) {
            dataQueryResponse.getRecords().forEach(entityRecord -> {
                List<String> record = new LinkedList<>();
                selectedColumns.forEach(column->{
                    record.add((entityRecord.getValues().get(column)==null? "":entityRecord.getValues().get(column)).toString());
                });
                try {
                    csvPrinter.printRecord(record);
                } catch (IOException e) {
                    log.warn("DS Export: cannot write row "+record,e);
                }
            });
            try {
                if(csvBuffer.getBuffer().length() >0) {
                    bin = new ByteArrayInputStream(csvBuffer.getBuffer().toString().getBytes("utf-8"));
                    csvBuffer.getBuffer().setLength(0);
                }
            } catch (UnsupportedEncodingException e) {
                log.error(e.getMessage(),e);
                throw new SyncariValidationException("Invalid data found while exporting CSV");
            }
        }

        public int read() {
            if(bin==null && !completed){
                readPage();
            }
            if(bin == null){
                completed=true;
                return -1;
            }
            int read = bin.read();
            if (read == -1) {
                bin=null;
                return read();
            }
            return read;
        }
    }
    
    private List<TransactionLog> tranformTransactionLogs(List<TransactionLog> logs) {
    	return logs.stream().map(log -> tranformTransactionLog(log)).collect(Collectors.toList());
    }
    
	private TransactionLog tranformTransactionLog(TransactionLog tlog) {
		Map<String, String> attrDataTypeMap = new HashMap<>();
		Map<String, Object> attrDisplayNameMap = new HashMap<>();
		var additionalInfo = tlog.getAdditionalInfo();

        String mergeString = additionalInfo.containsKey("mergeDetails") ? "mergeDetails"
                : additionalInfo.containsKey("mergeSkipDetails") ? "mergeSkipDetails" : "";

		if (!StringUtils.isBlank(mergeString)) {
		    try{
                var mergeDetails = (MergeOperation) additionalInfo.get(mergeString);
                var attrDefMap = mergeDetails.getAttributeDefinitionMap();
                if (attrDefMap != null) {
                    for (var attrKey : attrDefMap.keySet()) {
                        var mapping = attrDefMap.get(attrKey);

                        Object type  = mapping.get("dataType");
                        Datatype dataType = null;
                        if (Datatype.class.isAssignableFrom(type.getClass())) {
                            dataType = (Datatype) mapping.get("dataType");
                        } else {
                            if (null != ((Map)mapping.get("dataType")).get("name")){
                                dataType = DatatypeFactory.getDatatype((String)((Map)mapping.get("dataType")).get("name"));

                            }else if (null != ((Map)mapping.get("dataType")).get("_class")){
                                String className = (String)((Map)mapping.get("dataType")).get("_class");
                                try {
                                    dataType = (Datatype) Class.forName(className).getDeclaredConstructor().newInstance();
                                } catch (InstantiationException | IllegalAccessException | ClassNotFoundException |
                                         NoSuchMethodException | InvocationTargetException e) {
                                    log.error("Error while getting dataType {}", ExceptionUtils.getStackTrace(e));
                                }
                            } else {
                                // default to string
                                dataType = new StringType();
                            }
                        }
                        if (dataType != null) {
                            mapping.put("dataType", dataType.getName());
                            attrDataTypeMap.put(attrKey.toLowerCase(), dataType.getName());
                            attrDisplayNameMap.put(attrKey.toLowerCase(), mapping.get("displayName"));
                        }
                    }
                }
                var winningRec = mergeDetails.getWinningRecord();
                if (winningRec != null) {
                    updateEntityData(winningRec, attrDataTypeMap, attrDisplayNameMap);
                }
                var loosingRecs = mergeDetails.getLosingRecords();
                if(loosingRecs != null) {
                    loosingRecs.forEach(rec -> updateEntityData(rec, attrDataTypeMap, attrDisplayNameMap));
                }
            }catch (ClassCastException castException){
		        // There are old merge transaction being logged in BQ txn log which has different way of dataType storage, which is causing this error, we are skipping to get details of those txns
		        log.error("ClassCastException occurred while getting mergeDetails {}", ExceptionUtils.getStackTrace(castException));
            } catch (Exception e) {
                log.error("Error message " + e.getMessage(), e);
            }
		}
		return tlog;
	}
	private void updateEntityData(EntityData data, Map<String, String> attrDataTypeMap, Map<String, Object> attrDisplayNameMap) {
		var map = data.getValues();
		if (map != null) {
			for (var attrKey : map.keySet()) {
				var valueMap = new LinkedHashMap<String, Object>();
				valueMap.put("value", map.get(attrKey));
				String dataType = attrDataTypeMap.get(attrKey.toLowerCase());
				if(dataType == null) {
					dataType = StringType.NAME;
				}
				Object displayName = attrDisplayNameMap.get(attrKey.toLowerCase());
				if(displayName == null) {
					displayName = attrKey;
				}
				valueMap.put("dataType", dataType);
				valueMap.put("displayName", displayName);
				map.put(attrKey,valueMap);
			}
		}
	}
    
}
