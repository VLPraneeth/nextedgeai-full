package com.syncari.api.rest.controllers;

import static com.syncari.core.security.Permissions.ANALYTICS;

import java.io.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.pagination.Page;
import com.syncari.core.model.pagination.PageCursor;
import lombok.AllArgsConstructor;
import lombok.Data;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.QuoteMode;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

import com.syncari.analytics.service.AnalyticsService;
import com.syncari.analytics.service.data.ApiUsage;
import com.syncari.analytics.service.data.DataMetrics;
import com.syncari.analytics.service.data.Direction;
import com.syncari.analytics.service.data.MetricOverTime;
import com.syncari.analytics.service.data.ReportRequest;
import com.syncari.analytics.service.data.SchemaReport;
import com.syncari.core.model.misc.PageRequest;
import com.syncari.core.model.misc.SyncError;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.SchemaService;
import com.syncari.utils.DateUtil;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.User;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/report")
public class AnalyticsController {
	private static final String DATE_FORMAT = "MM/dd HH:mm";
	private static final String TIME_ONLY_FORMAT = "HH:mm";
	private static List<String> headers = List.of("SyncariEntityName","SyncariRecordId","ExternalEntityName", "ExternalRecordId",
			"ConnectorId","ConnectorName","Operation",
			"ErrorCode","ErrorDetails","OccuredTime");

	@Autowired
	AnalyticsService service;
	@Autowired
	DateUtil dateUtil;

	@Secured(ANALYTICS)
	@RequestMapping(method = RequestMethod.GET, value = "/syncThroughput/{pageNumber}/{startDate}/{endDate}/{type}/{connectorName}")
	public List<MetricOverTime> getSyncThroughput(@PathVariable int pageNumber, @PathVariable String startDate,
			@PathVariable String endDate, @PathVariable String type, @PathVariable String connectorName) {
		User currentUser = SyncariContext.getUser();
		String timeZone = currentUser.getTimeZone();
		List<MetricOverTime> results = service.getSyncThroughput(new PageRequest(pageNumber, 10),
				dateUtil.toInstant(startDate, timeZone), dateUtil.toInstant(endDate, timeZone),
				Direction.valueOf(type == null ? "all" : type.toLowerCase()), connectorName);
		return formatResults(results);
	}

	@Secured(ANALYTICS)
	@RequestMapping(method = RequestMethod.GET, value = "/syncLatency/{pageNumber}/{startDate}/{endDate}")
	public List<MetricOverTime> getSyncLatency(@PathVariable int pageNumber, @PathVariable String startDate,
			@PathVariable String endDate) {
		User currentUser = SyncariContext.getUser();
		String timeZone = currentUser.getTimeZone();
		List<MetricOverTime> results = service.getSyncLatency(new ReportRequest(new PageRequest(pageNumber, 10),
				dateUtil.toInstant(startDate, timeZone), dateUtil.toInstant(endDate, timeZone), null));
		return formatResults(results);
	}

	@Secured(ANALYTICS)
	@RequestMapping(method = RequestMethod.GET, value = "/synapseUsage/{pageNumber}/{startDate}/{endDate}/{connectorName}/{operation}")
	public List<ApiUsage> getSynapseUsage(@PathVariable int pageNumber, @PathVariable String startDate,
			@PathVariable String endDate, @PathVariable String connectorName, @PathVariable String operation) {
		User currentUser = SyncariContext.getUser();
		String timeZone = currentUser.getTimeZone();
		return service.getSynapseUsage(new PageRequest(pageNumber, 10),
				dateUtil.toInstant(startDate, timeZone), dateUtil.toInstant(endDate, timeZone),
				connectorName, operation);
	}

	@Secured(ANALYTICS)
	@RequestMapping(method = RequestMethod.GET, value = "/synapseLatency/{pageNumber}/{startDate}/{endDate}/{connectorName}")
	public List<MetricOverTime> getSynapseLatency(@PathVariable int pageNumber, @PathVariable String startDate,
			@PathVariable String endDate, @PathVariable String connectorName) {
		User currentUser = SyncariContext.getUser();
		String timeZone = currentUser.getTimeZone();
		List<MetricOverTime> results = service.getSynapseLatency(new PageRequest(pageNumber, 10),
				dateUtil.toInstant(startDate, timeZone), dateUtil.toInstant(endDate, timeZone), connectorName);
		return formatResults(results);
	}

	@Secured(ANALYTICS)
	@RequestMapping(method = RequestMethod.GET, value = "/syncErrors/{startDate}/{endDate}")
	public Page<SyncError> getSyncErrors(
			@PathVariable String startDate,
			@PathVariable String endDate,
			@RequestParam(required = false) String connectorName,
			@RequestParam(required = false, defaultValue = "0") int pageNumber,
			@RequestParam(required = false) String operation,
			@RequestParam(required = false) String syncariEntityName,
			@RequestParam(required = false) String syncariRecordId,
			@RequestParam(required = false) String timeZone,
			@RequestParam int count
    ) {
		String currentUserTimezone = SyncariContext.getUser().getTimeZone();
		String defaultTimeZone = "UTC";
		String timeZoneToUse = StringUtils.isNotEmpty(timeZone) ? DateUtil.isValidTimeZone(timeZone) ? timeZone : DateUtil.isValidTimeZone(currentUserTimezone) ? currentUserTimezone : defaultTimeZone
				: DateUtil.isValidTimeZone(currentUserTimezone) ? currentUserTimezone : defaultTimeZone ;
		return service.getSyncErrors(
            new PageCursor(pageNumber, count),
            dateUtil.toInstant(startDate, timeZoneToUse),
            dateUtil.toInstant(endDate, timeZoneToUse),
            connectorName,
            operation,
            syncariEntityName,
            syncariRecordId
        );
	}

