package com.syncari.core.service;

import com.syncari.connector.Constants;
import com.syncari.core.SyncariContext;
import com.syncari.core.exception.NotFoundException;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.ConnectorStatus;
import com.syncari.core.model.misc.InstanceProfileResponse;
import com.syncari.core.model.misc.StreamInfo;
import com.syncari.core.model.pagination.Page;
import com.syncari.core.model.pagination.PageCursor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Slf4j
@Service
public class InstanceService {
	@Autowired
	private ApplicationContext appContext;
	@Autowired
	ConnectorService connectorService;
	@Autowired
	SyncStatusService syncStatusService;
	@Autowired
	EntityRepoService repoService;
	@Autowired
	SchemaService schemaService;
	@Autowired
	SubscriptionService subService;
	@Autowired
	TransactionLogService transactionLogService;
	static final List<String> systemSynapse = List.of(Constants.FILE_DATA, Constants.DATASETS);

	public InstanceProfileResponse getInstanceProfile(String syncariId) {
		Organization org = subService.getOrgBySyncariId(syncariId);
		Instance instance = org.getInstance(syncariId).orElseThrow(() -> new NotFoundException(Instance.class, "syncariId", syncariId));
		InstanceProfileResponse response = new InstanceProfileResponse();
		response.setSyncariId(syncariId);
		SyncariContext.runWithContext(org, instance, SyncariContext.getUser(),()-> {
			Stream<Connector> connectorStream = connectorService.getAllActive().stream().filter(c -> !systemSynapse.contains(c.getMetadata().getName().toLowerCase()));
			connectorStream.forEach(connector -> {
				response.getSynapses().add(connector.getMetadata().getName());
				if(connector.getStatus() == ConnectorStatus.ERROR){
					response.getErrorSynapses().add(connector.getMetadata().getName());
				}
			});
			Stream<StreamInfo> streamInfoStream = syncStatusService.getAllPipelineStreamStatus().stream().filter(s -> s.getStatus() != StreamInfo.Status.UNPUBLISHED);
			streamInfoStream.forEach(streamInfo -> {
					if (streamInfo.getStatus() == StreamInfo.Status.RUNNING){
						response.setNumberOfRunningPipeline(response.getNumberOfRunningPipeline() + 1);
					}
					if (streamInfo.getStatus() == StreamInfo.Status.ERROR){
						response.setNumberOfErrorPipeline(response.getNumberOfErrorPipeline() + 1);
					}
					if (streamInfo.getStatus() == StreamInfo.Status.PAUSED){
						response.setNumberOfPausedPipeline(response.getNumberOfPausedPipeline() + 1);
					}
					response.setTotalPipelines(response.getTotalPipelines() + 1);
			});
			response.setTotalRecords(getTotalRecordCount());
			response.setTransactionsLastWeek(getTxnCount());
		});
		return response;
	}

	private long getTotalRecordCount() {
		long count = 0;
		List<EntityDefinition> entities = schemaService.getEntities(connectorService.getSyncariConnector().getId());
		for(EntityDefinition entity: entities){
			count = count + repoService.getCount(entity.getApiName());
		}
		return count;
	}

	private long getTxnCount() {
		PageCursor cursor = new PageCursor();
		cursor.setPageSize(1000);
		Calendar cal = Calendar.getInstance();
		cal.setTime(new Date());
		cal.add(Calendar.DATE, -7);
		long txnCount = 0;
		Page<TransactionLog> query = transactionLogService.query(cursor, Optional.of(cal.getTime()), Optional.of(new Date()), Optional.empty(), Optional.empty(),
				Optional.empty());
		while(query.getPageInfo().isHasMore()){
			List<TransactionLog> logs = query.getRecords();
			txnCount = txnCount + logs.size();
			cursor = new PageCursor();
			cursor.setPageSize(1000);
			cursor.setCursor(query.getPageInfo().getEnd());
			query = transactionLogService.query(cursor, Optional.of(cal.getTime()), Optional.of(new Date()), Optional.empty(), Optional.empty(),
					Optional.empty());
		}
		return txnCount;
	}

}
