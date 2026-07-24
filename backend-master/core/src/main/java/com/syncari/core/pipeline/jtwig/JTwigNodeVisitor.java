package com.syncari.core.pipeline.jtwig;

import com.syncari.connector.EntityData;
import com.syncari.core.actions.Actions;
import com.syncari.core.datatype.Datatype;
import com.syncari.core.datatype.ListType;
import com.syncari.core.datatype.ObjectType;
import com.syncari.core.event.store.model.NodeAudit;
import com.syncari.core.exceptions.PipelineException;
import com.syncari.core.functions.FunctionConstants;
import com.syncari.core.model.*;
import com.syncari.core.pipeline.*;
import com.syncari.core.service.FeatureService;
import com.syncari.core.service.PipelineNodeAuditService;
import com.syncari.utils.Pair;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

import static com.syncari.utils.I18n.i18n;

@Slf4j
public class JTwigNodeVisitor extends AbstractNodeConfigurationVisitor {

    // API name of functions to mock for simulation to avoid any side effects
    private static final List<String> MOCK_FUNCTIONS_FOR_SIMULATION = List.of(
            "advancedAttachRecord",
            "attachRecord",
            "updateSyncariRecords",
            "updateSyncariRecordsOnField"

    );

    // Temp workaround for functions that can be configured on list input but we cannot change the seed data.
    public static final Set<String> LIST_INPUT_FUNCTIONS = Set.of(
            FunctionConstants.SET_VALUE,
            FunctionConstants.FIND_VALUE,
            FunctionConstants.FILTER,
            FunctionConstants.PREDICATE,
            FunctionConstants.SET_FIELD_VALUES,
            FunctionConstants.CASE,
            FunctionConstants.CASE_BRANCH
    );

    public static final List<String> WHITE_LIST_ACTION_FUNCTION_FORCACHING=List.of(
            "advancedAttachRecord",
            "attachRecord",
            "updateSyncariRecords",
            "updateSyncariRecordsOnField",
            "insertRecordOnField",
            "insertRecord",
            "setValueOnEntity",
            "replaceOnEntity",
            "setFields"
    );
    private GraphContext context;
    private PipelineEvaluator evaluator;
    private Actions actions;
    private FeatureService featureService;

    private PipelineNodeAuditService pipelineNodeAuditService;

    public JTwigNodeVisitor(GraphContext context, PipelineEvaluator evaluator, Actions actions, PipelineNodeAuditService pipelineNodeAuditService, FeatureService featureService) {
        this.context = context;
        this.evaluator = evaluator;
        this.actions = actions;
        this.pipelineNodeAuditService = pipelineNodeAuditService;
        this.featureService = featureService;
    }

    @Override
    public void visit(GenericActionConfig actionConfig, MappingNode node) {
        context.startTimer();
        try {
            if (context.getBatchActionContext().shouldRunActions() && !actions.isBatchedAction(node.getApiName())) {
                log.debug("Skipping action {} with because its not batched", node.getName());
            }
            context.setCurrentNode(node);
            Stat stat = context.getStat();
            List<Edge> inboundEdges = context.getGraph().getInboundEdges(node);
            var results = inboundEdges.stream().flatMap(edge -> Optional.ofNullable((Pair<FunctionResult, MappingNode>) context.get("output_" + edge.getSourceStage().getId())).stream())
                    .collect(Collectors.toList());
            Optional<EntityData> record = Optional.ofNullable((EntityData) context.get("record"));
            //Only pass successfulinputs to Action to process.
            List<Pair<FunctionResult, MappingNode>> successfulInputs = results.stream().filter(i -> i != null && !FilterFailedResult.isFailedFilter(i.x.getResult())).collect(Collectors.toList());
            List<Pair<FunctionResult, MappingNode>> failedInputs = results.stream().filter(i -> i != null && FilterFailedResult.isFailedFilter(i.x.getResult())).collect(Collectors.toList());
            if (actions.isValidAction(node.getApiName(), actionConfig)) {
                if (context.getBatchActionContext().shouldRunActions()) {
                    //run the actions regardless of state of inputs - because we are finalizing batched actions here. All inputs have already been processed
                    if (record.isPresent()) {
                        context.cacheable("output_" + node.getId() + record.get().getId(), () -> executeAction(actionConfig, node));
                    } else {
                        log.error("This should not happen, record should always exists");
                        executeAction(actionConfig, node);
                    }
                } else {
                    if (isSimpleLoops(context)) {
                        executeActionV2(actionConfig, node, successfulInputs);
                    } else {
                        executeAction(actionConfig, node, successfulInputs);
                    }
                }
            } else {
                log.warn("Skipping action {} because of either a filter or a previously failed action in the chain", node.getApiName());
            }

            FunctionResult result = !successfulInputs.isEmpty() ? successfulInputs.get(0).x : !failedInputs.isEmpty() ? failedInputs.get(0).x : new FunctionResult(FilterFailedResult.VALUE, ObjectType.VALUE);
            log.debug("Node: {}, Result {}, Action call: {}", node.getName(), result, node.getApiName());
            if (!actions.isBatchedAction(node.getApiName()) || !context.getBatchActionContext().shouldRunActions()) {
                context.put("output_" + node.getId(), Pair.of(result, node));
                context.captureTestOutputForNode(result, node, !successfulInputs.isEmpty() ? successfulInputs.get(0).y : !failedInputs.isEmpty() ? failedInputs.get(0).y : null);
            }
            stat.getRecordsProcessed().inc();
        } finally {
            context.endTimer();
        }
    }

