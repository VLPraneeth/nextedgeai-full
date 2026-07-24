package com.syncari.core.model.misc.fragment;

import com.syncari.core.model.InputPort;
import com.syncari.core.model.OutputPort;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

@Data
@Accessors(chain = true)
@EqualsAndHashCode
public class FragmentEdge {

    @EqualsAndHashCode.Exclude
    String templateId;
    FragmentNode sourceStage;
    FragmentNode destinationStage;
    InputPort input;
    OutputPort output;

    public void validate() {
        validateCondition(sourceStage == null, i18n("fragment_edge_not_connected_to_node", "Source"));
        validateCondition(destinationStage == null, i18n("fragment_edge_not_connected_to_node", "Destination"));
        validateCondition(output == null, i18n("fragment_edge_not_connected_to_port", sourceStage.getName(), "output"));
        validateCondition(input == null, i18n("fragment_edge_not_connected_to_port", destinationStage.getName(), "input"));
        validateCondition(sourceStage.equals(destinationStage), i18n("fragment_edge_cyclic_reference", sourceStage.getName()));

        validateCondition(!(input.getDatatype().canConvert(output.getDatatype())|| input.getDatatype().equals(output.getDatatype())),
                i18n("fragment_edge_datatype_mismatch",input.getDatatype().getName(), output.getDatatype().getName(),sourceStage.getName(), destinationStage.getName()));
    }
}
