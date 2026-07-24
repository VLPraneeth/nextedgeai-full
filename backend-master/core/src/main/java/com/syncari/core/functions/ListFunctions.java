package com.syncari.core.functions;

import com.syncari.core.datatype.Datatype;
import com.syncari.core.datatype.DatatypeFactory;
import com.syncari.core.datatype.ObjectType;
import com.syncari.core.model.FunctionCall;
import com.syncari.core.model.FunctionResult;
import com.syncari.core.model.MappingNode;
import com.syncari.core.pipeline.FilterFailedResult;
import com.syncari.core.pipeline.FindInListCriteriaVisitor;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.pipeline.expression.PredicateParser;
import com.syncari.core.token.TokenHelper;
import com.syncari.utils.Pair;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;


@Component
@Slf4j
public class ListFunctions extends FunctionsBase {
    @Autowired
    TokenHelper tokenHelper;

    @Autowired
    TempVariableProcessor tempVariableProcessor;

    @Function
    public Object firstOnEntity(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        final String newValue = "newValue";
        String value = functionCall.getConfig(newValue).toString();
        Object input = StringUtils.isBlank(value) ? null : tokenHelper.resolveTokens((Map<String, Object>) context, value).y;
        if (input == null) {
            return inputs;
        }
        context.recordNodeInputs(newValue, input);
        Object firstValue = getFirstValue(input);
        context.put("previousValue", firstValue);
        context.put("Value From " + context.getCurrentNode().getName(), firstValue);
        return firstParam(inputs, context);
    }

    @Function
    public Object findInList(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        Object inputList = functionCall.getConfig().get("input");
        boolean useInputConfig = inputList != null && !StringUtils.isBlank(inputList.toString());
        context.recordNodeInputs("input", List.of());
        if (!useInputConfig && CollectionUtils.isEmpty(inputs) || inputs.get(0) == null || !List.class.isAssignableFrom(inputs.get(0).getClass())) {
            return List.of();
        }
        Map<String, Object> predicates = (Map<String, Object>) functionCall.getConfig().get("predicate");
        if (MapUtils.isEmpty(predicates)) return inputs.get(0);
        Expression expression = new PredicateParser(StringUtils.EMPTY).fromMap(predicates);

        Map<String, Object> localContext = new HashMap<>(context);
        int index = 0;
        int count = 0;
        List<Object> litValues = useInputConfig ? (List<Object>) tokenHelper.resolveTokensObject(context, inputList.toString()) : (List<Object>) inputs.get(0);
        context.recordNodeInputs("input", litValues);
        List<Object> output = new ArrayList<>();
        for (Object input : litValues) {
            localContext.put("current_value", input);
            localContext.put("current_index", index);
            FindInListCriteriaVisitor visitor = new FindInListCriteriaVisitor(localContext, tokenHelper);
            expression.accept(visitor);
            Object value = visitor.getValue();
            if (value != null && ((boolean) value) == true) {
                count++;
                output.add(input);
            }
            index++;
        }
        context.put("RecordCount From "+context.getCurrentNode().getName(), count);
        return output;
    }

    @Function
    public Object getListItem(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        if (CollectionUtils.isEmpty(inputs) || inputs.get(0) == null || !List.class.isAssignableFrom(inputs.get(0).getClass())) return null;
        if(functionCall.getConfig().containsKey("position")){
            List<Object> litValues = (List<Object>) inputs.get(0);
            String pos = functionCall.getConfig().getOrDefault("position",0).toString();
            String positionValue = tokenHelper.resolveTokens(context, pos);
            int position = 0;
            try {
                position = Integer.parseInt(positionValue);
            } catch (Exception e) {
                log.debug("Invalid position value {}", position);
                return null;
            }
            context.recordNodeInputs("position", position);
            if(litValues.size() <= position) return null;
            return litValues.get(position);
        }
        return null;
    }

    @Function
    public Object first(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        Object param = firstParam(inputs, context);
        return getFirstValue(param);
    }

    private Object getFirstValue(Object param) {
        if (param==null) return param;
        if(List.class.isAssignableFrom(param.getClass())){
            List<Object> params = List.class.cast(param);
            return params.isEmpty()?null:params.get(0);
        }
        return param;
    }