    public void executeActionV2(GenericActionConfig actionConfig, MappingNode node, List<Pair<FunctionResult, MappingNode>> inputs) {

        // TODO: check this
        final Map<String, List<Object>> contextChanges = context.getContextChanges();
        for (int j=0;j< inputs.size();j++) {
            Pair<FunctionResult, MappingNode> input  = inputs.get(j);
            Object r = input.x.getResult();
            if ((!FilterFailedResult.isFailedFilter(r))) {
                Object lookupResult = input.x.getLookupResult();
                context.put("previousLookup", lookupResult);
                context.put("previousLookupCount", input.x.getLookupCount());
                context.put("previous", r);
                Optional<EntityData> record = Optional.ofNullable((EntityData) context.get("record"));
                if (record.isPresent()) {
                    if (context.getCurrentLoopContext() != null) {
                        var indices = context.allIndices().stream().map(loopIndex -> loopIndex.toString()).collect(Collectors.joining(","));
                        context.cacheable("output_" + node.getId() + record.get().getId() + indices, () -> executeAction(actionConfig, node));
                    } else {
                        context.cacheable("output_" + node.getId() + record.get().getId(), () -> executeAction(actionConfig, node));
                    }
                } else {
                    log.error("This should not happen, record should always exists");
                    //start tracking context changes before evaluating function
                    executeAction(actionConfig, node);
                }
            }
        }

    }

    public void executeAction(GenericActionConfig actionConfig, MappingNode node, List<Pair<FunctionResult, MappingNode>> inputs) {

        final Map<String, List<Object>> contextChanges = context.getContextChanges();
        for (int j=0;j< inputs.size();j++) {
            Pair<FunctionResult, MappingNode> input  = inputs.get(j);

            List inputList = toList(input.x.getResult(), List.of(ObjectType.VALUE));
            List inputLookupList = toList(input.x.getLookupResult(), List.of(ObjectType.VALUE));
            List<Long> inputLookupCounts = input.x.getLookupCounts().isEmpty() ? listwithNulls(input.x.getLookupCount()) : input.x.getLookupCounts();
            if (input.x.getResult() instanceof List) {
                log.debug("Executing a loop for graph {} node {} action {}", context.getGraph().getTargetId(), node.getId(), actionConfig.getApiName());
            }

            if( inputList.size() > 1) {
                log.debug("Executing a loop for graph {} node {}", context.getGraph().getTargetId(), node.getId());
            }

            for (int i = 0; i < inputList.size(); i++) {
                Object r = inputList.get(i);
                final int index=i;
                context.addToSyncariNamespace("currentLoopIndex", index);
                contextChanges.forEach((resultKey, values)->{
                    if(index < values.size()){
                        context.put(resultKey,values.get(index));
                    }
                });
                if ((!FilterFailedResult.isFailedFilter(r))) {
                    Object lookupResult = inputLookupList.size() > i ? inputLookupList.get(i) : null;
                    Long lookupCount = inputLookupCounts.size() > i && inputLookupCounts.get(i) != null ? inputLookupCounts.get(i) : 0l;
                    context.put("previousLookup", lookupResult);
                    context.put("previousLookupCount", lookupCount);
                    context.put("previous", r);
                    Optional<EntityData> record = Optional.ofNullable((EntityData) context.get("record"));
                    if (record.isPresent()) {
                        context.cacheable("output_" + node.getId() + record.get().getId() + i, () -> executeAction(actionConfig, node));
                    } else {
                        log.error("This should not happen, record should always exists");
                        //start tracking context changes before evaluating function
                        executeAction(actionConfig, node);
                    }
                }
            }
        }
    }

