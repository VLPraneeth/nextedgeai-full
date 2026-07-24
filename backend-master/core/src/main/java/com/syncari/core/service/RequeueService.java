package com.syncari.core.service;

import com.syncari.core.model.RequeueRequest;
import com.syncari.core.repositories.customer.RequeueRequestRepo;
import com.syncari.utils.Pair;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class RequeueService {
	@Autowired
	protected RequeueRequestRepo requeueRequestRepo;

	@Autowired
	@Qualifier("defaultEmailService")
	protected EmailService emailService;

	public void requeue(List<RequeueRequest> requeueRequests){
		requeueRequestRepo.upsert(requeueRequests);
	}


	public void cleanupAndNotifyExpiredRequests(String entityDefinitionId, String graphId){
		PageRequest page = PageRequest.of(0, 100, Sort.by("_id"));
		Page<RequeueRequest> expiredRequests = requeueRequestRepo
				.findRequestsBefore(entityDefinitionId, graphId, ZonedDateTime.now(),
						page);
		while(expiredRequests.hasContent()){
			List<RequeueRequest> requests = expiredRequests.getContent();
			notifyExpiration(requests);
			requeueRequestRepo.deleteAll(requests);
			expiredRequests = requeueRequestRepo
					.findRequestsBefore(entityDefinitionId, graphId,ZonedDateTime.now(),
							//dont move the page to next, because we've deleted the current page
							page);
		}
	}

	public void cleanupProcessedRecords(List<RequeueRequest> requeueRequests){
		Map<Pair<String,String>, List<RequeueRequest>> grouped = requeueRequests.stream().collect(Collectors.groupingBy(r -> Pair.of(r.getEntityDefinitionId(), r.getGraphId())));
		grouped.forEach((key, records) ->{
			requeueRequestRepo.deleteProcessed(key.x,key.y,records.stream().map(r->r.getRecordId()).collect(Collectors.toList()));
		});
	}

	private void notifyExpiration(List<RequeueRequest> requests) {
		//TODO:
	}


	public Page<RequeueRequest> findRequeueRequests(String entityDefinitionId, String graphId, Pageable page){
		return requeueRequestRepo.findRequestsAfter(entityDefinitionId, graphId, ZonedDateTime.now(), page);
	}

	public Page<RequeueRequest> findSourceRequeueRequests(String entityDefinitionId, String graphId, Pageable page) {
		return requeueRequestRepo.findRequests(entityDefinitionId, graphId, RequeueRequest.RecordType.SOURCE, ZonedDateTime.now(), page);
	}

	public Page<RequeueRequest> findSourceRequeueRequests(String entityDefinitionId, String graphId) {
		return requeueRequestRepo.findRequests(entityDefinitionId, graphId, RequeueRequest.RecordType.SOURCE, ZonedDateTime.now(),
				PageRequest.of(0, 1000, Sort.by("retryTimeLimit")));
	}

	public Page<RequeueRequest> findExpiredSourceRequeueRequestsToProcess(String entityDefinitionId, String graphId) {
		return findExpiredSourceRequeueRequestsToProcess(entityDefinitionId, graphId, PageRequest.of(0, 1000, Sort.by("retryTimeLimit")));
	}

	public Page<RequeueRequest> findExpiredSourceRequeueRequestsToProcess(String entityDefinitionId, String graphId, Pageable page) {
		return requeueRequestRepo.findExpiredRequestsToProcess(entityDefinitionId, graphId, RequeueRequest.RecordType.SOURCE, ZonedDateTime.now(),
				page);
	}

}
