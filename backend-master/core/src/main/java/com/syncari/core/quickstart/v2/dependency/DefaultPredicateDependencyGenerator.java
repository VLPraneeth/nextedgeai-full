package com.syncari.core.quickstart.v2.dependency;

import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.pipeline.expression.LiteralExpression;
import com.syncari.core.pipeline.expression.VariableExpression;
import com.syncari.core.quickstart.v2.PipelineQSConfig;
import com.syncari.core.quickstart.v2.QuickStartContext;
import com.syncari.core.service.SchemaService;
import com.syncari.core.token.TokenHelper;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class DefaultPredicateDependencyGenerator implements PredicateDependencyGenerator {

    SchemaService schemaService;

    private final Pattern FIELD_OUTPUT_PATTERN = Pattern.compile("field_(\\w+)");
    private final Pattern NODE_OUTPUT_PATTERN = Pattern.compile("output_(\\w+)\\.x\\.(\\w+)");
    private final Pattern ACTION_NODE_OUTPUT_PATTERN = Pattern.compile("action_output_(\\w+)\\_(\\w+)");

    @Autowired
    public DefaultPredicateDependencyGenerator(SchemaService schemaService){
        this.schemaService = schemaService;
    }

    @Override
    public void generateDependency(VariableExpression variableExpression, QuickStartContext context) {
        PipelineQSConfig qsConfig = (PipelineQSConfig) context.getQsConfig();
        String variableName = variableExpression.getVariableName();
        Matcher attribMatcher = FIELD_OUTPUT_PATTERN.matcher(variableName);
        Matcher nodeOutputMatcher = NODE_OUTPUT_PATTERN.matcher(variableName);
        Matcher actionOutputMatcher = ACTION_NODE_OUTPUT_PATTERN.matcher(variableName);
        if(ObjectId.isValid(variableName)){
            // if variable is of type ObjectId and a valid attribute reference then add that as dependency
            Optional<AttributeDefinition> attrib = context.getAttribute(variableName);
            attrib.ifPresent(attributeDefinition -> qsConfig.addDependency(DependencyUtil.getAttributeDependency(attributeDefinition)));
        } else if(attribMatcher.matches()){
            // If variable is of pattern field_<attributeId> add that attribute as dependency
            String attribId = attribMatcher.group(1);
            context.getAttribute(attribId).ifPresentOrElse(
                    attrib -> qsConfig.addDependency(DependencyUtil.getAttributeDependency(attrib)),
                    () -> log.warn("Unable to find attribute with id {}, in node {} of pipeline {}", attribId, context.getCurrentNode().getName(), context.getCurrentPipeline().getName()));
        } else if(nodeOutputMatcher.matches()){
            qsConfig.addDependency(DependencyUtil.getNodeOutputDependency(variableName));
        } else if(actionOutputMatcher.matches()){
            qsConfig.addDependency(DependencyUtil.getActionNodeOutputDependency(variableName));
        }
    }

    @Override
    public void generateDependency(LiteralExpression literalExpression, QuickStartContext context) {
        PipelineQSConfig qsConfig = (PipelineQSConfig) context.getQsConfig();
        Object value = literalExpression.getValue();
        // if variable is of type ObjectId then extract the attribute and add that as dependency
        if(ObjectId.isValid(value.toString())){
            // if variable is of type ObjectId and a valid attribute reference then add that as dependency
            Optional<AttributeDefinition> attrib = context.getAttribute(value.toString());
            attrib.ifPresent(attributeDefinition -> qsConfig.addDependency(DependencyUtil.getAttributeDependency(attributeDefinition)));
        }else if (Map.class.isAssignableFrom(value.getClass())){
            // this is first not matching value then iterate over map to get the value.
            Map<String, Object> newMap = new HashMap<>();
            ((Map)value).keySet().forEach(k -> {
                Object val =  ((Map)value).get(k);
                if(ObjectId.isValid(val.toString())){
                    Optional<AttributeDefinition> attrib = context.getAttribute(val.toString());
                    attrib.ifPresent(attributeDefinition -> qsConfig.addDependency(DependencyUtil.getAttributeDependency(attributeDefinition)));
                }else if(TokenHelper.hasTokens(val.toString())){
                    DependencyUtil.getTokenDependencies(val.toString()).forEach(d -> qsConfig.addDependency(d));
                }
            });
        } else if(TokenHelper.hasTokens(value.toString())){
            // only add dependency for tokens of type {{<synapse-name>.<api-source-entity>.<api-field-name>}}
            DependencyUtil.getTokenDependencies(value.toString()).forEach(d -> qsConfig.addDependency(d));
        }
    }
}
