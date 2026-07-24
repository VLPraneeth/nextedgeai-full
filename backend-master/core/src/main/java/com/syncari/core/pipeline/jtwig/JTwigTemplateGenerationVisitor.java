package com.syncari.core.pipeline.jtwig;

import com.syncari.core.pipeline.SimpleExpressionVisitor;
import com.syncari.core.pipeline.expression.*;
import com.syncari.core.token.TokenHelper;

import java.util.*;
import java.util.stream.Collectors;

public class JTwigTemplateGenerationVisitor extends SimpleExpressionVisitor {
    protected Stack<String> rendered = new Stack<>();
    protected String generated = null;
    protected TokenHelper tokenHelper;

    public JTwigTemplateGenerationVisitor(TokenHelper tokenHelper) {
        this.tokenHelper = tokenHelper;
    }

    public void visit(If exp) {
        var falseExp = rendered.pop();
        var trueExp = rendered.pop();
        var cond = rendered.pop();
        rendered.push("{% if (" + cond + ") %}" + trueExp + "{% else %}" + falseExp + "{% endif %}");
    }

    public void visit(Equal exp) {
        var right = rendered.pop();
        var left = rendered.pop();
        rendered.push(String.format("(%s == %s)",left,right));
    }
    
    public void visit(EqualIgnoreCase exp) {
        var right = rendered.pop();
        var left = rendered.pop();
        rendered.push(String.format("%s.equalsIgnoreCase(%s)",left,right));
    }

    public void visit(And exp) {
        var right = rendered.pop();
        var left = rendered.pop();
        rendered.push(String.format("(%s and %s)",left,right));
    }
    public void visit(Or exp) {
        var right = rendered.pop();
        var left = rendered.pop();
        rendered.push(String.format("(%s or %s)",left,right));
    }

    public void visit(GreaterThan exp) {
        var right = rendered.pop();
        var left = rendered.pop();
        rendered.push(String.format("(%s > %s)",left,right));
    }

    public void visit(LessThan exp) {
        var right = rendered.pop();
        var left = rendered.pop();
        rendered.push(String.format("(%s < %s)",left,right));
    }

    public void visit(NotEqual exp) {
        var right = rendered.pop();
        var left = rendered.pop();
        rendered.push(String.format("(%s != %s)",left,right));
    }

    public void visit(Not exp) {
        var arg = rendered.pop();
        rendered.push(String.format("(not (%s))",arg));
    }

    public void visit(LiteralExpression exp) {
        if (exp.isRendered()) {
            rendered.push(exp.getValue() == null? null: exp.getValue().toString());
        } else {
            if(exp.getValue()!=null && List.class.isAssignableFrom(exp.getValue().getClass())){
                rendered.push(List.class.cast(exp.getValue()).stream().map(v->getLiteralValue(v)).collect(Collectors.toList()).toString());
            }else if(exp.getValue()!=null && Map.class.isAssignableFrom(exp.getValue().getClass())){
                Map<String, Object> resultMap = new HashMap<>();
                Map.class.cast(exp.getValue()).entrySet().forEach(x -> {
                            Map.Entry entry = ((Map.Entry)x);
                            if (entry.getValue()!=null && List.class.isAssignableFrom(entry.getValue().getClass())){
                                resultMap.put((String)entry.getKey(),List.class.cast(entry.getValue()).stream().map(v->getLiteralValue(v)).collect(Collectors.toList()).toString());
                            }else{
                                resultMap.put((String)entry.getKey(),getLiteralValue(entry.getValue()));
                            }
                        });
                rendered.push(mapToJtwigParsableString(resultMap));
            } else if (exp.getValue() == null) {
                rendered.push(null);
            }else{
                rendered.push(getLiteralValue(exp.getValue().toString()));
            }
        }
    }

    private String mapToJtwigParsableString(Map<String, Object> mapToConvert){
        return mapToConvert.entrySet().stream().map(e -> e.getKey() + ":" + e.getValue())
                .collect(Collectors.joining(", ", "{", "}"));

    }

    protected String getLiteralValue(Object value){
        if(tokenHelper.hasTokens(Objects.toString(value,null))){
            return tokenHelper.sanitizeAndStripTokenDelimiters(Objects.toString(value,null));
        }else {
            return getStringValue(value);
        }

    }

    protected String getStringValue(Object value) {
        if(String.class.isAssignableFrom(value.getClass())) {
            return ("\"" + value + "\"");
        }else{
            return value.toString();
        }
    }

    public void visit(VariableExpression exp) {
        if (exp.isRendered()) {
            rendered.push(String.format("{{value(%s)}}",exp.getVariableName()));
        } else {
            rendered.push(exp.getVariableName());
        }
    }

   
    public void visit(Empty exp) {
        var e = rendered.pop();
        rendered.push(String.format("(((%s) is empty) or ((%s) == empty))",e,e));
    }

    public void visit(NotEmpty exp) {
        var e = rendered.pop();
        rendered.push(String.format("(not (((%s) is empty) or ((%s) == empty)))",e,e));
    }

    @Override
    public void visit(BetweenExpression betweenExpression) {
        Expression gte = Expression.gte(betweenExpression.getExpression(),betweenExpression.getLower());
        Expression lt = Expression.lt(betweenExpression.getExpression(),betweenExpression.getUpper());
        var generator = new JTwigTemplateGenerationVisitor(tokenHelper);
        Expression.and(gte,lt).accept(generator);
        rendered.push(generator.getGeneratedBody());
    }

    @Override
    public void visit(GreaterThanEqual gteExpression) {
        var right = rendered.pop();
        var left = rendered.pop();
        rendered.push(String.format("(%s >= %s)",left,right));

    }

    @Override
    public void visit(LessThanEqual lteExpression) {
        var right = rendered.pop();
        var left = rendered.pop();
        rendered.push(String.format("(%s <= %s)",left,right));

    }
    @Override
    public void visit(Contains expression) {
        var right = rendered.pop();
        var left = rendered.pop();
        rendered.push(String.format("%s.contains(%s)",left,right));

    }

    @Override
    public void visit(StartsWith startsWithExpression) {
        var right = rendered.pop();
        var left = rendered.pop();
        rendered.push(String.format("%s.startsWith(%s)", left, right));
    }

    public void visit(NotStartsWith startsWithExpression) {
        var right = rendered.pop();
        var left = rendered.pop();
        rendered.push(String.format("(not (%s.startsWith(%s))", left, right));
    }

    public void visit(FunctionExpression exp) {
        rendered.push(exp.getFunctionCall().compile());
    }

    @Override
    public void visit(NotIn expression) {
        var right = rendered.pop();
        var left = rendered.pop();
        rendered.push(String.format("(not (%s.contains(%s)))", right,left));
    }

    @Override
    public void visit(In expression) {
        var right = rendered.pop();
        var left = rendered.pop();
        rendered.push(String.format("(%s.contains(%s))", right,left));
    }

    public void clear() {
        generated = null;
        rendered.clear();
    }

    public String getGeneratedBody() {
        if (generated == null) generated = rendered.pop();
        return generated;
    }
}
