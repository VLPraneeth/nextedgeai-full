package com.syncari.core.pipeline.jtwig;

import com.syncari.core.datatype.DatetimeType;
import lombok.extern.slf4j.Slf4j;
import org.jtwig.render.RenderRequest;
import org.jtwig.value.convert.Converter;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Slf4j
public class SyncariValueComparator implements org.jtwig.value.compare.ValueComparator {

    @Override
    public int compare(final RenderRequest renderRequest, Object left, Object right) {
        Converter<BigDecimal> numberConverter = renderRequest.getEnvironment().getValueEnvironment().getNumberConverter();
        Converter.Result<BigDecimal> leftNumber = numberConverter.convert(left);
        Converter.Result<BigDecimal> rightNumber = numberConverter.convert(right);
        //We keep the number logic before date logic
        if (leftNumber.isDefined()) {
            if (rightNumber.isDefined()) {
                return leftNumber.get().compareTo(rightNumber.get());
            }
        }
        //Additional logic to compare date/datetimes
        /* START SYNCARI CUSTOMIZATION */
        ZonedDateTime leftDateTime = null;
        ZonedDateTime rightDateTime = null;
        try {
            leftDateTime = DatetimeType.VALUE.convert(left);
            rightDateTime = DatetimeType.VALUE.convert(right);
        } catch (Exception e) {
            log.error("Exception in converting Datetime values {}, {}", left, right, e);
        }

        if(leftDateTime!=null && rightDateTime!=null){
            return leftDateTime.compareTo(rightDateTime);
        }
        if(leftDateTime==null && rightDateTime!=null){
            return -1;
        }
        if(leftDateTime!=null && rightDateTime==null){
            return 1;
        }
        /* END SYNCARI CUSTOMIZATION */
        return getString(renderRequest, left).compareTo(getString(renderRequest, right));
    }

    private String getString(RenderRequest renderRequest, Object value) {
        return renderRequest.getEnvironment().getValueEnvironment().getStringConverter().convert(value);
    }
}
