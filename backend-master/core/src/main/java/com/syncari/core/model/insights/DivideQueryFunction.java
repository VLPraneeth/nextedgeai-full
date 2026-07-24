package com.syncari.core.model.insights;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

@Slf4j
public class DivideQueryFunction extends DecimalNaryQueryFunction {


    public DivideQueryFunction() {
        super(AggFunctions.DIVIDE);
    }

    @Override
    protected String toExpression(List<String> params) {
        return StringUtils.join(params, "/");
    }
}
