package com.syncari.core.pipeline;

import com.syncari.connector.EntityData;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.NodeStatusMetric;
import com.syncari.core.model.util.Scope;
import com.syncari.core.sync.CurrentBatch;
import com.syncari.utils.Pair;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.SetUtils;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.syncari.utils.I18n.i18n;

@Data
@Accessors(chain = true)
public class GraphContext extends HashMap<String, Object> {
    public static final String CONNECTED_RECORDS = "connectedRecords";
    private GraphStatsCollector statsCollector = new GraphStatsCollector();
    private MappingGraph graph;
    private CurrentBatch currentBatch;
    private EntityDefinition syncariEntity;
    private MappingNode currentNode;
    private TestContext testContext = new TestContext();
    private RealtimeSyncContext realtimeSyncContext = new RealtimeSyncContext();
    private BatchActionContext batchActionContext = new BatchActionContext();
    //this is available throughout the cycle and shared across stages
    private Map<String, Object> cache = new ConcurrentHashMap<>();
    private boolean testMode;
    private boolean simulationMode;
    private boolean isResync;
    private StandardEvaluationContext evaluationContext;

    // this is a sink side node execution cache to cache the result of multiple nodes already visited.
    private Map<String, Object> nodeResultCache = new ConcurrentHashMap<>();

    //used only for cross graph caching?
    private Map<String, Object> capturedContext = new HashMap<>();
    private long startTime;
    private long endTime;

    public Map<String, Object> getCurrentNodeConfig() {
        return currentNodeConfig;
    }

    private Map<String, Object> currentNodeConfig = new HashMap<>();
    // Syncari recordid vs error
    private Map<String, List<NodeError>> errors = new HashMap<>();
    private ConcurrentHashMap<String, NodeStatusMetric> nodeStatusMetrics = new ConcurrentHashMap<>();
    private boolean captureContextFlag = false;

    //All the 'Value From <node name>' values are captured in this map
    //to make them available inside loops - This is legacy and
    //use ONLY to support legacy loops, and nothing else.
    private Map<String, List<Object>> contextChanges = new HashMap<>();

    //Capture changes to context, made by node execution
    private Map<String, List<Object>> changesByNodeExecution = new HashMap<>();

    // The current SyncariId of the record being processed.
    // Used for capturing test results by processing node.
    // Should be unique per context across parallel node executions.
    String currentSyncariId;

    //tracks changes made to the current context in a hashmap
    private boolean trackContextChanges;
    private Optional<Connector> datastore = Optional.empty();
    public GraphContext trackContextChanges(){
        this.setTrackContextChanges(true);
        return this;
    }

    public GraphContext stopTrackingContextChanges(){
        this.setTrackContextChanges(false);
        return this;
    }

    public StandardEvaluationContext getEvaluationContext(){
        if(evaluationContext==null){
            evaluationContext = new StandardEvaluationContext(this);
        }
        return evaluationContext;
    }

    public <T> T cache(String key, Supplier<T> supplier){
        if(cache.containsKey(key)){
            return (T) cache.get(key);
        }else{
            T value = supplier.get();
            if(value!=null) {
                cache.put(key, value);
            }
            return value;
        }
    }

    public void cache(String key, Runnable runnable){
        if (!cache.containsKey(key)) {
            runnable.run();
            cache.put(key, true);
        }
    }

    public <T> void cache(String key, T value){
        if(value!=null) {
            cache.put(key, value);
        }
    }

    public <T> T cached(String key){
        return (T) cache.get(key);
    }

    public <T> T cachedOrDefault(String key,T defaultValue){
        return cache.containsKey(key)? (T) cache.get(key) : defaultValue;
    }

    public <T> T removeFromCache(String key){
        return (T) cache.remove(key);
    }


    public List<Pair<FunctionResult, MappingNode>> getCurrentInputs(){

        List<Edge> inboundEdges = getGraph().getInboundEdges(getCurrentNode());
        return inboundEdges.stream().filter(e -> containsKey("output_" + e.getSourceStage().getId()))
                .map(e -> (Pair<FunctionResult, MappingNode>)get("output_" + e.getSourceStage().getId()))
                .distinct().collect(Collectors.toList());
    }

