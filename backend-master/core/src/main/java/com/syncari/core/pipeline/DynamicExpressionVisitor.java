package com.syncari.core.pipeline;

import com.syncari.core.pipeline.expression.*;

/**
 * This a a generic expresson visitor. The only implenentation is DynamicDispatchVisitor,
 * that uses reflection tom implement an extensible visitor pattern, without having to add new visit(..)
 * method signatures to ExpressionVisitor interface and verify all implementations are correct.
 * We lose some static type checking due to this, but makes it easier to add custom and contextual expression evaluators
 * without having to pollute the ExpressionVisitor. The DynamicDispatchVisitor can work with any sub-interface of ExpressionVisitor,
 * that may have special, contextual visit(..) methods. See usages of SelectWinnerExpressionVisitor, for an example
 * on how this is used in RecordMergeService
 */
public interface DynamicExpressionVisitor {
    void visit(Expression exp);
}
