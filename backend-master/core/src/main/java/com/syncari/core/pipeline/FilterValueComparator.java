package com.syncari.core.pipeline;

import com.syncari.core.datatype.*;
import com.syncari.utils.Pair;
import lombok.extern.slf4j.Slf4j;
import org.jtwig.value.Undefined;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Date;

@Slf4j
public class FilterValueComparator {

    public int compare(Object left, Object right) {
        if(left instanceof Pair) {
            Pair<Object, Datatype> valueDataType = (Pair<Object, Datatype>) left;
            Datatype datatype = valueDataType.getY();
            Object value = valueDataType.getX();
            if(datatype instanceof DoubleType || datatype instanceof IntegerType) {
                log.debug("Comparing number datatype");
                Integer integerResult =  compareInteger(convertNumber(value), convertNumber(right));
                if (integerResult != null) return integerResult;
            } else if (datatype instanceof DatetimeType || datatype instanceof DateType || datatype instanceof TimestampType) {
                log.debug("Comparing date datatype");
                Integer dateTimeResult = compareDatetime(value, right);
                if (dateTimeResult != null) return dateTimeResult;
            }
            log.debug("Comparing string datatype");
            return convertString(value).compareTo(convertString(right));
        } else {
            return nullSafeCompare(left, right);
        }
    }

    private int nullSafeCompare(Object a, Object b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;

        if (a instanceof String) {
            return a.toString().compareTo(b.toString());
        }
        if (a instanceof Long || a instanceof Integer) {
            final Long convertedRightValue = IntegerType.VALUE.convert(b);
            return convertedRightValue == null ? 1 : Long.compare(Number.class.cast(a).longValue(), convertedRightValue);
        }
        if (a instanceof Double) {
            final Double convertedRightValue = DoubleType.VALUE.convert(b);
            return convertedRightValue == null ? 1 : ((Double) a).compareTo(convertedRightValue);
        }
        if (a instanceof ZonedDateTime && b instanceof Instant) {
        	final Instant convertedLeftValue = TimestampType.VALUE.convert(a);
        	return convertedLeftValue.compareTo((Instant) b);
        }
        if (a instanceof ZonedDateTime && b instanceof Date) {
        	final Date convertedLeftValue = DateType.VALUE.convert(a);
        	return convertedLeftValue.compareTo((Date) b);
        }
        if (a instanceof ZonedDateTime) {
            ZonedDateTime convertedRightValue = DatetimeType.VALUE.convert(b);
            if(convertedRightValue != null) {
            	convertedRightValue = convertedRightValue.withZoneSameInstant(convertedRightValue.getZone().normalized());
            }
            ZonedDateTime leftValue = (ZonedDateTime) a;
            leftValue = leftValue.withZoneSameInstant(leftValue.getZone().normalized());
            return convertedRightValue == null ? 1 : leftValue.compareTo(convertedRightValue);
        }
        if (a instanceof Date) {
            final Date convertedRightValue = DateType.VALUE.convert(b);
            return convertedRightValue == null ? 1 : ((Date) a).compareTo(convertedRightValue);
        }
        if (a instanceof Boolean) {
            final Boolean convertedRightValue = BooleanType.VALUE.convert(b);
            return convertedRightValue == null ? 1 : ((Boolean) a).compareTo(convertedRightValue);
        }
        if (a instanceof BigDecimal) {
            final Double convertedRightValue = DoubleType.VALUE.convert(b);
            return convertedRightValue == null ? 1 : ((BigDecimal) a).compareTo(BigDecimal.valueOf(convertedRightValue));
        }
        if (a instanceof Instant) {
            final Instant convertedRightValue = TimestampType.VALUE.convert(b);
            return convertedRightValue == null ? 1 : ((Instant) a).compareTo(convertedRightValue);
        }

        return a.toString().compareTo(b.toString());
    }

    private Integer compareDatetime(Object left, Object right) {
        ZonedDateTime leftDateTime = null;
        ZonedDateTime rightDateTime = null;
        try {
            leftDateTime = DatetimeType.VALUE.convert(left);
            rightDateTime = DatetimeType.VALUE.convert(right);
        } catch (Exception e) {
            log.error("Exception in converting Datetime values {}, {}", left, right, e);
        }

        if (leftDateTime != null && rightDateTime != null) {
            return leftDateTime.compareTo(rightDateTime);
        }
        if (leftDateTime == null && rightDateTime != null) {
            return -1;
        }
        if (leftDateTime != null && rightDateTime == null) {
            return 1;
        }
        return null;
    }

    private Integer compareInteger(BigDecimal left, BigDecimal right) {
        var leftDecimal = left;
        var rightDecimal = right;

        if (leftDecimal != null && rightDecimal != null) {
            return leftDecimal.compareTo(rightDecimal);
        }
        return null;
    }

    private String convertString(Object object) {
        var value = StringType.VALUE.convert(object);
        return value != null ? value : "";
    }

    // From Jtwig BigDecimal Converter
    private BigDecimal convertNumber(Object object) {
        if (object == null) {
            return BigDecimal.ZERO;
        } else if (object == Undefined.UNDEFINED) {
            return BigDecimal.ZERO;
        } else if (object instanceof BigDecimal) {
            return (BigDecimal)object;
        } else if (object instanceof Number) {
            return new BigDecimal(((Number)object).doubleValue());
        } else if (object instanceof Boolean) {
            return (Boolean)object ? BigDecimal.ONE : BigDecimal.ZERO;
        } else {
            try {
                if (object instanceof String) {
                    return new BigDecimal((String)object);
                }
            } catch (NumberFormatException var3) {
            }

            return null;
        }
    }

}
