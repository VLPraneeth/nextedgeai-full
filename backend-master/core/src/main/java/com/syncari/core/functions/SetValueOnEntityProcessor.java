package com.syncari.core.functions;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.jtwig.value.Undefined;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.syncari.connector.EntityData;
import com.syncari.core.model.FunctionCall;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.StagedBatchRecord;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.repositories.customer.AttributeRepo;
import com.syncari.core.token.TokenHelper;

import lombok.extern.slf4j.Slf4j;

import static com.syncari.utils.I18n.i18n;

@Slf4j
@Component
public class SetValueOnEntityProcessor implements ProcessFunction {
	@Autowired
    TokenHelper tokenHelper;
	@Autowired
    AttributeRepo attributeProxyRepo;

	@Override
	public Object process(List<Object> inputs, FunctionCall functionCall, GraphContext context) {
		inputs.stream().forEach(i -> log.debug("Incoming Input for node {} is {}", Optional.ofNullable(context.getCurrentNode()).orElse(new MappingNode()).getId(), i));
		String value = (String) functionCall.getConfig().get("newValue");
		EntityData syncariRecord = context.getSyncariRecord();
        if(syncariRecord==null){
            //Check if stagedBatchRecord is in context
            StagedBatchRecord stagedBatchRecord = context.getStagedBatchRecord();
            if(stagedBatchRecord!=null){
                syncariRecord = stagedBatchRecord.getEntityData();
                log.debug("Using Staged record for node {} is {}", Optional.ofNullable(context.getCurrentNode()).orElse(new MappingNode()).getId(), syncariRecord);
            }
        }
		Object input = syncariRecord!=null ? syncariRecord : inputs.stream().filter(i -> i!=null && i!= Undefined.UNDEFINED).findFirst().orElse(null);
		log.debug("Input record used for node {} is {}", Optional.ofNullable(context.getCurrentNode()).orElse(new MappingNode()).getId(), input);
        if(input==null || !EntityData.class.isAssignableFrom(input.getClass())){
        	log.warn("Cannot move with setValueOnEntity. Expected EntityData but got {}", input);
        	return input;
        }
		Optional<EntityData> entityData = Optional.ofNullable((EntityData)input);
        Boolean useEmpty = (Boolean) functionCall.getConfig().get("useEmpty");
        useEmpty = useEmpty == null ? false : useEmpty;
        Map<String, String> setValueFieldMap = (Map) functionCall.getConfig().get("setValueField");
        
        String resolvedValueTemp = "";
		if(value == null) {
			resolvedValueTemp = null;
		} else if(StringUtils.isBlank(value) && !useEmpty) {
			resolvedValueTemp = null;
		} else {
			resolvedValueTemp = tokenHelper.resolveTokens(context, value);
		}
		String resolvedValue = resolvedValueTemp;
		if (checkFailedFilter(resolvedValue)) context.logError(i18n("failed_filter_setvalue"), i18n("failed_filter_setvalue"));
		Boolean newValueAsEmpty = useEmpty && resolvedValue != null && StringUtils.isBlank(resolvedValue);
		String tempAttributeId = (String) functionCall.getConfig().get("attributeDefinitionId");
		if (tempAttributeId == null && setValueFieldMap != null) {
			tempAttributeId = setValueFieldMap.get("attributeDefinitionId");
		}
		String attributeId = tempAttributeId;
		var attributeMaybe = context.cache("field_" + attributeId, () -> attributeProxyRepo.findById(attributeId));
		attributeMaybe.ifPresent(a ->{
			Object newValue = newValueAsEmpty ? resolvedValue : transformObject(inputs, i -> a.convert(resolvedValue), null);
			entityData.ifPresent(e ->
			{
				Object oldValue = a.convert(e.getValue(a.getApiName()));
				
				e.addValue(a.getApiName(), newValue);
				context.set("Value From " + context.getCurrentNode().getName(), newValue);
				context.set("field_" + attributeId, newValue);
				StagedBatchRecord stagedBatchRecord = context.getStagedBatchRecord();
				if(stagedBatchRecord!=null && !Objects.equals(newValue,oldValue)){
					stagedBatchRecord.setModifiedByPipeline(true);
					log.info("Setting value in Staged Batch Record. Old Value is {} and new value is {} for stagedBatchRecordId {}", oldValue, newValue, stagedBatchRecord.getId());
				}
			});
		});
		// Make sure to update the syncariRecord in context to be available for all other threads
		if(syncariRecord != null && entityData.isPresent()) {
			log.debug("Setting syncariRecord as {}", entityData.get());
			context.setSyncariRecord(entityData.get());
		}
		log.debug("Output record returned by node {} is {}", Optional.ofNullable(context.getCurrentNode()).orElse(new MappingNode()).getId(), entityData.orElse(null));
		return entityData.orElse(null);
	}
	
	private Object transformObject(List<Object> inputs, java.util.function.Function<Object, Object> transformer, Object defaultValue) {
        Optional<Object> params = inputs.stream().filter(input -> input!=null && input!= Undefined.UNDEFINED).findFirst();
        return params.map(transformer).orElse(defaultValue);
    }

}