    //TODO: Make this metadata based

    public void addConnectedRecord(EntityDefinition externalEntityDefinition, EntityData record){
        Map<EntityDefinition,List<EntityData>> connectedRecords = (Map<EntityDefinition,List<EntityData>>) getOrDefault(CONNECTED_RECORDS, new HashMap<>());
        List<EntityData> records = connectedRecords.getOrDefault(externalEntityDefinition,new ArrayList<>());
        records.add(record);
        connectedRecords.put(externalEntityDefinition,records);
        set(CONNECTED_RECORDS,connectedRecords);
    }

    public void updateSyncariRecord(EntityData updated){
        put("existing", updated);
        if(syncariEntity != null) {
            syncariEntity.getActiveAttributes().forEach(attribute -> {
                if (updated.has(attribute.getApiName())) {
                    put("field_" + attribute.getId(), updated.getValue(attribute.getApiName()));
                }
            });
        }
    }

    public void addCoreEntityNodeInput(String syncariId, Pair<FunctionResult, MappingNode> nodeResult){
        if (isSimulationMode() || isTestMode()) {
            // we need to store the input to coreEntity for each record
            testContext.getCoreEntityNodeInput().put(syncariId, nodeResult.y);
        }
    }

    public void addSinkEntityNodeInput(Pair<FunctionResult, MappingNode> nodeResult){
        if (isSimulationMode() || isTestMode()) {
            set("sink_entity_input", nodeResult);
        }
    }

    public Map<EntityDefinition, List<EntityData>> getAllConnectedRecords(){
        return (Map<EntityDefinition,List<EntityData>>) getOrDefault(CONNECTED_RECORDS, new HashMap<>());
    }

    public void clearConnectedRecords(){
        remove(CONNECTED_RECORDS);
    }


    public EntityData getSyncariRecord(){
        return (EntityData)get("syncariRecord");
    }
    public void setSyncariRecord(EntityData syncariRecord){
        put("syncariRecord", syncariRecord);
    }
    public void setStagedBatchRecord(StagedBatchRecord stagedBatchRecord){
        put("stagedRecord", stagedBatchRecord);
        put("record", stagedBatchRecord.getEntityData());
        put("previous", stagedBatchRecord.getEntityData());
    }
    public StagedBatchRecord getStagedBatchRecord(){
        return (StagedBatchRecord) get("stagedRecord");
    }

    @Override
    public boolean containsKey(Object key) {
        return super.containsKey(key) || (parent != null && parent.containsKey(key));
    }

    @Override
    public boolean containsValue(Object value) {
        return super.containsValue(value) || (parent != null && parent.containsValue(value));
    }

    @Override
    public int size() {
        return keySet().size();
    }

    @Override
    public Set<String> keySet() {
        if (parent == null) {
            return super.keySet();
        } else {
            final HashSet<String> keys = new HashSet<>(super.keySet());
            keys.addAll(parent.keySet());
            return keys;
        }
    }

    @Override
    public Collection<Object> values() {
        return parent == null ? super.values() : CollectionUtils.union(super.values(), parent.values());
    }

    @Override
    public void forEach(BiConsumer<? super String, ? super Object> action) {
        super.forEach(action);
        if (parent != null) {
            //the Set returned by super.keyset delegayes some Set methods to the backing map,
            //including `contains` -> map.containsKey() . Since we have overridden containsKey,
            //this creates odd behaviors. So we copy the actual keys in the context
            final Set<String> currentKeys = new HashSet<>(super.keySet());
            parent.forEach((curkey, curval) -> {
                if (!currentKeys.contains(curkey)) {
                    action.accept(curkey, curval);
                }
            });
        }
    }

    @Override
    public boolean isEmpty() {
        return super.isEmpty() && (parent == null || parent.isEmpty());
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        return parent == null ? super.entrySet() : SetUtils.union(super.entrySet(), parent.entrySet());
    }

    @Override
    public Object getOrDefault(Object key, Object defaultValue) {
        final Object o = get(key);
        return o == null ? defaultValue : o;
    }