    protected <T> List<T> listwithNulls(T value) {
        List<T> l = new ArrayList<>();
        l.add(value);
        return l;
    }

    public ActionResult executeAction(GenericActionConfig actionConfig, MappingNode node) {
        context.trackContextChanges();
        var actionResult = actions.dispatch(node.getApiName(), actionConfig, context);
        final Map<String, Object> capturedContext = context.getCapturedContext();
        capturedContext.put(String.format("Action Status for %s", node.getName()), actionResult.isStatus());
        capturedContext.put(String.format("Action Result From %s", node.getName()), actionResult.getResult());
        context.put(String.format("Action Result From %s", node.getName()), actionResult.getResult());
        context.put(String.format("action_output_%s_result", node.getId()), actionResult.getResult());
        context.put(String.format("action_output_%s_status", node.getId()), actionResult.isStatus());
        if (actionResult.getError() != null) {
            String errorMessage = actionResult.getError().getMessage() == null ? "Unknown error" : actionResult.getError().getMessage();
            context.put(String.format("Action Result From %s", node.getName()), Map.of("isSuccess", false, "errors", List.of(errorMessage)));
        }
        context.stopTrackingContextChanges();
        logActionAudit(node, actionResult);
        context.resetCurrentNodeConfig();
        return actionResult;
    }

    private void logActionAudit(MappingNode node, ActionResult actionResult) {
        if (actionResult.isStatus()) {
            logNodeAudit(node);
        } else {
            logNodeError(node, actionResult.getError());
        }
    }

    public Map<String, Object> getContext() {
        return context;
    }

    @Override
    public void visit(SimpleFunctionNodeConfig simpleFunctionNodeConfig, MappingNode node) {
        context.startTimer();
        try {
            FunctionCall functionCall = simpleFunctionNodeConfig.getFunctionCall();
            //make sure at least one arg is present in context, otherwise don't execute function
            //THIS IS IMPORTANT BECAUSE FUNCTIONS MAY INCORRECTLY RETURN EMPTY VALUES OTHERWISE,
            // MESSING WITH CONFLICT RESOLUTION
            //TODO: Consider setting strict evaluation for functions
            if (!isSimpleLoops(context)) {
                // this won't work with loops
                if (context.containsKey("output_" + node.getId())) {
                    log.debug("Already evaluated function {} with results {}", node.getName(), context.get("output_" + node.getId()));
                    return;
                }
            } else {
                // Execute loop Node everytime but the functions inside are cached
                if (isNodeExecuted(node)) {
                    log.debug("Already evaluated function {} with results {}", node.getName(), context.get("output_" + node.getId()));
                    return;
                }
            }

            String suffix = node.getId();
            FunctionResult result = null;
            Pair<FunctionResult, MappingNode> input = getInput(functionCall);
            if (functionCall.getParams().stream().filter(p -> context.containsKey(p.getContextRoot())).findFirst().isPresent()) {
                try {
                    log.debug("Executing function {}({}) with node id {} inside graph {} for record {}",
                            functionCall.getFunctionDefinition().getName(),node.getName(), node.getId(),
                            context.getGraph().getName(), input.x.getResult());

                    functionCall.setCurrentParam(input.y.getId());
                    context.put("functionCall", functionCall);
                    context.put("context", context);
                    if (isSimpleLoops(context)) {
                        result = executeFunctionV2(functionCall, input, node);
                    } else {
                        result = executeFunction(functionCall, input, node);
                    }
                    log.debug("Executed function {}({}) with node id {} inside graph {} with id {} and result {}",
                            functionCall.getFunctionDefinition().getName(),node.getName(), node.getId(),
                            context.getGraph().getName(),context.getGraph().getId(),result);
                    log.debug("Adding function output to node {} result {}", node.getId(), result);

                    addLoopNodeOutput(node, context, Pair.of(result, node));
                    context.put("output_" + node.getId(), Pair.of(result, node));
                } catch(TerminateExecutionPathException ex){
                    log.debug("Evaluation failed for function node {}({}). Terminating path execution", functionCall.getFunctionDefinition().getName(),node.getName());
                    result = new FunctionResult(FilterFailedResult.VALUE,ObjectType.VALUE);
                    addLoopNodeOutput(node, context, Pair.of(result, node));
                    context.put("output_" + node.getId(), Pair.of(result, node));
                    updateContextWithResults(node, result);
                    logNodeError(node, ex);
                    throw ex;
                } catch (Exception e){
                    log.error(String.format("Evaluation failed for function node %s(%s)", functionCall.getFunctionDefinition().getName(), node.getName()), e);
                    logNodeError(node, e);
                    throw e;
                }

            }else{
                result = new FunctionResult(FilterFailedResult.VALUE,ObjectType.VALUE);
                log.debug("No Contextual parameters found while executing function {} in context {}", node, context.entrySet());
                addLoopNodeOutput(node, context, Pair.of(new FunctionResult(FilterFailedResult.VALUE, ObjectType.VALUE), node));
                context.put("output_" + suffix, Pair.of(new FunctionResult(FilterFailedResult.VALUE, ObjectType.VALUE), node));
                updateContextWithResults(node, result);
            }
            if (null != input) {
                context.captureTestOutputForNode(result, node, input.y);
            }
        } catch (Exception e) {
            logNodeError(node, e);
            throw new PipelineException(e).setNodeId(node.getId()).setGraphId(node.getMappingGraphId()).setScope(node.getScope());
        } finally {
            context.endTimer();
        }

    }

