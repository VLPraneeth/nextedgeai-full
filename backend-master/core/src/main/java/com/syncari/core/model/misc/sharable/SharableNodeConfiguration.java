package com.syncari.core.model.misc.sharable;

import java.util.List;

import com.syncari.core.model.MappingNode;
import com.syncari.core.model.NodeConfiguration;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.pipeline.NodeConfigurationVisitor;
import com.syncari.core.validation.NodeValidatorVisitor;
import com.syncari.core.validation.ValidationContext;

public interface SharableNodeConfiguration extends NodeConfiguration {

    default void accept(NodeConfigurationVisitor visitor, MappingNode node){
        // No-op
    }

    default void accept(NodeConfigurationVisitor visitor){
        // No-op
    }

    default void accept(NodeValidatorVisitor validator, ValidationContext validationContext){
        // No-op
    }
    
    default List<ValidationError> acceptWithoutException(NodeValidatorVisitor validator, ValidationContext validationContext) {
        return List.of();
    }
    
    default SharableNodeConfiguration copy() {
      return this;
    }
    
}