    public Object get(Object key) {
        if (super.containsKey(key)) {
            return super.get(key);
        } else if (parent != null) {
            return parent.get(key);
        } else {
            return null;
        }
    }

    public <T> T getValue(String key, Class<T> type){
        Object value = get(key);
        if(value==null) return null;
        if(type.isAssignableFrom(value.getClass())){
            return type.cast(value);
        }
        throw new RuntimeException(String.format("Cannot cast %s to %s", value, type.getSimpleName()));
    }

    public <T> Optional<T> getValueOpt(String key, Class<T> type){
        Object value = get(key);
        if(value==null) return Optional.empty();
        if(type.isAssignableFrom(value.getClass())){
            return Optional.of(type.cast(value));
        }
        throw new RuntimeException(String.format("Cannot cast %s to %s", value, type.getSimpleName()));
    }

    private List<GraphContext> children = new ArrayList<>();
    private GraphContext parent;

    public GraphContext(Map<String, Object> initial) {
        super(1024);
        putAll(initial);
    }

    public GraphContext() {
        initTempVariableNamespace();
    }

    public GraphContext set(String key, Object value) {
        this.put(key, value);
        return this;
    }

    public GraphContext createSubContext(MappingGraph graph) {
        GraphContext graphContext = new GraphContext().setGraph(graph).setCurrentNode(null).setCurrentBatch(currentBatch)
                .setStatsCollector(statsCollector).setTestMode(isTestMode()).setSimulationMode(isSimulationMode())
                .setSyncariEntity(syncariEntity)
                .setParent(this)
                .setErrors(errors)
                .setRealtimeSyncContext(realtimeSyncContext)
                .setTempVariables(getTempVariables())
                .setTestContext(testContext.setSimulationMode(isSimulationMode()));

        graphContext.cache = cache;
        graphContext.nodeResultCache = nodeResultCache;
        graphContext.nodeStatusMetrics = nodeStatusMetrics;
        children.add(graphContext);
        return graphContext;

    }

    public GraphContext getOrCreateSubContext(MappingGraph graph){
        return findSubContext(graph).orElseGet(() -> createSubContext(graph));
    }

    public Optional<GraphContext> findSubContext(MappingGraph graph){
        return children.stream().filter(ctx -> ctx.getGraph() != null && ctx.getGraph().getId().equals(graph.getId())).findFirst();
    }



    /**
     * Copies context, without children. Its a copy by reference
     * Make sure the context does not contain any non-threadsafe values
     * @return
     */
    public GraphContext
    copy() {
        //donot copy previousValues
        GraphContext graphContext = new GraphContext(this)
                .setGraph(graph).setCurrentNode(currentNode).setCurrentBatch(currentBatch)
                .setStatsCollector(statsCollector).setTestMode(isTestMode())
                .setSimulationMode(isSimulationMode())
                .setTestContext(testContext)
                .setRealtimeSyncContext(realtimeSyncContext)
                .setCurrentSyncariId("")
                .setSyncariEntity(syncariEntity)
                .setBatchActionContext(batchActionContext);
        graphContext.cache = cache;
        graphContext.nodeResultCache = nodeResultCache;
        graphContext.nodeStatusMetrics = nodeStatusMetrics;
        return graphContext;

    }

    public Stat getStat() {
        return statsCollector.getStat(graph, currentNode, currentBatch == null? "default":currentBatch.getCurrentBatchId());
    }

    public void clear() {
        contextChanges.clear();
        clearContext();
        graph = null;
        currentBatch = null;
        currentNode = null;
    }
    public void clearActionResultsCache() {
        nodeResultCache.clear();
    }
    public void clearContext() {
        // clear everything except "syncari.temp" namespace (which is used for temp variables right now)
        var map = getTempVariables();
        super.clear();
        setTempVariables(map);
    }

    public void clearChildren() {
        children = new ArrayList<>();
    }

    public void clearStats() {
        children.forEach(context -> context.clearStats());
        statsCollector.clear();
    }

    public List<PipelineStats> getAllStats() {
        return statsCollector.getCurrentStats();
    }

