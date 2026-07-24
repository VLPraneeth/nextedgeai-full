package com.syncari.core.pipeline.expression.dedupe;

import com.syncari.core.model.FunctionCall;
import com.syncari.core.pipeline.ExpressionVisitor;
import com.syncari.core.pipeline.Max;
import com.syncari.core.pipeline.Min;
import com.syncari.core.pipeline.MostComplete;
import com.syncari.core.pipeline.expression.Expression;

//Expression is needed to render UI, among other things
public interface SelectWinnerExpression extends Expression {

    void accept(SelectWinnerExpressionVisitor visitor);

}
