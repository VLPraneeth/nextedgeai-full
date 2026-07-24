package com.syncari.core.model.misc.fragment;

import com.syncari.connector.exception.NotSupportedException;
import com.syncari.core.model.MappingNode;
import lombok.Data;
import lombok.experimental.Accessors;

import static com.syncari.utils.I18n.i18n;

@Data
@Accessors(chain = true)
public class FragmentNode extends MappingNode {

    String templateId;

    public void validate(){
        switch (getType()) {
            case FUNCTION:
            case ACTION:
            case CORE_ENTITY:
            case CORE_ATTRIBUTE:
                break;

            default:
                throw new NotSupportedException(String.format(i18n("fragment_node_not_supported_error"), getType().name()));
        }
    }
}