    public GraphContext addError(String syncariRecordId, NodeError error) {
    	errors.putIfAbsent(syncariRecordId, new ArrayList());
    	errors.get(syncariRecordId).add(error);
    	return this;
    }

    public GraphContext setTestMode(boolean testMode) {
        this.testMode = testMode;
        return this;
    }
    public boolean isTestMode(){
        return testMode;
    }

    public void captureTestOutputForNode(FunctionResult result, MappingNode node, MappingNode inputNode){
        if(!isSimulationMode() && !isTestMode()) {
            return;
        }

        FunctionResult updatedResult = result;
        // If FilterFailed with valid value, capture snapshot of EntityData
        if(FilterFailedResult.isFailedFilter(result.getResult())){
            FilterFailedResult failedResult = FilterFailedResult.normalizedFailedResult(result.getResult());
            if(!failedResult.hasInvalidResults() && failedResult.getValue() instanceof FunctionResult){
                var failedNodeOutput = (FunctionResult) failedResult.getValue();
                if(failedNodeOutput.getResult() instanceof EntityData){
                    EntityData output = (EntityData) failedNodeOutput.getResult();
                    updatedResult = new FunctionResult(new FilterFailedResult(output.withValues(new HashMap<>(output.getValues()))),
                            result.getDatatype(), result.getLookupResult(),result.getLookupCount());
                }
            }
        } else if(result.getResult() instanceof EntityData){
            // if capturing successful EntityData output, capture a snapshot and not the same object
            EntityData output = (EntityData) result.getResult();
            updatedResult = new FunctionResult(output.withValues(new HashMap<>(output.getValues())), result.getDatatype(), result.getLookupResult(),result.getLookupCount());
        }
        testContext.captureNodeOutput(currentSyncariId, node.getId(), updatedResult, inputNode == null ? null : inputNode.getId());
    }

    public void captureTestOutputForCoreEntityNode(FunctionResult result, MappingNode node){
        MappingNode inputNode = testContext.getCoreEntityNodeInput().get(currentSyncariId);
        captureTestOutputForNode(result, node, inputNode);
    }

    public void captureTestOutputForSinkEntityNode(FunctionResult result, MappingNode node){
        Pair<FunctionResult, MappingNode> sinkEntityInput = (Pair<FunctionResult, MappingNode>) get("sink_entity_input");
        var inputNode = sinkEntityInput != null ? sinkEntityInput.y : null;
        captureTestOutputForNode(result, node, inputNode);
    }

    public <T> T getNodeResultsCache(String key,T defaultValue){
        return nodeResultCache.containsKey(key)? (T) nodeResultCache.get(key) : defaultValue;
    }

    public void loadSynapseConfigFromCache() {
        if(cache.containsKey("synapseConfigs")) {
            Map<String, Object> cachedConfigs = (Map<String, Object>) cache.get("synapseConfigs");
            this.putAll(cachedConfigs);
        }
    }

    public void loadServiceCredsFromCache() {
        if(cache.containsKey("serviceCredentials")) {
            Map<String, Object> cachedConfigs = (Map<String, Object>) cache.get("serviceCredentials");
            this.putAll(cachedConfigs);
        }
    }

    public void initTempVariableNamespace() {
        Map<String, Object> tempMap = new HashMap<String, Object>();
        tempMap.put("temp", new HashMap<String, Object>());
        this.put("syncari", tempMap);
    }
    
    public void setTempVariable(String key, Object value) {
		Map<String, Object> syncariMap = (Map<String, Object>) this.get("syncari");
		if(syncariMap == null) {
			syncariMap = new HashMap<String, Object>();
			this.put("syncari", syncariMap);
		}
		Map<String, Object> tempMap = (Map<String, Object>) syncariMap.get("temp");
		if(tempMap == null) {
			tempMap = new HashMap<String, Object>();
			syncariMap.put("temp", tempMap);
		}
		//if(!tempMap.containsKey(key)) {
        tempMap.put(key, value);
		//}
    }

