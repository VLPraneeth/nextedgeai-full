package com.syncari.core.functions;

import com.syncari.core.datatype.Datatype;
import com.syncari.core.datatype.DoubleType;
import com.syncari.core.datatype.IntegerType;
import com.syncari.core.model.*;
import com.syncari.core.pipeline.DiffInfoContext;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.pipeline.expression.PredicateParser;
import com.syncari.core.pipeline.expression.VariableExpression;
import com.syncari.core.service.SchemaService;
import com.syncari.core.validation.*;
import com.syncari.utils.Pair;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

@Component("sumRecords")
public class SumRecordFunction  extends AbstractAggregateFunction {
	@Override
	public List<Pair<String, String>> toUserFriendlyValue(DiffInfoContext context, String configProperty) {
		if (context != null && context.getCurrentNode() != null) {
			if ("value".equals(configProperty)) { // Skip the property
				return List.of();
			}
		}
		return super.toUserFriendlyValue(context, configProperty);
	}
}
