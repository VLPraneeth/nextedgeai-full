package com.syncari.core.model.insights;

import com.syncari.core.exceptions.SyncariValidationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class FormulaQueryFunction extends NaryQueryFunction {

    private AggFunctions function = AggFunctions.FORMULA;

    @Override
    public AggFunctions getQueryFunction() {
        return function;
    }

    @Override
    public String getEscapedName(String escapeChar, Map<String, String> entityIdAliasMap) {
        if (CollectionUtils.isEmpty(this.getColumns())) {
            throw new SyncariValidationException("Not valid inputs for function '" + getQueryFunction().name() + "'");
        }
        final String formula = this.getColumns().stream().map(c -> {
            if (c.isLiteral()) {
                return c.getName();
            }
            String entityAlias = entityIdAliasMap.get(c.getDatasetId());
            if (entityAlias != null) {
                String nameTouse = c.getType() != QField.Type.DATASET ? c.getName().toLowerCase() : c.getName();
                return "\"" + entityAlias + "\"." + escapeChar + nameTouse + escapeChar;
            } else {
                return escapeChar + c.getName() + escapeChar;
            }
        }).collect(Collectors.joining(" "));
        return formula;
    }


    @Override
    public String buildExpression(String escapeChar, Map<String, String> entityIdAliasMap) {
        final String expression = "(" + this.getEscapedName(escapeChar, entityIdAliasMap) + ")";
        return this.getAlias() == null ? expression : expression + " " + "\"" + this.getAlias() + "\"";
    }

    @Override
    public String getDataType() {
        return function.getDataType();
    }

    public QueryFunction makeCopy() {
        QueryFunction func = new FormulaQueryFunction().setAlias(getAlias()).setDataType(getDataType());
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
            ((FormulaQueryFunction) func).setInnerQueryFields(cols);
        }
        return func;
    }

    @Override
    public boolean validate() {
        final boolean notEmpty = CollectionUtils.isNotEmpty(this.getColumns());
        log.info("Validate called, it will return result {}", notEmpty);
        return notEmpty;
    }
}