    public Map<String, Object> getTempVariables() {
    	Map<String, Object> result = new HashMap<String, Object>();
    	Map<String, Object> syncariMap = (Map<String, Object>) this.get("syncari");
		if(syncariMap != null) {
			Map<String, Object> tempMap = (Map<String, Object>) syncariMap.get("temp");
			return tempMap;
		}
		return result;
    }

    private GraphContext setTempVariables(Map<String, Object> map) {
        Map<String, Object> syncariMap = (Map<String, Object>) this.get("syncari");
        if (syncariMap == null) {
            syncariMap = new HashMap<String, Object>();
        }
        syncariMap.put("temp", map);
        this.put("syncari", syncariMap);
        return this;
    }

    public void addToSyncariNamespace(String key, Object value) {
        Map<String, Object> syncariMap = (Map<String, Object>) this.get("syncari");
        if (syncariMap == null) {
            syncariMap = new HashMap<String, Object>();
        }
        syncariMap.put(key, value);
        this.put("syncari", syncariMap);
    }


    // This put is for actions and state changing functions, we need to call this instead of calling put when
    // check state changing functions how do we call this.

    public Object put(String key, Object value){
        if (this.captureContextFlag){
            this.capturedContext.put(key,value);
        }
        //captureContext is managed by caching APIs. Cannot use it here. This is bad design
        if (this.trackContextChanges){
            //track all changes
            final List<Object> results = contextChanges.getOrDefault(key, new ArrayList<>());
            results.add(value);
            contextChanges.put(key, results);
            final List<Object> values = new ArrayList<>();
            values.add(value);
            changesByNodeExecution.put(key, values);
        }
        return super.put(key, value);
    }

    public Object remove(Object key) {
        if (this.captureContextFlag) {
            this.capturedContext.remove(key);
        }
        //captureContext is managed by caching APIs. Cannot use it here. This is bad design
        if (this.trackContextChanges) {
            contextChanges.remove(key);
        }
        return super.remove(key);
    }

    public <T> void recordNodeInputs(String configName, T value) {
        currentNodeConfig.put(configName, value);
    }

    public <T> T recordNodeInputs(String configName, Supplier<T> valueGenerator) {
        T value = valueGenerator.get();
        currentNodeConfig.put(configName, value);
        return value;
    }

    public Optional<MappingGraph> getEntityPipeline() {
        if (getGraph().getScope() == Scope.ENTITY) {
            return Optional.of(getGraph());
        }
        if (getParent() == null || getParent().getGraph() == null || getParent().getGraph().getScope() != Scope.ENTITY) {
            return Optional.empty();
        }
        return Optional.of(getParent().getGraph());
    }

    public void resetCurrentNodeConfig() {
        currentNodeConfig = new HashMap<>();
        changesByNodeExecution = new HashMap<>();
    }

    public GraphContext addResult(Object result) {
        final String resultKey = getCurrentNode().isActionNode() ? String.format("Action Result From %s", getCurrentNode().getName()) :
                String.format("Value From %s", getCurrentNode().getName());
        put(resultKey, result);
        put("previousValue", result);
        return this;
    }

