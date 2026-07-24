package com.syncari.core.model.insights.dataset;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class Variable {

    private String apiName;
    private String displayName;
    private String datatype;
    private String helpText;
    // all variables are required unless specified otherwise
    private boolean isRequired = true;
    private boolean isMultiValueField;
    private boolean updatable;
    private VariableValue variableValue;

    public Variable makeCopy(){
        return new Variable().setApiName(this.getApiName()).setDisplayName(this.getDisplayName()).setDatatype(this.getDatatype())
                .setHelpText(this.getHelpText()).setVariableValue(null != this.getVariableValue() ?this.getVariableValue().makeCopy() : null)
                .setRequired(this.isRequired).setUpdatable(this.updatable).setMultiValueField(this.isMultiValueField);
    }
}