    @Function
    public String join(List<Object> inputs, FunctionCall functionCall, GraphContext graphContext) {
        String delimiter = getConfigOrDefault("delimiter", functionCall, "", graphContext);
        List<Pair<FunctionResult, MappingNode>> inputResults =
                graphContext.getCurrentInputs();
        List<String> results = inputResults.stream().filter(i -> !FilterFailedResult.isFailedFilter(i.x.getResult()) && i.x.getResult() != null)
                .map(i -> join(delimiter, i.x.typedValue())).collect(Collectors.toList());
        return join(delimiter,results);
    }
    
    @Function
    public List<Object> sort(List<Object> inputs, FunctionCall functionCall, GraphContext graphContext) {
        if(inputs == null || inputs.isEmpty() || !(inputs.get(0) instanceof List)) return new ArrayList<>();
        List val = (List) inputs.get(0);
        graphContext.recordNodeInputs("input", val);
        return (List<Object>) val.stream().sorted().collect(Collectors.toList());
    }

    public String join(String delimiter, Object param) {
        if (param == null) return null;
        if (List.class.isAssignableFrom(param.getClass())) {
            List<Object> params = List.class.cast(param);
            final List<Object> nonNullParams = params.stream().filter(a->a!=null && !FilterFailedResult.isFailedFilter(a)).collect(Collectors.toList());
            return nonNullParams.isEmpty() ? null : String.join(delimiter,nonNullParams.stream().
                    filter(a->!StringUtils.isBlank(a.toString())).map(a->a.toString()).collect(Collectors.toList()));
        }
        return param.toString();
    }

    @Function
    public Object last(List<Object> inputs, FunctionCall functionCall, GraphContext graphContext) {
        Object param = firstParam(inputs, graphContext);
        if (param == null) return param;
        if (List.class.isAssignableFrom(param.getClass())) {
            List<Object> params = List.class.cast(param);
            return params.isEmpty() ? null : params.get(params.size() - 1);
        }
        return param;
    }

    public Object firstParam(List<Object> inputs, GraphContext graphContext) {
        final Object o = ((inputs == null) || inputs.isEmpty()) ? null : inputs.get(0);
        graphContext.recordNodeInputs("input", o);
        return o;
    }

    @Function
    public Object reverse(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        Object param = firstParam(inputs, context);
        if (param == null)
            return param;
        if (List.class.isAssignableFrom(param.getClass())) {
            List newList = new ArrayList<>(((List) param));
            Collections.reverse(newList);
            return newList;
        }
        return inputs;
    }

    private <T> Object doListOp(List<Object> inputs, FunctionCall functionCall, GraphContext context, TriConsumer<List, Optional<Integer>, Object> listOp) {

        final Object datatypeString = getConfigOrDefault(ListMutateFunctions.DATA_TYPE, functionCall, "string", context);
        Datatype datatype = DatatypeFactory.getDatatype(datatypeString.toString());

        String valueToAdd = functionCall.getConfig().getOrDefault(ListMutateFunctions.VALUE, "").toString();
        Object typedValue = null;
        var resolvedValue = tokenHelper.resolveTokens((Map<String, Object>) context, (String) valueToAdd);

        if (TokenHelper.hasOneTokenOnly((String) valueToAdd) && resolvedValue.y != null
                && List.class.isAssignableFrom(resolvedValue.y.getClass())) {
            typedValue = List.class.cast(resolvedValue.y).stream().map(v -> datatype.convert(v))
                    .collect(Collectors.toList());
        } else if (datatype.getName().equals(ObjectType.VALUE.getName())) {
            typedValue = datatype.convert(resolvedValue.y);
        } else {
            typedValue = datatype.convert(resolvedValue.x);
        }
        context.recordNodeInputs(ListMutateFunctions.VALUE, typedValue);


        Optional<Integer> indexOpt = Optional.ofNullable(functionCall.getConfig().get(ListMutateFunctions.LIST_INDEX))
                .map(is -> is.toString().trim()).filter(is -> StringUtils.isNumeric(is)).map(is -> Integer.parseInt(is));


        context.recordNodeInputs(ListMutateFunctions.LIST_INDEX, indexOpt.orElse(null));
        var inputOpt = Optional.ofNullable(functionCall.getConfig(ListMutateFunctions.INPUT_LIST));

        Optional<String> tempVariable = inputOpt.flatMap(listToken -> tokenHelper.extractTempVariableName(listToken.toString()));

        var input = inputOpt.map(listToken -> tokenHelper.resolveTokensObject(context, listToken.toString()))
                .filter(resolved -> List.class.isAssignableFrom(resolved.getClass()))
                .map(resolved -> {
                    return List.class.cast(resolved).stream()
                            .map(v -> datatype.convert(v))
                            .collect(Collectors.toList());
                }).orElse(firstParam(inputs, context));

        if (input == null) {
        	if (typedValue != null && List.class.isAssignableFrom(typedValue.getClass())) {
                context.recordNodeInputs(ListMutateFunctions.INPUT_LIST, typedValue);
        		return typedValue;
        	} else {
                final List<Object> listValue = List.of(typedValue);
                context.recordNodeInputs(ListMutateFunctions.INPUT_LIST, listValue);
                return listValue;
            }
        }

        if (List.class.isAssignableFrom(input.getClass())) {
            List newList = new ArrayList<>(((List) input));
            listOp.apply(newList, indexOpt, typedValue);
            if (tempVariable.isPresent() && !StringUtils.isBlank(tempVariable.get())) {
                tempVariableProcessor.setTempVariable(tempVariable.get(), newList, context);
            }
            context.recordNodeInputs(ListMutateFunctions.INPUT_LIST, newList);
            return newList;
        }
        context.recordNodeInputs(ListMutateFunctions.INPUT_LIST, inputs);
        return inputs;

    }

