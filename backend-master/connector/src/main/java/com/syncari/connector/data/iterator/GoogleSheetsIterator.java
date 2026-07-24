package com.syncari.connector.data.iterator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.exception.RetriableException;
import com.syncari.connector.rest.SyncariOauthRestClient;
import com.syncari.connector.service.googlesheets.SheetInfo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;
import com.syncari.connector.ConnectorHelper;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.JsonParserConfig;
import com.syncari.connector.data.EntitySchema;
import com.syncari.connector.data.SyncRequest;
import com.syncari.connector.data.WatermarkInfo;
import com.syncari.connector.service.GoogleSheetsService;
import com.syncari.utils.TextUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GoogleSheetsIterator extends AbstractEntityDataBatchIterator {
    ConnectorInfo config;
	List<EntityData> data = new ArrayList<>();
	String folderName;
	String fileName;
	String sheetName;
	int colCount;
	ObjectMapper mapper;
    private SheetInfo sheetInfo;
    WatermarkInfo baseWatermark;
	long offset = 0;
	boolean isLastPage = false;
	TextUtil util = new TextUtil();
	EntitySchema entitySchema;
    Supplier<AuthConfig> tokenHandler;

    public GoogleSheetsIterator(SyncRequest request, SheetInfo sheetInfo, WatermarkInfo baseWatermark, long offset, int colCount, ObjectMapper mapper, int maxRecords, Supplier<AuthConfig> tokenHandler) {
        this.sheetInfo = sheetInfo;
        this.baseWatermark = baseWatermark;
        this.offset = offset;
        this.fileName = sheetInfo.getSpreadsheetId();
        this.folderName = request.getEntityName();
        this.sheetName = sheetInfo.getSheetName();
        this.colCount = colCount;
        this.config = request.getConnector();
        this.mapper = mapper;
        this.maxRecords = maxRecords;
        this.pageSize=1000;
        this.entitySchema=request.getEntitySchema();
        this.tokenHandler = tokenHandler;
    }

    @Override
	public boolean hasNext() {
		// We have already consumed last page. Nothing more here
		if (isLastPage && isConsumed() || hasFetchedMaxRecords()) {
			return false;
		}
		// retrieved data is not yet consumed.
		if (!isConsumed())
			return true;
		long now = System.currentTimeMillis();
		var results = getRows(folderName, fileName, sheetName, offset, getEffectivePageSize(), colCount, config, tokenHandler);
		long done = System.currentTimeMillis();

		List<EntityData> entityDataStream = results;
		// set last modified
		data = entityDataStream.stream().map(e -> {
			e.setLastModified(sheetInfo.getLastModifiedTime().toInstant().toEpochMilli());
			e.setCreatedAt(sheetInfo.getCreatedTime().toInstant().toEpochMilli());
			e.addValue(GoogleSheetsService.SYNCARI_LAST_MODIFIED, sheetInfo.getLastModifiedTime());
			return e;
		}).collect(Collectors.toList());
		stats.addLatencyCount((done-now),data.size());
		offset = nextOffset(results, data);
		isLastPage = data.size()+1 < getEffectivePageSize();
		return data.size() > 0;
	}

    protected long nextOffset(List<EntityData> results, List<EntityData> data) {
        return offset + data.size();
    }

	private boolean isConsumed() {
		return data.isEmpty();
	}

	@Override
	public List<EntityData> next() {
		// reset data to mark it as consumed
        totalRecordsFetched+=data.size();
		var temp = data;
		if (!data.isEmpty()) {
			lastWatermark = baseWatermark.getStart();
		}
		data = new ArrayList<>();

		return temp;
	}


    private List<EntityData> getRows(String entityId, String spreadSheetId, String sheetName, long offset, int pageSize, int colCount, ConnectorInfo config, Supplier<AuthConfig> tokenHandler) {
        String idApiName = (entitySchema != null && entitySchema.hasIdField()) ? entitySchema.getIdField().getApiName() : Constants.SYNCARI_ID;
        List<EntityData> result = new ArrayList<>();
        SyncariOauthRestClient restClient = new SyncariOauthRestClient(getSingleJsonConfig(""), mapper);
        String columnAlphabet = ConnectorHelper.getColumnAlphabet(colCount);
        String headerRange = "&ranges="+sheetName+"!A1:"+columnAlphabet+"1";
        // Limit the end row to pagesize records and this row is inclusive hence decrement -1
        long endRowIndex = offset + pageSize - 1;
        String range = "&ranges="+sheetName+"!A"+offset+":"+columnAlphabet+endRowIndex;
        String sheetUrl = String.format(GoogleSheetsService.GET_SHEET_VALUES_BY_RANGE, spreadSheetId, headerRange+range,config.getId());
        ResponseEntity<String> sheetResponse = null;
        try{
            sheetResponse = restClient.getOauthResponse(sheetUrl, config, tokenHandler, GoogleSheetsService.SHEETS_TOKEN_REFRESH_ERROR_CODES);
        } catch (RetriableException e) {
            if(e.getMessage().contains("Quota exceeded")) {
                try {
                    Thread.sleep(60000);
                    sheetResponse = restClient.getOauthResponse(sheetUrl, config, tokenHandler, GoogleSheetsService.SHEETS_TOKEN_REFRESH_ERROR_CODES);
                } catch (InterruptedException ex) {
                    throw e;
                } catch (NonRetriableException nre) {
                    return handleNRE(result, nre);
                }
            }
        } catch (NonRetriableException nre){
            return handleNRE(result, nre);
        }
        ReadContext sheetCtx = JsonPath.parse(sheetResponse.getBody());
        List<String> headerRows = sheetCtx.read("valueRanges[0].values[0]");
        List valueRanges = sheetCtx.read("valueRanges");
        if(valueRanges == null || valueRanges.size() <= 1) return result;
        Map values = sheetCtx.read("valueRanges[1]");
        if(!values.containsKey("values")) return result;
        List<?> rows = sheetCtx.read("valueRanges[1].values");
        String sheetNameKey = util.createApiName(sheetName);
        if (rows != null && rows.size() > 0) {
            for (int i = 0; i < rows.size(); i++) {
                EntityData data = new EntityData(entityId);
                for (int j = 0; j < headerRows.size(); j++) {
                    String key = util.createApiName(headerRows.get(j));
                    Object value = null;
                    if(i < rows.size()) {
                        List list = ((List<?>) rows.get(i));
                        if(j < list.size()) {
                            value = list.get(j);
                        }
                    }
                    if(idApiName.equalsIgnoreCase(key)) {
                        data.setId(value == null ? null : value.toString());
                        data.addValue(key, value);
                    } else {
                        if(value != null && "".equalsIgnoreCase(value.toString())) {
                            value = null;
                        }
                        data.addValue(key, value);
                    }
                }
                // If syncari id has no value, generate a value based on sheetId , sheetname and row number
                if(!data.hasId()) {
                    data.setId(String.format("%s-%s-%s",spreadSheetId,sheetNameKey,(offset+i)));
                }
                result.add(data);
            }
        }
        log.info("Fetched {} rows for spreadSheetId {}", result.size(), spreadSheetId);
        return result;
    }

    private List<EntityData> handleNRE(List<EntityData> result, NonRetriableException nre) {
        // If the error is 400 and Range exceeds grid limits return results
        // Happens only when the range offset is more than totalRowcount
        // Range Limit is not impacted by totalRowCount
        if (StringUtils.equals(nre.getErrorCode(), ErrorCodes.BAD_REQUEST.toString())
                && StringUtils.isNotBlank(nre.getStatusCode())
                && StringUtils.contains(nre.getStatusCode(), "400 BAD_REQUEST")
                && StringUtils.isNotBlank(nre.getMessage())
                && StringUtils.contains(nre.getMessage(), "exceeds grid limits")){
            return result;
        }
        else {
            throw nre;
        }
    }

    private JsonParserConfig getSingleJsonConfig(String plural) {
        return new JsonParserConfig(null, null, null, StringUtils.capitalize("Id"), true, null);
    }
    
}
