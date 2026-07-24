package com.syncari.core.model;

import static com.syncari.core.utils.ValidationUtils.validateCondition;

import java.util.ArrayList;
import java.util.List;

import com.syncari.core.model.util.ErrorCode;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.data.mongodb.core.mapping.DBRef;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.ValidationError;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class Edge extends UUIDAuditModel {

    private OutputPort output;
    @DBRef
    private MappingNode sourceStage;
    private String graphId;
    @DBRef
    private MappingNode destinationStage;
    private InputPort input;
    private String originalId;

    public void validate(String graphName) {
    	var errors = validateWithoutException(graphName);
    	if(errors != null && !errors.isEmpty()) {
    		throw new SyncariValidationException(errors.get(0).getMessage());
    	}
    }
    
    public List<ValidationError> validateWithoutException(String graphName) {
		List<ValidationError> errors = new ArrayList<>();
		validateCondition(
				sourceStage == null ? ValidationError.globalError()
						: ValidationError.scopedError(sourceStage.getScope(), sourceStage.getId()),
				output == null, "Edge from %s not connected to output port in %s pipeline",
				ErrorCode.E1173.getCode(), sourceStage != null ? sourceStage.getName() : "a node", graphName).ifPresent(e -> errors.add(e));

		validateCondition(
				destinationStage == null ? ValidationError.globalError()
						: ValidationError.scopedError(destinationStage.getScope(), destinationStage.getId()),
				input == null, "Edge to %s not connected to input port in %s pipeline",
				ErrorCode.E1174.getCode(), destinationStage != null ? destinationStage.getName() : "a node", graphName)
						.ifPresent(e -> errors.add(e));

		validateCondition(ValidationError.globalError(), sourceStage == null,
				"Edge not connected to Source node in %s pipeline", ErrorCode.E1175.getCode(), graphName).ifPresent(e -> errors.add(e));
		validateCondition(ValidationError.globalError(), destinationStage == null,
				"Edge not connected to Destination node in %s pipeline", ErrorCode.E1176.getCode(), graphName).ifPresent(e -> errors.add(e));
		if (ObjectUtils.allNotNull(input, output, sourceStage, destinationStage)) {
			validateCondition(ValidationError.scopedError(sourceStage.getScope(), sourceStage.getId()),
					!(input.getDatatype().canConvert(output.getDatatype())
							|| input.getDatatype().equals(output.getDatatype())),
					"Input data type %s does not match Output Datatype %s for edge between %s and %s in %s pipeline",
					ErrorCode.E1177.getCode(), output.getDatatype().getName(), input.getDatatype().getName(), sourceStage.getName(), // sourceStage has output port and destinationStage has inputPort
					destinationStage.getName(), graphName).ifPresent(e -> errors.add(e));
		}
		return errors;
    }


}
