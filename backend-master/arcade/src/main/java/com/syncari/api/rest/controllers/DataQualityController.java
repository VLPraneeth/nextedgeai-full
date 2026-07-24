package com.syncari.api.rest.controllers;

import static com.syncari.core.security.Permissions.*;

import java.util.*;
import java.util.stream.Collectors;

import com.syncari.api.rest.controllers.data.DFIFeatureDTO;
import com.syncari.core.dfiv2.DFIConstants;
import com.syncari.core.model.DataQualityCategory;
import com.syncari.core.model.DataQualityRule;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.exceptions.ResourceConflictException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

import com.syncari.api.rest.controllers.data.DataQualityCategoryDTO;
import com.syncari.api.rest.controllers.data.DataQualityRuleDTO;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.service.DataQualityService;
import com.syncari.core.service.MappingGraphService;
import com.syncari.core.service.UserService;
import com.syncari.utils.KeyValue;


import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping(value = "/api/v1/dfi/v2")
public class DataQualityController {

    @Autowired
    MappingGraphService graphService;

    @Autowired
	  private UserService userService;

    @Autowired
    DataQualityService dataQualityService;

    @Autowired
    TokenController tokenController;

    @ExceptionHandler(ResourceConflictException.class)
    public ResponseEntity<Map<String, String>> handleResourceConflict(ResourceConflictException ex) {
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("error", "Conflict");
        errorResponse.put("message", ex.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }

    @Secured(READ_STUDIO)
    @GetMapping("/referenceDataSets")
    public KeyValue getReferenceDataSetOptions() {
        Map<String, List<String>> dataSetOptions = dataQualityService.getReferenceMetaDataOptions();
        List<KeyValue> options = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : dataSetOptions.entrySet()) {
            for (String field : entry.getValue()) {
                String label = entry.getKey() + " / " + field;
                String value = entry.getKey() + "/" + field;
                options.add(new KeyValue().set("label", label).set("value", value));
            }
        }
        return new KeyValue().set("referenceDataSets", options);
    }

    @Secured(READ_STUDIO)
    @GetMapping("/dfiProvisionStatus/{syncariEntityId}/{draftStatus}")
    public DFIFeatureDTO getDFIProvisionStatus(@PathVariable String syncariEntityId, @PathVariable String draftStatus) {
        var mappingGraph = getMappingGraph(syncariEntityId, draftStatus);
        String dfiStatus = DFIConstants.DFI_PROVISION_STATUS_DISABLED;
        if (mappingGraph.isPresent()) {
            dfiStatus = dataQualityService.getDFIProvisionStatus(mappingGraph.get());
        } else
            log.error("graph doesn't exist for entity {} draft status {}", syncariEntityId, draftStatus);
        return new DFIFeatureDTO().setStatus(dfiStatus);
    }

    @Secured(READ_STUDIO)
    @GetMapping("/categories")
    public List<DataQualityCategoryDTO> getCategories() {
      return toDataQualityCategoriesDTO(dataQualityService.getAllCategories());
    }

    @Secured(WRITE_STUDIO)
    @PostMapping("/categories")
    public List<DataQualityCategoryDTO> saveCategories(@RequestBody List<DataQualityCategoryDTO> dqCategoriesDTO) {
      return toDataQualityCategoriesDTO(dataQualityService.batchUpdateCategories(toDataQualityCategories(dqCategoriesDTO)));
    }

    @Secured(WRITE_STUDIO)
    @DeleteMapping("/categories/{categoryId}")
    public void deleteCategory(@PathVariable String categoryId) {
      dataQualityService.deleteCategory(categoryId);
    }

    @Secured(READ_STUDIO)
    @GetMapping("/rules/{syncariEntityId}/metadata")
    public KeyValue getRulesMetadata(@PathVariable String syncariEntityId) {
        var graph = getMappingGraph(syncariEntityId, DraftStatus.NEW.toString());
        List<KeyValue> tempTokens = graph.isPresent() ? tokenController.getTempVariableTokens(graph.get(), graph.get().getCoreNode()) : List.of();
        return dataQualityService.getCreateRuleMetadata(syncariEntityId, tempTokens);
    }

    @Secured(READ_STUDIO)
    @GetMapping("/rules/{syncariEntityId}/{draftStatus}")
    public List<DataQualityRuleDTO> getRule(@PathVariable String syncariEntityId, @PathVariable String draftStatus) {

      var mappingGraph = getMappingGraph(syncariEntityId, draftStatus);

      if (mappingGraph.isPresent()) {
        return toDataQualityRulesDTO(dataQualityService.getAllRules(mappingGraph.get()));
      }

      return List.of();
    }

    @Secured(WRITE_STUDIO)
    @PostMapping("/rules/{syncariEntityId}/{draftStatus}")
    public DataQualityRuleDTO saveRule(@PathVariable String syncariEntityId, @PathVariable String draftStatus,  @RequestBody DataQualityRuleDTO dqRuleDTO) {

      var mappingGraph = getMappingGraph(syncariEntityId, draftStatus);
      if (mappingGraph.isEmpty())
          throw new SyncariValidationException("Pipeline must exist to create dfi rules");

      return toDataQualityRuleDTO(dataQualityService.saveRule(mappingGraph.get(), toDataQualityRule(syncariEntityId, dqRuleDTO, mappingGraph.get())));
    }

