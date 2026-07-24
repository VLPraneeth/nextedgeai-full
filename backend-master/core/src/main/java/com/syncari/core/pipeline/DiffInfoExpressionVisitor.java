package com.syncari.core.pipeline;

import static com.syncari.utils.I18n.i18n;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Stack;
import java.util.stream.Collectors;
import org.bson.types.ObjectId;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.MappingNode;
import com.syncari.core.pipeline.expression.And;
import com.syncari.core.pipeline.expression.Contains;
import com.syncari.core.pipeline.expression.Empty;
import com.syncari.core.pipeline.expression.Equal;
import com.syncari.core.pipeline.expression.EqualIgnoreCase;
import com.syncari.core.pipeline.expression.GreaterThan;
import com.syncari.core.pipeline.expression.GreaterThanEqual;
import com.syncari.core.pipeline.expression.In;
import com.syncari.core.pipeline.expression.LessThan;
import com.syncari.core.pipeline.expression.LessThanEqual;
import com.syncari.core.pipeline.expression.LiteralExpression;
import com.syncari.core.pipeline.expression.Not;
import com.syncari.core.pipeline.expression.NotEmpty;
import com.syncari.core.pipeline.expression.NotEqual;
import com.syncari.core.pipeline.expression.NotIn;
import com.syncari.core.pipeline.expression.NotStartsWith;
import com.syncari.core.pipeline.expression.Or;
import com.syncari.core.pipeline.expression.StartsWith;
import com.syncari.core.pipeline.expression.VariableExpression;
import com.syncari.core.pipeline.expression.dedupe.ConcatExpression;
import com.syncari.core.pipeline.expression.dedupe.FieldLevelExpression;
import com.syncari.core.pipeline.expression.dedupe.FirstMatchingValueExpression;
import com.syncari.core.pipeline.expression.dedupe.FirstNotMatchingExpression;
import com.syncari.core.pipeline.expression.dedupe.HighestValueBinaryExpression;
import com.syncari.core.pipeline.expression.dedupe.LatestCreatedValueBinaryExpression;
import com.syncari.core.pipeline.expression.dedupe.LatestUpdatedValueBinaryExpression;
import com.syncari.core.pipeline.expression.dedupe.LeastFrequentValueBinaryExpression;
import com.syncari.core.pipeline.expression.dedupe.LowestValueBinaryExpression;
import com.syncari.core.pipeline.expression.dedupe.MostFrequestValueBinaryExpression;
import com.syncari.core.pipeline.expression.dedupe.OldestCreatedValueBinaryExpression;
import com.syncari.core.pipeline.expression.dedupe.OldestUpdatedValueBinaryExpression;
import com.syncari.core.repositories.customer.MappingNodeRepo;
import com.syncari.core.service.SchemaService;
import com.syncari.core.token.TokenHelper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DiffInfoExpressionVisitor extends SimpleExpressionVisitor {
	private Stack<String> values = new Stack<>();
	private GraphContext context;
	private SchemaService schemaService;
	private MappingNodeRepo nodeRepo;
	private TokenHelper tokenHelper;
	
	public DiffInfoExpressionVisitor(SchemaService schemaService, MappingNodeRepo nodeRepo) {
	    this(null, schemaService, nodeRepo, null);
	}
	public DiffInfoExpressionVisitor(GraphContext context, SchemaService schemaService, MappingNodeRepo nodeRepo, TokenHelper tokenHelper) {
        this.context = context;
        this.schemaService = schemaService;
        this.nodeRepo = nodeRepo;
        this.tokenHelper = tokenHelper;
    }
	
	public String getValue() {
        return values.size() == 0? "<<empty>>" : values.pop();
    }
	
	private boolean isTokenResolutionRequired() {
	  return this.tokenHelper != null && this.context != null;
	}
	
	@Override
	public void visit(VariableExpression exp) {
		boolean alreadyPushed = false;
		String variableName = exp.getVariableName();
		if (schemaService != null && variableName != null && variableName.startsWith("field_")) {
			String[] splitStr = variableName.split("_");
			Optional<String> attributeId = Optional.empty();
			if (splitStr.length == 2)
				attributeId = Optional.of(splitStr[1]);
			if (attributeId.isPresent()) {
				Optional<AttributeDefinition> attributeDefinition = schemaService.findAttribute(attributeId.get());
				if (attributeDefinition.isPresent()) {
					values.push(attributeDefinition.get().getDisplayName());
					alreadyPushed = true;
				}
			}
		} else if (schemaService != null && variableName != null && variableName.startsWith("output_")) {
			String[] splitStr = variableName.split("_");
			Optional<String> nodeIdFragment = Optional.empty();
			if (splitStr.length == 2)
				nodeIdFragment = Optional.of(splitStr[1]);
			if (nodeIdFragment.isPresent()) {
				var nodeId = nodeIdFragment;
                if (nodeIdFragment.get().endsWith(".x.typedValue")) {
                  nodeId = Optional.of(nodeIdFragment.get().substring(0,
                      nodeIdFragment.get().indexOf(".x.typedValue")));
                } else if (nodeIdFragment.get().endsWith(".x.lookupResult")) {
                  nodeId = Optional.of(nodeIdFragment.get().substring(0,
                      nodeIdFragment.get().indexOf(".x.lookupResult")));
                }
				Optional<MappingNode> node = nodeRepo.findById(nodeId.get());
				if (node.isPresent()) {
					values.push(node.get().getName());
					alreadyPushed = true;
				}
			}
		} else if (schemaService != null && variableName != null && variableName.startsWith("action_output_")) {
			// Handle action_output_<nodeId>_<status> pattern
			String[] splitStr = variableName.split("_");
			if (splitStr.length >= 3) {
				// Extract nodeId (everything between "action_output_" and last "_")
				String nodeId = variableName.substring("action_output_".length());
				int lastUnderscoreIndex = nodeId.lastIndexOf("_");
				if (lastUnderscoreIndex > 0) {
					String actualNodeId = nodeId.substring(0, lastUnderscoreIndex);
					String suffix = nodeId.substring(lastUnderscoreIndex + 1);
					
					Optional<MappingNode> node = nodeRepo.findById(actualNodeId);
					if (node.isPresent()) {
						values.push(node.get().getName() + " " + suffix);
						alreadyPushed = true;
					}
				}
			}
		} else if (variableName != null && variableName.trim().startsWith("syncari_findInList")) {
			values.push(i18n(variableName));
			alreadyPushed = true;
		} else if(variableName instanceof String && isTokenResolutionRequired()) {
          if(tokenHelper.hasTokens((String) variableName)) {
            variableName = tokenHelper.resolveTokens(context, (String) variableName);
          }
        }
		if (!alreadyPushed) {
			values.push(variableName);
		}
	}
	
	@Override
	public void visit(LiteralExpression exp) {
		var literal = exp.getValue();
		if (literal instanceof Map) {
			Map literalMap = (Map) literal;
			if (literalMap.containsKey("retainfields")) {
				List<String> fieldIds = (List<String>) literalMap.get("retainfields");
				String retainfield = String.format("(%s)",
						String.join(", ",
								fieldIds.stream()
										.map(fieldId -> schemaService.findAttribute(fieldId)
												.orElse(new AttributeDefinition()).getDisplayName())
										.collect(Collectors.toList())));
				values.push(retainfield);
				return;
			}
		} else if(literal != null && ObjectId.isValid(literal.toString()) && schemaService != null) {
			var attrib = schemaService.findAttribute(literal.toString());
			if(attrib.isPresent()) {
				values.push(attrib.get().getDisplayName());
				return;
			}
		} else if(literal instanceof String && isTokenResolutionRequired()) {
		  if(tokenHelper.hasTokens((String) literal)) {
		    literal = tokenHelper.resolveTokensObject(context, (String) literal);
		  }
		}
		values.push(literal != null ? literal.toString() : null);
	}
	
	@Override
	public void visit(And exp) {
        values.push(convertBinaryWithoutBracket(exp.getName()));
	}
	
	@Override
	public void visit(Equal exp) {
		values.push(convertBinary(exp.getName()));
	}
	
	@Override
	public void visit(Or exp) {
		values.push(convertBinaryWithoutBracket(exp.getName()));
	}
	
//	@Override
//	public void visit(If exp) {
//  }

	@Override
    public void visit(EqualIgnoreCase exp) {
    	values.push(convertBinary(exp.getName()));
    }

	@Override
    public void visit(GreaterThan exp) {
    	values.push(convertBinary(exp.getName()));
    }

	@Override
    public void visit(LessThan exp) {
    	values.push(convertBinary(exp.getName()));
    }

	@Override
    public void visit(NotEqual exp) {
    	values.push(convertBinary(exp.getName()));
    }

	@Override
    public void visit(Not exp) {
		values.push(convertUnary(exp.getName()));
    }

//	@Override
//    public void visit(FilterFailedExpression exp) {
//    }

    public void visit(Empty exp) {
    	values.push(convertUnary(exp.getName()));
    }

    public void visit(NotEmpty exp) {
    	values.push(convertUnary(exp.getName()));
    }

//    @Override
//    public void visit(BetweenExpression betweenExpression) {
//    	
//    }

    @Override
    public void visit(GreaterThanEqual exp) {
    	values.push(convertBinary(exp.getName()));
    }

    @Override
    public void visit(LessThanEqual exp) {
    	values.push(convertBinary(exp.getName()));
    }

    @Override
    public void visit(Contains exp) {

    	values.push(convertBinary(exp.getName()));
    }

    @Override
    public void visit(StartsWith exp) {
    	values.push(convertBinary(exp.getName()));
    }

    public void visit(NotStartsWith exp) {
    	values.push(convertBinary(exp.getName()));
    }

    @Override
    public void visit(NotIn exp) {
    	values.push(convertBinary(exp.getName()));
    }

    @Override
    public void visit(In exp) {
    	values.push(convertBinary(exp.getName()));
    }
    
    @Override
    public void visit(FieldLevelExpression exp) {
    	if(exp.getOperand() instanceof VariableExpression) {
    		visit((VariableExpression)exp.getOperand());
    		values.push(String.format("[%s (%s)]", values.pop(), i18n(exp.getName())));
    	} else {
    		values.push(i18n(exp.getName()));
    	}
    }
    
    @Override
    public void visit(FirstMatchingValueExpression exp) {
    	values.push(convertBinary(exp.getName()));
    }
    
    @Override
    public void visit(FirstNotMatchingExpression exp) {
    	values.push(convertBinary(exp.getName()));
    }
    
    @Override
    public void visit(HighestValueBinaryExpression exp) {
    	values.push(convertBinary(exp.getName()));
    }
    
    @Override
    public void visit(LatestCreatedValueBinaryExpression exp) {
    	values.push(convertBinary(exp.getName()));
    }
    
    @Override
    public void visit(LatestUpdatedValueBinaryExpression exp) {
    	values.push(convertBinary(exp.getName()));
    }
    
    @Override
    public void visit(LeastFrequentValueBinaryExpression exp) {
    	values.push(convertBinary(exp.getName()));
    }
    
    @Override
    public void visit(LowestValueBinaryExpression exp) {
    	values.push(convertBinary(exp.getName()));
    }
    
    @Override
    public void visit(MostFrequestValueBinaryExpression exp) {
    	values.push(convertBinary(exp.getName()));
    }
    
    @Override
    public void visit(OldestCreatedValueBinaryExpression exp) {
    	values.push(convertBinary(exp.getName()));
    }
    
    @Override
    public void visit(OldestUpdatedValueBinaryExpression exp) {
    	values.push(convertBinary(exp.getName()));
    }
    
    @Override
    public void visit(ConcatExpression exp) {
    	values.push(convertBinary(exp.getName()));
    }
	
	private String convertBinary(String op) {
		return convertBinary(op, i18n("diff_info_op_" + op));
	}
	
	private String convertBinary(String op, String opLabel) {
		var right = values.pop();
		var left = values.pop();
		return String.format("[%s %s %s]", left, i18n("diff_info_op_" + op), right);
	}
	
	private String convertBinaryWithoutBracket(String op) {
		var right = values.pop();
		var left = values.pop();
		return String.format("%s %s %s", left, i18n("diff_info_op_" + op), right);
	}
	
	private String convertUnary(String op) {
		return String.format("[%s %s]", i18n("diff_info_op_" + op), values.pop());
	}
}
