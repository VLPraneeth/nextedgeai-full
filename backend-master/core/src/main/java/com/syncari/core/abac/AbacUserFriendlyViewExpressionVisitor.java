package com.syncari.core.abac;

import static com.syncari.utils.I18n.i18n;
import java.util.Stack;
import com.syncari.core.model.abac.ResourceType;
import com.syncari.core.pipeline.SimpleExpressionVisitor;
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
import com.syncari.core.repositories.customer.AbacAttributeRepo;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AbacUserFriendlyViewExpressionVisitor extends SimpleExpressionVisitor {
	private Stack<String> values = new Stack<>();
	private AbacAttributeRepo attributeRepo;
	
	public AbacUserFriendlyViewExpressionVisitor(AbacAttributeRepo attributeRepo) {
        this.attributeRepo = attributeRepo;
    }
	
	public String getValue() {
        return values.size() == 0? "<<empty>>" : values.pop();
    }
	
	@Override
	public void visit(VariableExpression exp) {
	  String variableName = exp.getVariableName();
      if (variableName != null && variableName.startsWith("field_")) {
        variableName = variableName.substring("field_".length());
      }
      if (attributeRepo != null) {
        var attrOpt = attributeRepo.findById(variableName);
        if (attrOpt.isPresent()) {
          var attr = attrOpt.get();
          if (attr.getResourceType() == ResourceType.USER) {
            variableName = String.format("%s.%s", "user", attr.getDisplayName());
          } else {
            variableName = String.format("%s.%s", "resource", attr.getDisplayName());
          }
        }
      }
      values.push(variableName);
		
	}
	
	@Override
	public void visit(LiteralExpression exp) {
	  values.push(String.valueOf(exp.getValue()));
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


    public void visit(Empty exp) {
    	values.push(convertUnary(exp.getName()));
    }

    public void visit(NotEmpty exp) {
    	values.push(convertUnary(exp.getName()));
    }


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