    @Secured(WRITE_STUDIO)
    @DeleteMapping("/rules/{syncariEntityId}/{draftStatus}/{ruleId}")
    public void deleteRule(@PathVariable String syncariEntityId, @PathVariable String draftStatus,  @PathVariable String ruleId) {
 
      var mappingGraph = getMappingGraph(syncariEntityId, draftStatus);

      if (mappingGraph.isPresent()) {
        dataQualityService.deleteRule(syncariEntityId, ruleId);
      }

    }

    private List<DataQualityRuleDTO> toDataQualityRulesDTO(List<DataQualityRule> dqRules) {
      var dqRulesDTO = dqRules.stream()
      .map(d -> {
        return toDataQualityRuleDTO(d);
      })
      .collect(Collectors.toList());
      return dqRulesDTO;
    }

    private DataQualityRuleDTO toDataQualityRuleDTO(DataQualityRule dqRule) {
      var dqRuleDTO = new DataQualityRuleDTO();
      Integer failed = dqRule.getFailed() == null ? 0 : dqRule.getFailed();
      Integer passed = dqRule.getPassed() == null ? 0 : dqRule.getPassed();

      dqRuleDTO.setId(dqRule.getId())
              .setName(dqRule.getName())
              .setCategory(dqRule.getCategory())
              .setPolicy(dqRule.getPolicy())
              .setScope(dqRule.getScope())
              .setScopeType(dqRule.getScopeType())
              .setRuleConfig(dqRule.getRuleConfig())
              .setPassed(passed)
              .setFailed(failed)
              .setTotal(passed + failed)
              .setUpdatedBy(dqRule.getUpdatedBy() != null ? getUserDisplayName(dqRule.getUpdatedBy()) : null)
              .setUpdatedAt(dqRule.getUpdatedAt())
              .setCreatedBy(dqRule.getCreatedBy() != null ? getUserDisplayName(dqRule.getCreatedBy()) : null)
              .setCreatedAt(dqRule.getCreatedAt());
      return dqRuleDTO;
    }

    private DataQualityRule toDataQualityRule(String entityId, DataQualityRuleDTO dqRuleDTO, MappingGraph mappingGraph) {
      DataQualityRule dqRule = new DataQualityRule();
      dqRule.setId(dqRuleDTO.getId());
      dqRule.setCategory(dqRuleDTO.getCategory());
      dqRule.setPolicy(dqRuleDTO.getPolicy());
      dqRule.setName(dqRuleDTO.getName());
      dqRule.setRuleConfig(dqRuleDTO.getRuleConfig());
      dqRule.setScope(dqRuleDTO.getScope());
      dqRule.setScopeType(dqRuleDTO.getScopeType());
      dqRule.setEntityId(entityId);
      dqRule.setMappingGraphId(mappingGraph.getId());
      return dqRule;
    }

    private List<DataQualityCategoryDTO> toDataQualityCategoriesDTO(List<DataQualityCategory> dqCategories) {
      var dqCategoriesDTO = dqCategories.stream()
      .map(d -> {
        return toDataQualityCategoryDTO(d);
      })
      .collect(Collectors.toList());
      return dqCategoriesDTO;
    }

    private DataQualityCategoryDTO toDataQualityCategoryDTO(DataQualityCategory dqCategory) {
      var dqCategoryDTO = new DataQualityCategoryDTO();
      dqCategoryDTO.setId(dqCategory.getId())
        .setName(dqCategory.getName())
        .setUpdatedBy(dqCategory.isSeeded() ? "System" : getUserDisplayName(dqCategory.getUpdatedBy()))
        .setUpdatedAt(dqCategory.getUpdatedAt())
        .setType(dqCategory.isCustom() ? DFIConstants.SCOPE_TYPE_CUSTOM : DFIConstants.SCOPE_TYPE_SYSTEM)
        .setCreatedBy(dqCategory.isSeeded() ? "System" : getUserDisplayName(dqCategory.getCreatedBy()))
        .setCreatedAt(dqCategory.getCreatedAt());
      return dqCategoryDTO;
    }

    private String getUserDisplayName(String userId) {
      var optUser = userService.findUserById(userId);
      if (optUser.isPresent()) {
        var user = optUser.get();
      
        if (user.getFirstName().isBlank() || user.getLastName().isBlank()) {
          return user.getEmail();
        }
        return user.getFirstName() + " " + user.getLastName();
      }
      return userId;
    }

    private List<DataQualityCategory> toDataQualityCategories(List<DataQualityCategoryDTO> dqCategoriesDTO) {
      var dqCategories = dqCategoriesDTO.stream()
      .map(d -> {
        return toDataQualityCategory(d);
      })
      .collect(Collectors.toList());
      return dqCategories;
    }

    private DataQualityCategory toDataQualityCategory(DataQualityCategoryDTO dqCategoryDTO) {
      var dqCategory = new DataQualityCategory();
        dqCategory.setId(dqCategoryDTO.getId());
        dqCategory.setName(dqCategoryDTO.getName());
        dqCategory.setCustom(dqCategoryDTO.getType().equalsIgnoreCase(DFIConstants.SCOPE_TYPE_CUSTOM));
      return dqCategory;
    }

    private Optional<MappingGraph> getMappingGraph(String syncariEntityId, String draftStatus) {
      DraftStatus status = StringUtils.isBlank(draftStatus) ? DraftStatus.NEW : DraftStatus.valueOf(draftStatus.toUpperCase());
      final Optional<MappingGraph> mappingGraph = graphService.retrieveEntityGraph(syncariEntityId, status);
      return mappingGraph;
    }
}