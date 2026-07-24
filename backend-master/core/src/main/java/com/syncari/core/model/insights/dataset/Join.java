package com.syncari.core.model.insights.dataset;

import com.syncari.core.model.insights.JoinType;
import com.syncari.core.model.insights.QField;
import com.syncari.utils.I18n;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.syncari.core.utils.ValidationUtils.validateCondition;

@Data
@Accessors(chain = true)
public class Join {

    private QField datasetFieldFrom;
    private QField datasetFieldTo;
    private JoinType joinType;

    public void validate(){
        validateCondition(datasetFieldFrom == null, I18n.i18n("error_dataset_join"));
        validateCondition(datasetFieldTo == null, I18n.i18n("error_dataset_join"));
        validateCondition(joinType == null, I18n.i18n("error_dataset_join"));
    }
}