    private void logNodeError(MappingNode node, Throwable ex) {
        if (context.isNodeLoggingOn()) {
            pipelineNodeAuditService.queue(new NodeAudit(context.getGraph(), node, context, ex));
        }
    }

    private boolean isNodeExecuted(MappingNode node) {
        return context.isLoopNode() ? context.isLoopCompleted() : context.containsKey("output_" + getCompositeId(node, context));
    }

    private void addLoopNodeOutput(MappingNode node, GraphContext context, Pair<FunctionResult, MappingNode> output) {
        if (isSimpleLoops(context) && context.getCurrentLoopContext() != null) {
            var indices = context.allIndices().stream().map(loopIndex -> loopIndex.toString()).collect(Collectors.joining("_"));
            String compositeId = String.format("%s_%s", node.getId(), indices);
            context.put("output_" + compositeId, output);
        }
    }
    private String getCompositeId(MappingNode node, GraphContext context) {
        if (isSimpleLoops(context) && context.getCurrentLoopContext() != null) {
            var indices = context.allIndices().stream().map(loopIndex -> loopIndex.toString()).collect(Collectors.joining("_"));
            return String.format("%s_%s", node.getId(), indices);
        } else {
            return node.getId();
        }
    }

    protected FunctionResult executeFunction(FunctionCall functionCall, Pair<FunctionResult, MappingNode> input, MappingNode node) {
        //Pair<FunctionResult, MappingNode> input = getInput(functionCall);
        FunctionDefinition functionDefinition = functionCall.getFunctionDefinition();
        Datatype dataType = functionDefinition.getParam().getDatatype();
        Datatype outputType = functionDefinition.getOutputType();
        Datatype inputType = functionDefinition.getInputType();
        List<Datatype> allInputTypes = new ArrayList<>(functionDefinition.getAdditionalInputTypes());
        allInputTypes.add(inputType);
        List<Object> results = new ArrayList<>();
        List<Object> lookupResults = new ArrayList<>();
        List<Long> lookupResultCount = new ArrayList<>();
        List inputList = toList(input.x.getResult(), allInputTypes);
        List inputLookupList = toList(input.x.getLookupResult(), allInputTypes);
        List<Long> inputLookupCount = input.x.getLookupCounts().isEmpty() ? listwithNulls(input.x.getLookupCount()) : input.x.getLookupCounts();
        //Assume input is a list and loop function evaluation for each item, collect it all
        String fnApiName = functionCall.getFunctionDefinition().getName();
        //All 'value from x' values
        if (input.x.getResult() instanceof List && !allInputTypes.contains(ListType.VALUE)) {
            log.debug("Executing a loop for graph {} node {} function {}", context.getGraph().getTargetId(), node.getId(), functionDefinition.getName());
        }

        final Map<String, List<Object>> previousValues = context.getContextChanges();
        for(int i=0;i<inputList.size();i++){
            //make the 'Value From <nodename>' from all previously executed nodes available to loop
            final int index=i;
            context.addToSyncariNamespace("currentLoopIndex", index);

            previousValues.forEach((resultKey, values)->{
                if(index < values.size()){
                    context.put(resultKey,values.get(index));
                }
            });
            Object lookupResult = inputLookupList.size()> i? inputLookupList.get(i) : null;
            Long previousLookupCount = inputLookupCount.size() >i ? inputLookupCount.get(i): null;
            if(FilterFailedResult.isFailedFilter(inputList.get(i))){
                //Failed filter. Pass thru as is
                context.put(functionCall.getCurrentParam().getContextRoot(), Pair.of(
                        new FunctionResult(inputList.get(i),input.x.getDatatype(),lookupResult, previousLookupCount)
                        ,input.y));
            }else{
                context.put(functionCall.getCurrentParam().getContextRoot(), Pair.of(
                        new FunctionResult(dataType.convert(inputList.get(i)),dataType,lookupResult,previousLookupCount)
                        ,input.y));
            }

            context.put("previousLookup", lookupResult);
            context.put("previousLookupCount", previousLookupCount==null?0l:previousLookupCount);
            context.put("previous", inputList.get(i));
            FunctionResult result = null;
            Optional<EntityData> record = Optional.ofNullable((EntityData)context.get("record"));
            //start tracking context changes before evaluating function
            context.trackContextChanges();
            if (WHITE_LIST_ACTION_FUNCTION_FORCACHING.contains(fnApiName)){
                if (record.isPresent()){
                    result = !FilterFailedResult.isFailedFilter(inputList.get(i)) ? context.cacheable("output_" + node.getId() + record.get().getId() + i, ()-> context.isSimulationMode() && MOCK_FUNCTIONS_FOR_SIMULATION.contains(fnApiName)
                            ? input.x
                            : evaluate(functionCall)) : evaluate(functionCall);
                }else{
                        log.error("This should not happen, record should always exists");
                    result = context.isSimulationMode() && MOCK_FUNCTIONS_FOR_SIMULATION.contains(fnApiName)
                            ? input.x
                            : evaluate(functionCall);
                }
            }else{
                result = context.isSimulationMode() && MOCK_FUNCTIONS_FOR_SIMULATION.contains(fnApiName)
                        ? input.x
                        : evaluate(functionCall);
            }

            log.debug("Evaluated record returned by node {} is {}", node.getId(), result.getResult());
            results.add(result.getResult());
            updateContextWithResults(node, result);

            //stop context tracking
            context.stopTrackingContextChanges();
            logNodeAudit(node);
            context.resetCurrentNodeConfig();
            context.remove("Value From " + node.getName());
            lookupResults.add(result.getLookupResult());
            lookupResultCount.add(result.getLookupCount());
            outputType = result.getDatatype();
        }
        //reset the input to original value
        context.put(functionCall.getCurrentParam().getContextRoot(), input);

        //Translate to a single object or a list, depending on data type and input
        List finalLookupResults = passThrough(lookupResults, inputLookupList);
        List<Long> finalLookupResultCount = passThroughCounts(lookupResultCount, inputLookupCount);


        //if the input to the current node was a list, and the current node does not accept a list, we have unwrapped the input list,
        //executed the current node for each of the values in the input list and collected the results. We now need to pass those
        //results as a list to the next node
        ////Example split-> uppercase ->CamelCase -> First. 'Split' on "firstname middlename lastname" returns a list [firstname,middlename,lastname], 'uppercase' does not accept a list, so 'uppercase' will be
        //executed for eeach value from 'split' and collected as a list [FIRSTNAME,MIDDLENAME,LASTNAME] and sent to 'CamelCase'. 'CamelCase' does not accept a list and
        //will be executed for each value from 'uppercase' and collected to a list [Firstname,Middlename, Lastname]. Finally this list is sent to 'first', which does accept a list
        //and will return the first element 'Firstname'

        if ((input.x.getResult() instanceof List || input.x.getDatatype().equals(ListType.VALUE)) && !allInputTypes.contains(ListType.VALUE)) {
            return new FunctionResult(results, outputType, finalLookupResults, finalLookupResultCount);
        } else {
            //If the input to the current node was single valued, or the current node expects a List as input,
            //the output is just a list with a single value, so extract that single value and send that out to the next node
            //Example split-> reverse -> First (split returns a list, reverse accepts a list, first accepts a list
            //"firstname middlename lastname"  -> split:[firstname,middlename,lastname]->reverse:[lastname,middlename,firstname]->first:lastname
            return new FunctionResult(results.isEmpty() ? null : results.get(0), outputType, finalLookupResults.isEmpty() ? FunctionResult.NO_RESULTS : finalLookupResults.get(0), finalLookupResultCount.get(0));
        }
    }

