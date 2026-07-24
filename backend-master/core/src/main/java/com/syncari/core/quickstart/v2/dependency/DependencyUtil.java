package com.syncari.core.quickstart.v2.dependency;

import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.Connector;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.ReferenceDataMeta;
import com.syncari.core.model.insights.dataset.Dataset;
import com.syncari.core.quickstart.v2.QSDependency;
import com.syncari.core.token.TokenHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DependencyUtil {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\{\\{(.+?)\\.(.+?)\\.(.+?)\\}\\}");
    private static final Pattern NODE_OUTPUT_PATTERN = Pattern.compile("output_(\\w+)\\.x\\.(\\w+)");
    private static final Pattern ACTION_NODE_OUTPUT_PATTERN = Pattern.compile("action_output_(\\w+)\\_(\\w+)");

    public static QSDependency getAttributeDependency(AttributeDefinition attribute){
        return new QSDependency()
                .setId(attribute.getId())
                .setType(QSDependency.Type.Attribute)
                .setSourceValue(attribute);
    }

    public static QSDependency getEntityDependency(EntityDefinition entity){
        return new QSDependency()
                .setId(entity.getId())
                .setType(QSDependency.Type.Entity)
                .setSourceValue(entity);
    }

    public static QSDependency getConnectorDependency(Connector connector){
        return new QSDependency()
                .setId(connector.getId())
                .setType(QSDependency.Type.Connector)
                .setSourceValue(connector);
    }

    public static List<QSDependency> getTokenDependencies(String token) {
        List<QSDependency> tokenDependencies = new ArrayList<>();
        if(TokenHelper.hasTokens(token)) {
            List<String> extractedTokens = TokenHelper.extractTokensFromTemplate(token);
            extractedTokens.forEach(tok -> {
                Matcher tokenMatcher = TOKEN_PATTERN.matcher(tok);
                if(tokenMatcher.matches()) {
                    tokenDependencies.add(new QSDependency()
                            .setId(tok)
                            .setType(QSDependency.Type.Token)
                            .setSourceValue(tok));
                }
            });
        }
        return tokenDependencies;
    }

    public static QSDependency getRefDatasetDependency(ReferenceDataMeta refDataMeta){
        return new QSDependency()
                .setId(refDataMeta.getId())
                .setType(QSDependency.Type.Reference)
                .setSourceValue(refDataMeta);
    }

    public static QSDependency getDatasetDependency(Dataset dataset){
        return new QSDependency()
                .setId(dataset.getId())
                .setType(QSDependency.Type.Dataset)
                .setSourceValue(dataset);
    }

    public static QSDependency getNodeOutputDependency(String nodeOutputReference){
        Matcher nodeOutputMatcher = NODE_OUTPUT_PATTERN.matcher(nodeOutputReference);
        if(nodeOutputMatcher.matches()) {
            return new QSDependency()
                    .setId(nodeOutputReference)
                    .setType(QSDependency.Type.Node_Output_Ref)
                    .setSourceValue(nodeOutputReference);
        }
        return null;
    }

    public static QSDependency getActionNodeOutputDependency(String nodeOutputReference){
        Matcher actionNodeOutputMatcher = ACTION_NODE_OUTPUT_PATTERN.matcher(nodeOutputReference);
        if(actionNodeOutputMatcher.matches()) {
            return new QSDependency()
                    .setId(nodeOutputReference)
                    .setType(QSDependency.Type.Action_Node_Output_Ref)
                    .setSourceValue(nodeOutputReference);
        }
        return null;
    }
}
