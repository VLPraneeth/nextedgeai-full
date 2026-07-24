package com.syncari.core.validation;

import com.syncari.core.datatype.PredicateType;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.token.TokenHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.syncari.core.utils.ValidationUtils.validateCondition;

@Slf4j
public class TokenValidator {

    private static final List<String> NODE_NAME_TOKEN_PREFIXES = List.of("Lookup From", "Lookup Count From", "Value From", "Action Result From");
    private static Pattern NODE_NAME_REF_PATTERN = Pattern.compile(String.format("\\{\\{(%s) ([^.]+)\\.?(.*)\\}\\}", String.join("|", NODE_NAME_TOKEN_PREFIXES)));
    private static final String ARRAY_USAGE_REGEX = "(\\[.*?])";
    private static Pattern ARRAY_USAGE_IN_NODE_NAME = Pattern.compile(ARRAY_USAGE_REGEX);

    private static void validateNodeReference(String token, ValidationContext validationContext) {
        Matcher matcher = NODE_NAME_REF_PATTERN.matcher(token);
        if (matcher.matches()) {
            String tokenPrefix = matcher.group(1);
            String possibleNodeName = matcher.group(2);
            // if token is format Lookup From <nodename>.values.<fieldname>
            //String nodeName = possibleNodeName.contains(".") ? possibleNodeName.split("\\.")[0] : possibleNodeName;

            // extract node name if it has array usage in it
            // e.g. Action Result From MyActionNode[0].profile.name  --> In this case node name should be 'MyActionNode' and not 'MyActionNode[0]'
            final Matcher m = ARRAY_USAGE_IN_NODE_NAME.matcher(possibleNodeName);
            String nodeName = m.find()
                    ? possibleNodeName.replaceAll(ARRAY_USAGE_REGEX , "")
                    : possibleNodeName;
            log.debug("Possible node name: {} and processed node name: {}", possibleNodeName, nodeName);

            var nodes = validationContext.getTopoSortedNodes();
            var validNode = nodes.stream().takeWhile(node -> !node.getName().equals(validationContext.getNode().getName()))
                    .anyMatch(node-> node.getName().equals(nodeName));
            validateCondition(!validNode, "invalid_node_reference_token", token, validationContext.getNode().getName(), nodeName);
            validateNodeType(tokenPrefix, nodeName, validationContext);
        }
    }

    private static void validateNodeType(String tokenPrefix, String referredNode, ValidationContext validationContext) {
        Optional<MappingNode> nodeMaybe = validationContext.getGraph().getNodeByName(referredNode);
        nodeMaybe.ifPresent(node -> {
            validateCondition(tokenPrefix.equals("Action Result From") && node.getConfiguration().getNodeType() != MappingNodeType.ACTION,
                    "referred_node_invalid_type", referredNode, validationContext.getNode().getName(), node.getConfiguration().getNodeType().toString());
        });
    }

	public static void validateToken(TokenHelper helper, Object value, ValidationContext validationContext) {
		if(value == null) return;
		if (value instanceof String && !StringUtils.isBlank(value.toString())) {
			validateCondition(!helper.isValid(value.toString()), "incomplete_token", validationContext.getNode().getName(),
					validationContext.getGraph().getName());
			TokenHelper.extractTokensFromTemplate(value.toString()).forEach(token -> {
				// identify tokens with node names
                validateCondition(StringUtils.isBlank(token), "invalid_token_node",
                        validationContext.getNode().getName());
                validateNodeReference(token, validationContext);
                validateTokenSyntax(token, validationContext);
            });
        }
        if (value instanceof PredicateType) {
            // TODO
        }
    }

    private static void validateTokenSyntax(String token, ValidationContext validationContext) {
        validateCondition(!TokenHelper.isValidSyntax(token), "invalid_token_syntax", token, validationContext.getNode().getName(), validationContext.getGraph().getName());
    }
}