    public void startTimer() {
        startTime = System.currentTimeMillis();
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public long getTimeTaken() {
        return endTime - startTime;
    }

    public void endTimer() {
        endTime = System.currentTimeMillis();
    }

    public void clearCache(String key) {
        cache.remove(key);
    }

    @Data
    @AllArgsConstructor
    class NodeResult<T> {
        T result;
        Map<String, Object> contextChanges = new HashMap<>();
    }

    public <T> T cacheable(String key, Supplier<T> supplier) {
        Supplier<NodeResult<T>> withCapture = () -> {
            captureContextFlag = true;
            capturedContext = new HashMap<>();
            try {
                T result = supplier.get();
                return new NodeResult(result, capturedContext);
            }finally {
                captureContextFlag = false;
                capturedContext = new HashMap<>();
            }
        };
        final NodeResult<T> nodeResult = (NodeResult<T>) nodeResultCache.computeIfAbsent(key, k -> withCapture.get());
        this.putAll(nodeResult.contextChanges);
        return nodeResult.result;
    }

    public void logError(String errorMessage, String errorDetails) {
        NodeError nodeError = new NodeError().setError(errorMessage).setErrorDetails(errorDetails)
                .setNodeId(getCurrentNode().getId()).setNodeName(getCurrentNode().getApiName());
        if (getGraph() != null) {
            nodeError.setGraphName(getGraph().getName()).setGraphId(getGraph().getId())
                    .setScope(getGraph().getScope()).setTargetId(getGraph().getTargetId());
        }
        addError(getCurrentSyncariId(), nodeError);
    }

    Deque<LoopContext> loopContexts = new ArrayDeque<>();

    public LoopContext getLoopContext(String name) {
        if (!loopContexts.isEmpty() && loopContexts.peek().getLoopName().equals(name)) {
            return loopContexts.peek();
        }
        return null;
    }

    public boolean isLoopCompleted() {
        return this.containsKey("loop_output_" + currentNode.getId()) && getCurrentLoopContext() == null;
    }
    public void endLoop(MappingNode loopNode) {
        // set the loop completion in context
        this.put("loop_output_" + loopNode.getId(), true);
        loopContexts.pop();
    }

    public LoopContext createLoopContext(String name) {
        var context = new LoopContext(name);
        loopContexts.push(context);
        return context;
    }

    public LoopContext getCurrentLoopContext() {
        return !loopContexts.isEmpty() ? loopContexts.peek() : null;
    }

    public List<Integer> allIndices() {
        return loopContexts.stream().map(LoopContext::getCounter).collect(Collectors.toList());
    }

    public boolean isLoopNode() {
        return Optional.ofNullable(getCurrentNode().getConfiguration().getConfigMap()).map(map -> (boolean)map.getOrDefault("loopStart", false)).orElse(false);
    }

    public boolean isInLoop() {
        boolean loopNode = Optional.ofNullable(getCurrentNode().getConfiguration().getConfigMap()).map(map -> (boolean)map.getOrDefault("loopStart", false)).orElse(false);
        LoopContext currentLoopContext = getLoopContext(getCurrentNode().getName());
        return loopNode && currentLoopContext != null;
    }

    public boolean continueLoop() {
        boolean loopStart = Optional.ofNullable(getCurrentNode().getConfiguration().getConfigMap()).map(map -> (boolean)map.getOrDefault("loopStart", false)).orElse(false);
        LoopContext currentLoopContext = getLoopContext(getCurrentNode().getName());
        return loopStart && currentLoopContext != null ? currentLoopContext.isEvalCondition() : false;
    }

    @Data
    public static class LoopContext {
        private int index;
        private Object value;
        private Object nextElement;
        private Stack<IterationData> iterationStack = new Stack<>();
        private Iterator<? extends Object> iterator;
        private String loopName;
        private Object input;
        private static int MAX_LOOP_ITERATIONS = 10000;
        private int maxIterations = MAX_LOOP_ITERATIONS;
        private int counter = 0;

        public LoopContext(String loopName) {
            this.loopName = loopName;
        }

        @Data
        public static class IterationData {
            private Object result;
            private boolean evalCondition;
        }

        public void incrementIndex() {
            index++;
        }

        public void incrementCounter() {
            counter++;
        }

        public int getCounter() {
            return counter;
        }

        public void setIterator(Iterator<? extends Object> iterator) {
            this.iterator = iterator;
        }

        public void pushIterationData(Object result, boolean evalCondition) {
            IterationData data = new IterationData();
            data.setResult(result);
            data.setEvalCondition(evalCondition);
            iterationStack.push(data);
        }

        public boolean isEvalCondition() {

            if (counter > maxIterations) {
                throw new RuntimeException(i18n("possible_infinite_loop_error", loopName, maxIterations));
            }

            return !iterationStack.isEmpty() && iterationStack.peek().isEvalCondition();
        }
    }
    public boolean isNodeLoggingOn() {
        return getEntityPipeline().map(e -> e.isNodeLoggingOn()).orElse(false);
    }

    public boolean isSimpleLoopOn() {
        return getEntityPipeline().map(e -> e.isSimpleLoopsOn()).orElse(false);
    }

    public Map<String, List<Object>> getChangesByNode() {
        return changesByNodeExecution;
    }

}

