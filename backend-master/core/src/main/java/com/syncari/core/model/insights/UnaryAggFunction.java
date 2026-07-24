package com.syncari.core.model.insights;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class UnaryAggFunction extends UnaryQueryFunction {

    protected AggFunctions function;

    //for persistence!
    private UnaryAggFunction() {

    }

    public UnaryAggFunction(AggFunctions function, String dataType) {
        this.function = function;
        this.dataType = dataType;
    }

    @Override
    public AggFunctions getQueryFunction() {
        return function;
    }

    protected String toExpression(String columnExp) {
        return String.format("%s(%s)", function.name(), columnExp);
    }

    @Override
    public String buildExpression(String escapeChar, Map<String, String> entityIdAliasMap) {
        String columnExp = this.getEscapedName(escapeChar, entityIdAliasMap);
        final String exp = toExpression(columnExp);
        return null == this.getAlias() ? exp :
                exp + " as " + "\"" + this.getAlias() + "\"";
    }

    @Override
    public QueryFunction makeCopy() {
        final List<QField> columnCopy = this.getColumns()
                .stream()
                .map(c -> c.makeCopy())
                .collect(Collectors.toList());
        return new UnaryAggFunction(getQueryFunction(), getDataType())
                .setAlias(getAlias())
                .setColumns(columnCopy);
    }
}