    private void logNodeAudit(MappingNode node) {
        if (context.isNodeLoggingOn()) {
            pipelineNodeAuditService.queue(new NodeAudit(context.getGraph(), node, context));
        }
    }

    protected FunctionResult executeFunctionV2(FunctionCall functionCall, Pair<FunctionResult, MappingNode> input, MappingNode node) {
        //Pair<FunctionResult, MappingNode> input = getInput(functionCall);
        FunctionDefinition functionDefinition = functionCall.getFunctionDefinition();
        Datatype dataType = functionDefinition.getParam().getDatatype();
        Datatype outputType = functionDefinition.getOutputType();
        Datatype inputType = functionDefinition.getInputType();
        List<Datatype> allInputTypes= new ArrayList<>(functionDefinition.getAdditionalInputTypes());
        allInputTypes.add(inputType);

        String fnApiName = functionCall.getFunctionDefinition().getName();
        if (input.x.getResult() instanceof List && !allInputTypes.contains(ListType.VALUE) && !LIST_INPUT_FUNCTIONS.contains(fnApiName)) {
            log.error(i18n("multivalued_function_error", node.getName(), context.getGraph().getName()));
            throw new RuntimeException(i18n("multivalued_function_error", node.getName(), context.getGraph().getName()));
        }

        Object inputValue = input.x.getResult();
        Object inputLookupValue = input.x.getLookupResult();
        Long inputLookupCount = input.x.getLookupCount();

        if(FilterFailedResult.isFailedFilter(inputValue)){
            //Failed filter. Pass thru as is
            context.put(functionCall.getCurrentParam().getContextRoot(), Pair.of(
                    new FunctionResult(inputValue,input.x.getDatatype(),inputLookupValue, inputLookupCount)
                    ,input.y));
        }else{
            context.put(functionCall.getCurrentParam().getContextRoot(), Pair.of(
                    new FunctionResult(dataType.convert(inputValue),dataType,inputLookupValue,inputLookupCount)
                    ,input.y));
        }

        context.put("previousLookup", inputLookupValue);
        context.put("previousLookupCount", inputLookupCount==null?0l:inputLookupCount);
        context.put("previous", inputValue);
        FunctionResult result = null;
        Optional<EntityData> record = Optional.ofNullable((EntityData)context.get("record"));
        if (WHITE_LIST_ACTION_FUNCTION_FORCACHING.contains(fnApiName)){
            if (record.isPresent()){
                if (context.getCurrentLoopContext() != null) {
                    var indices = context.allIndices().stream().map(loopIndex -> loopIndex.toString()).collect(Collectors.joining(","));
                    result = !FilterFailedResult.isFailedFilter(inputValue) ? context.cacheable("output_" + node.getId() + record.get().getId() + indices, ()-> context.isSimulationMode() && MOCK_FUNCTIONS_FOR_SIMULATION.contains(fnApiName)
                            ? input.x
                            : evaluate(functionCall)) : evaluate(functionCall);
                } else {
                    result = !FilterFailedResult.isFailedFilter(inputValue) ? context.cacheable("output_" + node.getId() + record.get().getId(), ()-> context.isSimulationMode() && MOCK_FUNCTIONS_FOR_SIMULATION.contains(fnApiName)
                            ? input.x
                            : evaluate(functionCall)) : evaluate(functionCall);
                }

            }else{
                log.error("This should not happen, record should always exists");
                result = context.isSimulationMode() && MOCK_FUNCTIONS_FOR_SIMULATION.contains(fnApiName)
                        ? input.x
                        : evaluate(functionCall);
            }
        }else{
            result = context.isSimulationMode() && MOCK_FUNCTIONS_FOR_SIMULATION.contains(fnApiName)
                    ? input.x
                    : evaluate(functionCall);
        }

        log.debug("Evaluated record returned by node {} is {}", node.getId(), result.getResult());
        updateContextWithResults(node, result);
        logNodeAudit(node);
        context.resetCurrentNodeConfig();
        //stop context tracking
        outputType = result.getDatatype();

        //reset the input to original value
        context.put(functionCall.getCurrentParam().getContextRoot(), input);

        //Translate to a single object or a list, depending on data type and input
        Object finalLookupResult = FunctionResult.isLookupResult(result.getLookupResult()) ? result.getLookupResult() : inputLookupValue;
        Long finalLookupResultCount = result.getLookupCount() != null ? result.getLookupCount() : inputLookupCount;
        return new FunctionResult(result.getResult(), outputType, finalLookupResult, finalLookupResultCount);
    }

