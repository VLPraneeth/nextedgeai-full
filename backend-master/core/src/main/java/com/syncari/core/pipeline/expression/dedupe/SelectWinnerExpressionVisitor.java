package com.syncari.core.pipeline.expression.dedupe;

import com.syncari.core.pipeline.ExpressionVisitor;

public interface SelectWinnerExpressionVisitor extends ExpressionVisitor {

    void visit(MostCompleteRecordExpression expression);

}