    @Function
    public Object addToList(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
        TriConsumer<List, Optional<Integer>, Object> addList = ( List newList, Optional<Integer> indexOpt, Object typedValue) -> {
            if (!newList.contains(typedValue)) {
                indexOpt.ifPresentOrElse(
                        i -> {
                            if (i > -1 && i <= newList.size()) {
                            	if(typedValue != null && List.class.isAssignableFrom(typedValue.getClass())) {
                            		newList.addAll(i, (List) typedValue);
                            	} else {
                            		newList.add(i, typedValue);
                            	}
                            } else {
                                log.error("AddToList: Invalid index {}, size of list {}", i, newList.size());
                            }
                        },
                        () -> {
                        	if(typedValue != null && List.class.isAssignableFrom(typedValue.getClass())) {
                        		newList.addAll((List) typedValue);
                        	} else {
                        		newList.add(typedValue);
                        	}
                        }
                );
            }
        };
        return doListOp(inputs, functionCall, context, addList);
    }

    @Function
    public Object removeFromList(List<Object> inputs, FunctionCall functionCall, GraphContext context) {

        TriConsumer<List, Optional<Integer>, Object> removeFromList = ( List newList, Optional<Integer> indexOpt, Object typedValue) -> {
            indexOpt.ifPresentOrElse(
                    i -> {
                        if (i > -1 && i < newList.size()) {
                            newList.remove(i.intValue());
                        } else {
                            log.error("RemoveFromList: Invalid index {}, size of list {}", i, newList.size());
                        }
                    },
                    () -> newList.remove(typedValue)
            );
        };
        return doListOp(inputs, functionCall, context, removeFromList);
    }

    @Function
    public Object removeDuplicates(List<Object> inputs, FunctionCall functionCall, GraphContext context) {

        var inputOpt = Optional.ofNullable(functionCall.getConfig(ListMutateFunctions.INPUT_LIST));

        var input = inputOpt.map(listToken -> tokenHelper.resolveTokensObject(context, listToken.toString()))
                .filter(resolved -> List.class.isAssignableFrom(resolved.getClass())).orElse(firstParam(inputs, context));

        if (input != null) {
            context.recordNodeInputs(ListMutateFunctions.INPUT_LIST, input);
            if (List.class.isAssignableFrom(input.getClass())) {
                List newList = new ArrayList<>(((List) input));
                Set<Object> set = new LinkedHashSet<>(newList);
                return set.stream().collect(Collectors.toList());
            }
        }
        return inputs;
    }

    @FunctionalInterface
    interface TriConsumer<One, Two, Three> {
        public void apply(One one, Two two, Three three);
    }

}
