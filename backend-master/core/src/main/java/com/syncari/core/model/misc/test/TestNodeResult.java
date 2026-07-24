package com.syncari.core.model.misc.test;

import com.syncari.core.model.UUIDAuditModel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.springframework.data.mongodb.core.index.Indexed;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents the result of executing a node in a pipeline test.
 * Stored as individual documents in testNodeResult collection (Spring Data MongoDB infers the collection name).
 *
 * Note: This class extends UUIDAuditModel to have proper entity structure,
 * but is also used as embedded objects in legacy TestResult documents for backward compatibility.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class TestNodeResult extends UUIDAuditModel {

    // Reference to parent TestResult (only used when stored externally)
    @Indexed
    String testResultId;

    // Sequence/order of this node result (only used when stored externally)
    Integer sequence;

    String nodeId;
    String nodeName;
    // Map of inputs for node. Key:apiName of field, Value:TestNodeResultAttributeValue
    Map<String, TestNodeResultAttributeValue> inputs = new HashMap<>();
    // Map of outputs for node. Key:apiName of field, Value:TestNodeResultAttributeValue
    Map<String, TestNodeResultAttributeValue> outputs = new HashMap<>();
    // TODO: can this be replaced with PipelineTestStatus
    //PipelineTestRunState status;
    Status status;
    String errorMsg;

    public enum Status {
        PENDING,
        COMPLETED,
        SUCCESS,
        FAILED,
        SKIPPED
    }

    public void addInput(String apiName, TestNodeResultAttributeValue value){
        inputs.put(apiName, value);
    }

    public void addOutput(String apiName, TestNodeResultAttributeValue value){
        outputs.put(apiName, value);
    }
}