    private void updateContextWithResults(MappingNode node, FunctionResult result) {
        context.put(node.getName(), result.typedValue());
        context.put("Lookup From " + node.getName(), result.getLookupResult());
        context.put("Lookup Count From " + node.getName(), result.getLookupCount());
        //Hack for requiring all functions to repeat this following code
        if (!context.containsKey("Value From " + node.getName())) {
            context.put("Value From " + node.getName(), result.getResult());
        }
    }

    private FunctionResult evaluate(FunctionCall functionCall) {
        try {
            return evaluator.evaluate(functionCall, context);
        }catch (TerminateExecutionPathException termEx){
            return new FunctionResult(FilterFailedResult.VALUE, ObjectType.VALUE);
        }
    }

    /*
      * Extracts the correct input from all the connected edges. If multiple edges are connected to this node,
      * only one of them is expected to be successful and the other a failure (Multiple branches of filters converging, for example)
      * If no successful inputs are found, the first failed input is returned back.
     */
    private Pair<FunctionResult, MappingNode> getInput(FunctionCall functionCall) {
        List<Pair<FunctionResult, MappingNode>> currentInputs = context.getCurrentInputs();

        currentInputs.stream().forEach(i -> log.debug("Input for current node {} is {}", context.getCurrentNode().getId(), i.getX().getResult()));
        List<Pair<FunctionResult, MappingNode>> successfulInputs = currentInputs.stream().filter(i -> i != null && !FilterFailedResult.isFailedFilter(i.x.getResult())).collect(Collectors.toList());
        var failedInputs = currentInputs.stream().filter(i-> i != null && FilterFailedResult.isFailedFilter(i.x.getResult()));
        //success or failure
        if(successfulInputs.isEmpty()){
            //all results are failures. Propagate one of tghem
            return failedInputs.findFirst().orElse((Pair<FunctionResult,MappingNode>) context.get(functionCall.getFirstParam().getContextRoot()));
        }else if(successfulInputs.size() >1){
            //more than one successes, convert results to a single result (the node doesn't matter, so pick the first one)
            List<Object> allResults = successfulInputs.stream().map(p -> p.x.getResult()).collect(Collectors.toList());
            return Pair.of(new FunctionResult(allResults,ListType.VALUE),successfulInputs.get(0).y);
        }else{
            //only one result, return as is
            return successfulInputs.get(0);
        }
    }

