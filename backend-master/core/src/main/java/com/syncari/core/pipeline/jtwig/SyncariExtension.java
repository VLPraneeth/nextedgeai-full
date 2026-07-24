package com.syncari.core.pipeline.jtwig;

import com.syncari.core.Features;
import com.syncari.core.functions.*;
import com.syncari.core.model.FunctionCall;
import com.syncari.core.pipeline.FilterFailedResult;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.pipeline.TerminateExecutionPathException;
import com.syncari.core.pipeline.jtwig.functions.*;
import com.syncari.core.service.FeatureService;
import lombok.extern.slf4j.Slf4j;
import org.jtwig.environment.EnvironmentConfigurationBuilder;
import org.jtwig.extension.Extension;
import org.jtwig.functions.FunctionRequest;
import org.jtwig.functions.JtwigFunction;
import org.jtwig.value.Undefined;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class SyncariExtension implements Extension {
    @Autowired
    private TextFunctions textFunctions;
    @Autowired
    private MathFunctions mathFunctions;
    @Autowired
    private DateFunctions dateFunctions;
    @Autowired
    private LookUpFunctions lookUpFunctions;
    @Autowired
    private MiscFunctions miscFunctions;
    @Autowired
    private FilterFunctions filterFunctions;
    @Autowired
    private ListFunctions listFunctions;
    @Autowired
    private SimilarWebFunctions similarWebFunctions;
    @Autowired
    private SalesIntelFunctions salesIntelFunctions;
    @Autowired
    private AggregateFunctions aggregateFunctions;
    @Autowired
    private ApexAnalytixFunctions apexAnalytixFunctions;
    @Autowired
    private AidentifiedFunctions aidentifiedFunctions;
    @Autowired
    private JsonFunctions jsonFunctions;
    @Autowired
    private LoopFunctions loopFunctions;


    @Override
    public void configure(EnvironmentConfigurationBuilder environmentConfigurationBuilder) {
        environmentConfigurationBuilder.functions()
                .add(new Sum())
                .add(new HasConflicts())
                .add(new NonEmpty())
                .add(new FirstOf())
                .add(new Latest())
                .add(new Value())
                .add(new Null())
                .add(new FilterFailed())
                //TO Add more, inject the class that has methods annotated with 'Function' ,
                // and call the add method as below
                //.add(customFunctions(mathFunctions))
                .add(customFunctions(textFunctions))
                .add(customFunctions(jsonFunctions))
                .add(customFunctions(listFunctions))
                .add(customFunctions(mathFunctions))
                .add(customFunctionsLegacy(dateFunctions))
                .add(customFunctionsLegacy(lookUpFunctions))
                .add(customFunctionsLegacy(miscFunctions))
                .add(customFunctionsLegacy(filterFunctions))
                .add(customFunctionsLegacy(similarWebFunctions))
                .add(customFunctionsLegacy(salesIntelFunctions))
                .add(customFunctionsLegacy(apexAnalytixFunctions))
                .add(customFunctionsLegacy(aggregateFunctions))
                .add(customFunctionsLegacy(aidentifiedFunctions))
                .add(customFunctionsLegacy(loopFunctions))
                .and().value().withValueComparator(new SyncariValueComparator())
        ;
    }

    private List<? extends JtwigFunction> customFunctionsLegacy(Object functionDefinitions) {
        Method[] declaredMethods = functionDefinitions.getClass().getDeclaredMethods();
        List<Method> methods = Arrays.asList(declaredMethods);
        return methods.stream().filter(method ->
                method.isAnnotationPresent(Function.class)
        ).map(method ->
                new NamedSideChannelFunction(method.getName(), req -> executeLegacy(req, method, functionDefinitions))
        ).collect(Collectors.toList());
    }

    private List<? extends JtwigFunction> customFunctions(Object functionDefinitions) {
        Method[] declaredMethods = functionDefinitions.getClass().getDeclaredMethods();
        List<Method> methods = Arrays.asList(declaredMethods);
        return methods.stream().filter(method ->
                method.isAnnotationPresent(Function.class)
        ).map(method ->
                new NamedSideChannelFunction(method.getName(), req -> execute(req, method, functionDefinitions))
        ).collect(Collectors.toList());
    }

    private Object execute(FunctionRequest request, Method method, Object target) {
        List<Object> actualArgs = request.getArguments();
        var inputs = actualArgs.stream().filter(arg ->
                arg == null ||  (!FunctionCall.class.isAssignableFrom(arg.getClass()) && !GraphContext.class.isAssignableFrom(arg.getClass()))
        ).map(arg -> arg == null || Undefined.UNDEFINED == arg ? null : arg).collect(Collectors.toList());
        boolean hasFailedFilter = hasFailedFilter(inputs);
        if(hasFailedFilter && method.getAnnotation(AcceptsFilterValue.class)==null){
            log.debug("Found a failed filter in inputs for {}. Skipping evaluation and propagating up",method.getName());

            //We return a static filter failed result. This prevents a downstream isFalse from flipping the branch.
            //eg: Filter1->  Filter2 -> isFalse -> end
            //If Filter1 fails and returns a filterfailed, filter2 simply propagates it, and isFalse will return a result by flipping Filterfailed
            //but with FilterFailedResult.VALUE, its an indication to isFalse that its a propagated failure with no real values and
            // isfalse will also fail. See MiscFunctions#isFalse
            return FilterFailedResult.VALUE;
        }
        var callAndContext = actualArgs.stream().filter(arg ->
                arg != null &&  (FunctionCall.class.isAssignableFrom(arg.getClass()) || GraphContext.class.isAssignableFrom(arg.getClass()))
        ).collect(Collectors.toList());
        Object result = null;
        List<Object> arguments = new ArrayList<>();
        arguments.add(inputs);
        arguments.addAll(callAndContext);
        try {
            //TODO: More stuff in FunctionRequest
            //FunctionSideChannel.captureStatistic(method.getName(),);
            result = method.invoke(target, arguments.toArray());
            return result;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }finally{
            Object graphContextObject = actualArgs.get(actualArgs.size() - 1);
            if(graphContextObject!=null && graphContextObject.getClass().isAssignableFrom(GraphContext.class)){
                var graphContext =(GraphContext)graphContextObject;
                if(graphContext.getGraph()!=null) {
                    graphContext.getStat().getRecordsProcessed().inc();
                    if (hasChanges(inputs, result)) {
                        graphContext.getStat().getChangeCount().inc();
                    }
                    if (inputs.stream().allMatch(i -> i == null)) {
                        graphContext.getStat().getEmptyInputCount().inc();
                    }
                    if (result == null) {
                        graphContext.getStat().getEmptyOutputCount().inc();
                    }
                }
            }
        }
    }
    private Object executeLegacy(FunctionRequest request, Method method, Object target) {
        var expectedArgs = Arrays.asList(method.getParameters());
        List<Object> actualArgs = request.getArguments();
        assert actualArgs.size() == expectedArgs.size() : String.format("Missing Parameters for function %s. Expected %s, found %s",method.getName(),expectedArgs.size(),actualArgs.size() );
        var inputs = actualArgs.stream().filter(arg ->
            arg == null ||  (!FunctionCall.class.isAssignableFrom(arg.getClass()) && !GraphContext.class.isAssignableFrom(arg.getClass()))
        ).map(arg -> arg== null||Undefined.UNDEFINED==arg? null: arg).collect(Collectors.toList());
        Object result = null;
        boolean hasFailedFilter = hasFailedFilter(inputs);
        //Don't call function if failedFiter is present, and the function doesn't accept filterValues
        if(hasFailedFilter && method.getAnnotation(AcceptsFilterValue.class)==null){
            log.debug("Found a failed filter in inputs for {}. Skipping evaluation and propagating up",method.getName());
            throw new TerminateExecutionPathException();
            //return failedFilter.get();
        }
        try {
            //TODO: More stuff in FunctionRequest
            //FunctionSideChannel.captureStatistic(method.getName(),);

            result = method.invoke(target, actualArgs.toArray());
            return result;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }finally{
            Object graphContextObject = actualArgs.get(actualArgs.size() - 1);
            if(graphContextObject!=null && graphContextObject.getClass().isAssignableFrom(GraphContext.class)){
                var graphContext =(GraphContext)graphContextObject;
                graphContext.getStat().getRecordsProcessed().inc();
                if(hasChanges(inputs, result)){
                    graphContext.getStat().getChangeCount().inc();
                }
                if(inputs.stream().allMatch(i->i==null)) {
                    graphContext.getStat().getEmptyInputCount().inc();
                }
                if(result==null){
                    graphContext.getStat().getEmptyOutputCount().inc();
                }
            }
            }
    }

    private boolean hasFailedFilter(List<Object> inputs) {
        return !inputs.stream().filter(i -> FilterFailedResult.isFailedFilter(i)).collect(Collectors.toList()).isEmpty();
    }

    private boolean hasChanges(List<Object> inputs, Object result){
        //no inputs and non empty results
        if(inputs.isEmpty() && result!=null) return  true;
        //if any input is not the same as result, it means function had an effect
        //if there were no inputs and result was null, its considered a no-op
        return inputs.stream().anyMatch(input -> !Objects.equals(input, result));
    }

}