	@Secured(ANALYTICS)
	@RequestMapping(method = RequestMethod.POST, value = "/syncErrors/{syncCycleId}/{nodeId}")
	public Page<SyncError> getSyncErrors(
			@PathVariable String syncCycleId,
			@PathVariable String nodeId,
			@RequestParam(required = false, defaultValue = "0") int pageNumber,
			@RequestParam int count,
			@RequestBody Map<String, String> errorDetails
	) {
		return service.getSyncErrors(
				new PageCursor(pageNumber, count),
				syncCycleId,
				nodeId,
				errorDetails.get("message")
		);
	}

	@Secured(ANALYTICS)
	@RequestMapping(value = "/syncErrors/download", method = RequestMethod.POST, consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE})
	public ResponseEntity<Resource> download(@RequestParam String startDate,
											 @RequestParam String endDate,
											 @RequestParam(required = false) String connectorName,
											 @RequestParam(required = false) String operation,
											 @RequestParam(required = false) String syncariEntityName,
											 @RequestParam(required = false) String syncariRecordId,
											 @RequestParam(required = false, defaultValue = "UTC") String timeZone) {

		if(StringUtils.isBlank(startDate) || StringUtils.isBlank(endDate)) {
			throw new SyncariValidationException("Start and End date are required");
		}
		DownloadRequest request = new DownloadRequest(connectorName, syncariRecordId, syncariEntityName, operation, startDate, endDate, timeZone);
		InputStreamResource resource = new InputStreamResource(new QueryCSVInputStream(request));
		return ResponseEntity.ok()
				.contentType(MediaType.TEXT_PLAIN)
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + "syncerrors_"+ startDate+"_"+endDate + ".csv\"")
				.body(resource);
	}

	@Secured(ANALYTICS)
	@RequestMapping(method = RequestMethod.GET, value = "/dataMetrics/{pageNumber}/{entityName}")
	public List<DataMetrics> dataMetrics(@PathVariable int pageNumber, @PathVariable String entityName) {
		return service.getDataMetrics(new PageRequest(pageNumber, 10), entityName);
	}

	@Secured(ANALYTICS)
	@RequestMapping(method = RequestMethod.GET, value = "/schemaReport/{pageNumber}/{connectorId}")
	public List<SchemaReport> entityMapping(@PathVariable int pageNumber, @PathVariable String connectorId) {
		return service.getSchemaReport(new PageRequest(pageNumber, 10), connectorId);
	}

	private Instant toInstant(String dateString, int addDays) {
		LocalDate localDate = LocalDate.parse(dateString);
		if (addDays > 0)
			localDate = localDate.plusDays(addDays);
		LocalDateTime localDateTime = localDate.atStartOfDay();
		return localDateTime.toInstant(ZoneOffset.UTC);
	}

	private List<MetricOverTime> formatResults(List<MetricOverTime> results) {
		results.stream().forEach(r -> {
			if(r.isByHour()) {
				r.setTimeString(dateUtil.formatDate(Instant.ofEpochMilli(r.getTime()), TIME_ONLY_FORMAT));
			} else {
				r.setTimeString(dateUtil.formatDate(Instant.ofEpochMilli(r.getTime()), DATE_FORMAT));
			}
		});
		return results;
	}

	@Data
	@AllArgsConstructor
	class DownloadRequest {
		String connectorName;
		String syncariRecordId;
		String syncariEntityName;
		String operation;
		String startDate;
		String endDate;
		String timeZone;
	}

	class QueryCSVInputStream extends InputStream {
		private  int pageNumber;
		private  DownloadRequest request;
		private ByteArrayInputStream bin;
		private final CSVPrinter csvPrinter;
		private final StringWriter csvBuffer;
		private boolean completed;

		public QueryCSVInputStream(DownloadRequest request){
			try {
				this.request = request;
				csvBuffer = new StringWriter();
				csvPrinter = new CSVPrinter(csvBuffer, CSVFormat.DEFAULT
						.withHeader(headers.toArray(new String[headers.size()])).withQuoteMode(QuoteMode.ALL));
			}catch(IOException ex){
				throw new RuntimeException(ex);
			}
			readPage();
		}

		private void readPage(){
			String timeZone = DateUtil.isValidTimeZone(request.timeZone) ? request.timeZone : "UTC";
			Page<SyncError> page = service.getSyncErrors(
					new PageCursor(pageNumber, 1000),
					dateUtil.toInstant(request.startDate, timeZone),
					dateUtil.toInstant(request.endDate, timeZone),
					request.connectorName,
					request.operation,
					request.syncariEntityName,
					request.syncariRecordId
			);
			writeToBuffer(page.getRecords());
			pageNumber = page.getPageInfo().getPageNumber() + 1;
		}

		private void writeToBuffer(List<SyncError> records) {
			records.forEach(error -> {
				List<String> record = new ArrayList<>();
				record.add(error.getSyncariEntityName());
				record.add(error.getSyncariRecordId());
				record.add(error.getExternalEntityName());
				record.add(error.getExternalRecordId());
				record.add(error.getConnectorId());
				record.add(error.getConnectorName());
				record.add(error.getOperation());
				record.add(error.getErrorCode());
				record.add(error.getErrorDetails());
				record.add(error.getOccuredTime().toString());
				try {
					csvPrinter.printRecord(record);
				} catch (IOException e) {
					log.warn("Error Export: cannot write row "+record,e);
				}
			});
			try {
				if(csvBuffer.getBuffer().length() >0) {
					bin = new ByteArrayInputStream(csvBuffer.getBuffer().toString().getBytes("utf-8"));
					csvBuffer.getBuffer().setLength(0);
				}else{
					log.info("CSV Buffer length is 0");
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
				log.info("bin is null so not calling read page");
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
}
