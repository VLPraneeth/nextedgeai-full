package com.syncari.core.model.insights;

import com.syncari.core.exceptions.SyncariValidationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class DecimalNaryQueryFunction extends NaryQueryFunction {

    private AggFunctions function;

    public DecimalNaryQueryFunction(AggFunctions function) {
        this.function = function;
    }

    @Override
    public AggFunctions getQueryFunction() {
        return function;
    }

    @Override
    public String getEscapedName(String escapeChar, Map<String, String> entityIdAliasMap) {
        List<String> escapedColumnsNames = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(innerQueryFields)) {
            escapedColumnsNames.addAll(innerQueryFields.stream()
                    .map(c -> c.getQueryFunction().buildExpression(escapeChar, entityIdAliasMap))
                    .collect(Collectors.toList()));
        }
        if (CollectionUtils.isNotEmpty(this.getColumns())) {
            escapedColumnsNames.addAll(this.getColumns().stream().map(c -> {
                String nameTouse = c.getType() != QField.Type.DATASET ? c.getName().toLowerCase() : c.getName();
                String entityAlias = (MapUtils.isNotEmpty(entityIdAliasMap) && (StringUtils.isNotEmpty(c.getDatasetId()))) ? entityIdAliasMap.get(c.getDatasetId()) : null;
                String escapedQ = (null != entityAlias) ? (c.isLiteral() ? "cast(" + c.getName() + " as decimal)" : "cast(" + "\"" + entityAlias + "\"." + escapeChar + nameTouse + escapeChar + " as decimal)") :
                        (c.isLiteral() ? "cast(" + c.getName() + " as decimal)" : "cast(" + escapeChar + c.getName() + escapeChar + " as decimal)");
                return "trunc(" + escapedQ + ", 2)";

            }).collect(Collectors.toList()));
        }
        if ((CollectionUtils.isEmpty(escapedColumnsNames))) {
            throw new SyncariValidationException("Not valid inputs for function '" + getQueryFunction().name() + "'");
        }
        return toExpression(escapedColumnsNames);
    }

    protected String toExpression(List<String> params) {
        return String.format("%s(%s)", function.name(), String.join(",", params));
    }


    @Override
    public String buildExpression(String escapeChar, Map<String, String> entityIdAliasMap) {
        final String expression = "(" + this.getEscapedName(escapeChar, entityIdAliasMap) + ")";
        return this.getAlias() == null ? expression : expression + " " + "\"" + this.getAlias() + "\"";
    }

    @Override
    public String getDataType() {
        return "double";
    }

    public QueryFunction makeCopy() {
        QueryFunction func = new DecimalNaryQueryFunction(function).setAlias(getAlias()).setDataType(getDataType());
        if (CollectionUtils.isNotEmpty(this.getColumns())) {
            List<QField> cols = new ArrayList<>();
            this.getColumns().forEach(col -> {
                cols.add(col.makeCopy());
            });
            func.setColumns(cols);
        }
        if (CollectionUtils.isNotEmpty(this.getInnerQueryFields())) {
            List<QueryField> cols = new ArrayList<>();
            this.getInnerQueryFields().forEach(col -> {
                cols.add(col.makeCopy());
            });
            ((DecimalNaryQueryFunction) func).setInnerQueryFields(cols);
        }
        return func;
    }

    @Override
    public boolean validate() {
        log.info("Validate called, it will return result {}", CollectionUtils.isNotEmpty(this.getInnerQueryFields()) || CollectionUtils.isNotEmpty(this.getColumns()));
        return CollectionUtils.isNotEmpty(this.getInnerQueryFields()) || CollectionUtils.isNotEmpty(this.getColumns());
    }

}
