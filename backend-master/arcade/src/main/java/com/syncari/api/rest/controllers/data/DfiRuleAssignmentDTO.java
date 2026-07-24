package com.syncari.api.rest.controllers.data;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.syncari.core.model.DfiRuleAssignment;
import com.syncari.core.model.RuleAssignment;
import com.syncari.core.model.RuleDefinition;
import com.syncari.core.schema.EntityDef;
import com.syncari.core.service.DfiRuleAssignmentService;
import com.syncari.core.service.SchemaService;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class DfiRuleAssignmentDTO implements BaseDTO<DfiRuleAssignment, DfiRuleAssignmentDTO> {
    public String entityId;
    public String entityApiName;
    public boolean initializing;
    public Set<RuleAssignment> rules = new TreeSet<>();
    List<RuleDefinition> ruleDefinitions = new ArrayList<>();
    EntityDef entityDef;
    public Date lastPublished;
    public Set<String> deletedRuleIds = new TreeSet<>();
    // TODO: Autowired does not work, investigate
    private DfiRuleAssignmentService dfiRuleAssignmentService;
    private SchemaService schemaService;

    public DfiRuleAssignmentDTO() {}

    public DfiRuleAssignmentDTO(DfiRuleAssignmentService dfiRuleAssignmentService, 
            SchemaService schemaService) {
        this.dfiRuleAssignmentService = dfiRuleAssignmentService;
        this.schemaService = schemaService;
    }

    public DfiRuleAssignment toDfiRuleAssignment() {
        return new DfiRuleAssignment().setEntityId(entityId).setEntityApiName(entityApiName).setRules(rules);
    }

    @Override
    public DfiRuleAssignmentDTO augment(DfiRuleAssignmentDTO draDTO) {
        // TODO: This needs to be properly filtered by the entity name.
        draDTO.setRuleDefinitions(dfiRuleAssignmentService.findAllRuleDefinitions());
        // Enrich with info from published version, like deleted rule ids and lastpublished.
        Optional<DfiRuleAssignment> published = dfiRuleAssignmentService.findPublished(draDTO.getEntityId());
        if (published.isPresent()) {
            Set<String> publishedRuleIds = published.get().getRules().stream().map(RuleAssignment::getId).collect(Collectors.toSet());
            Set<String> draftRuleIds = draDTO.getRules().stream().map(RuleAssignment::getId).collect(Collectors.toSet());
            publishedRuleIds.removeAll(draftRuleIds);
            if (publishedRuleIds.size() > 0) {
                draDTO.setDeletedRuleIds(publishedRuleIds);
            }
            draDTO.setLastPublished(published.get().getUpdatedAt());
        }
        draDTO.setEntityDef(schemaService.getSchemaByEntityId(draDTO.getEntityId()).getEntities().stream().findFirst().orElse(null));
        return draDTO;
    }
}
