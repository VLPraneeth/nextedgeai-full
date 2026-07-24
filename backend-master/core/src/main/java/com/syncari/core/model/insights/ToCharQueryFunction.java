package com.syncari.core.model.insights;

import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
public class ToCharQueryFunction extends UnaryQueryFunction {

    private AggFunctions function = getQFunction();
    String Function_Name = "TO_CHAR";

    private String toCharField = "Month";

    public AggFunctions getQFunction(){
        return AggFunctions.TO_CHAR;
    }

    @Override
    public AggFunctions getQueryFunction() {
        return function;
    }

    @Override
    public boolean validate() {
        return ((super.validate()) && (null != toCharField));
    }

    @Override
    public String getEscapedName(String escapeChar, Map<String, String> entityIdAliasMap) {
        return super.getEscapedName(escapeChar,entityIdAliasMap);
    }

    @Override
    public String buildExpression(String escapeChar, Map<String, String> entityIdAliasMap) {
        return StringUtils.isNotEmpty(this.getAlias()) ?  Function_Name + "("+  this.getEscapedName(escapeChar,entityIdAliasMap) + "," + "\'"+ this.getToCharField() + "\'" +")" + " " + "\"" +  this.getAlias() + "\"" :
                Function_Name + "("+  this.getEscapedName(escapeChar,entityIdAliasMap) + "," + "\'"+ this.getToCharField() + "\'" +")";
    }

    public QueryFunction makeCopy(){
        QueryFunction func = new ToCharQueryFunction().setAlias(alias).setDataType(dataType);
        if (CollectionUtils.isNotEmpty(this.getColumns())){
            List<QField> cols = new ArrayList<>();
            this.getColumns().forEach(col -> {
                cols.add(col.makeCopy());
            });
            func.setColumns(cols);
        }
        return func;
    }
}