    private List<? extends Object> passThrough(List<? extends Object> lookupResults, List<Object> inputLookupList) {
        //If the current lookupResults are all NO_RESULT objects, the function didnt do any lookups. Return input,
        //else return current results
        return lookupResults.stream().allMatch(r->!FunctionResult.isLookupResult(r)) ? inputLookupList : lookupResults;
    }
    private List<Long> passThroughCounts(List<Long> lookupCounts,List<Long> inputLookupCount) {
        //If the current lookupCounts are all nulls, the function didnt do any lookups. Return input,
        //else return current results
        return lookupCounts.stream().allMatch(r->r==null) ? inputLookupCount : lookupCounts;
    }

    private List toList(Object result, List<Datatype> inputType) {
        //Input valye is a List & Function does not expect List as a parameter,
        // This means the previous node execution returned a list of values, and the downstream nodes must be executed for
        // each element of this list
        if( result instanceof List && !inputType.contains(ListType.VALUE)){
            return List.class.cast(result);
        } else {
            //The previous node did not return a list, or it returned a list and current node accepts a ListType as param
            //So wrap the value in a single element list and return it back
            if (result != null && result instanceof List) {
                result = List.class.cast(result).stream().filter(r -> !FilterFailedResult.isFailedFilter(r)).collect(Collectors.toList());
            }
            var listResult = new ArrayList<>();
            listResult.add(result);
            return listResult;
        }
    }

    private boolean isSimpleLoops(GraphContext context) {
        return context.isSimpleLoopOn();
    }
}
