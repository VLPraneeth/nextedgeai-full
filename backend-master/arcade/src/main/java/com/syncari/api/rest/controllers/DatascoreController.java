package com.syncari.api.rest.controllers;

import static com.syncari.core.security.Permissions.READ_DATA_STUDIO;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.syncari.api.rest.controllers.data.studio.DataScoreCardResponse;
import com.syncari.api.rest.controllers.data.studio.ScoreStatus;
import com.syncari.core.model.DfiRuleAssignment;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.misc.DataScoreCard;
import com.syncari.core.model.misc.Trend;
import com.syncari.core.service.DfiRuleAssignmentService;
import com.syncari.core.service.EntityRepoService;
import com.syncari.core.service.MappingGraphService;
import com.syncari.core.service.SchemaService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/dfi")
public class DatascoreController {
    @Autowired
    EntityRepoService repoService;
    @Autowired
    SchemaService schemaService;
    @Autowired
    MappingGraphService graphService;
    @Autowired
    DfiRuleAssignmentService dfiRuleAssignmentService;

    @Secured(READ_DATA_STUDIO)
    @GetMapping("/entity/{entityId}")
    public DataScoreCardResponse getCard(@PathVariable String entityId, @RequestParam(required = false) boolean includeTrend) {
        DataScoreCardResponse response = new DataScoreCardResponse();
        EntityDefinition entity = schemaService.getEntity(entityId);
        if(!repoService.isDfiEnabled(entity)) {
            response.setStatus(ScoreStatus.na);
            return response;
        }
        if(graphService.retrieveApprovedEntityGraph(entityId).isEmpty()) {
            response.setStatus(ScoreStatus.unpublished);
            return response;
        }
        response.setStatus(ScoreStatus.available);
        DataScoreCard card = repoService.getScoreCard(schemaService.getEntity(entityId));
        if (includeTrend) {
            int rangeInDays = 30;
            int deltaPercent = 0;
            Map<String, Integer> data = repoService.getDfiTrend(entityId, rangeInDays);
            card.setTrend(new Trend(rangeInDays, deltaPercent, data));
        }
        response.setData(card);
        return response;
    }

    @Secured(READ_DATA_STUDIO)
    @GetMapping("/entities")
    public List<String> getDfiEnabledEntities() {
        List<DfiRuleAssignment> dfis = dfiRuleAssignmentService.findAllPublished();
        return dfis.stream().map(x -> x.getEntityApiName()).collect(Collectors.toList());
    }

}