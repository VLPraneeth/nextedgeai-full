package com.syncari.core.service.cache;

import com.syncari.core.datatype.*;
import com.syncari.core.utils.RedisValues;
import org.apache.commons.lang3.StringUtils;
import redis.clients.jedis.search.querybuilder.LongRangeValue;
import redis.clients.jedis.search.querybuilder.Value;
import redis.clients.jedis.search.querybuilder.Values;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class CacheDataTypeConverter {
    public enum Operator {
        GT {
            public Value toValue(Long v) {
                return new LongRangeValue(v, Long.MAX_VALUE).inclusiveMin(false);
            }

            public Value toValue(Double v) {
                return Values.gt(v);
            }

        }, GTE {
            public Value toValue(Long v) {
                return new LongRangeValue(v, Long.MAX_VALUE).inclusiveMin(true);
            }

            public Value toValue(Double v) {
                return Values.ge(v);
            }

        }, LT {
            public Value toValue(Long v) {
                return new LongRangeValue(Long.MIN_VALUE, v).inclusiveMax(false);
            }

            public Value toValue(Double v) {
                return Values.lt(v);
            }

        }, LTE {
            public Value toValue(Long v) {
                return new LongRangeValue(Long.MIN_VALUE, v).inclusiveMax(true);
            }

            public Value toValue(Double v) {
                return Values.le(v);
            }

        };

        public Value toValue(Long v) {
            throw new UnsupportedOperationException("Operation " + this.name() + " not supported for long type with value " + v);
        }

        public Value toValue(Double v) {
            throw new UnsupportedOperationException("Operation " + this.name() + " not supported for double type with value " + v);
        }

    }

    private static Object nullSafe(Object value, Function<Object, Object> converter) {
        if (value == null) return null;
        return converter.apply(value);
    }


    private Map<Class<?>, Function<Object, Object>> converters = Map.of(
            DateType.class, value -> nullSafe(value, v -> Date.class.cast(value).getTime()),
            BooleanType.class, value -> Boolean.class.cast(value) ? 1 : 0,
            DatetimeType.class, value -> nullSafe(value, v -> ZonedDateTime.class.cast(v).toInstant().toEpochMilli()),
            TimestampType.class, value -> nullSafe(value, v -> Instant.class.cast(v).toEpochMilli() / 1000)
    );


    private Function<Object, Object> noop = value -> value;

    public Object convertFrom(Datatype dataType, Object value) {
        if (value == null || StringUtils.isBlank(value.toString())) {
            return value;
        }
        final Object converted = dataType.convert(value);
        return converters.getOrDefault(dataType.getClass(), noop).apply(converted);
    }

    public Object convertTo(Datatype dataType, Object value) {
        if (value == null || StringUtils.isBlank(value.toString())) {
            return value;
        }
        // All the conversions from cache type to mongo type seems compatible, if not change this
        return dataType.convert(value);
    }

    public Value convertFrom(Object value) {
        if (value == null) {
            // make this into a empty check
            return null;
        } else if (value instanceof Long) {
            return new LongRangeValue((Long) value, (Long) value);
        } else if (value instanceof Integer) {
            return Values.eq((int) value);
        } else if (value instanceof Double) {
            return Values.eq(((Double) value).doubleValue());
        } else if (value instanceof List) {
            final String[] tags = (String[]) List.class.cast(value).toArray(new String[0]);
            return RedisValues.escapedTags(tags);
        } else {
            return RedisValues.escapedTags(value.toString());
        }
    }

    public Value toValue(Datatype datatype, Object value) {
        Object converted = convertFrom(datatype, value);
        return convertFrom(converted);
    }

    public Value convertByOperator(Operator operator, Datatype datatype, Object input) {
        Object value = convertFrom(datatype, input);
        if (value == null) {
            // make this into a empty check
            return null;
        } else if (value instanceof Long) {
            return operator.toValue((Long) value);
        } else if (value instanceof Integer) {
            return operator.toValue((Long) value);
        } else if (value instanceof Double) {
            return operator.toValue((Double) value);
        } else {
            throw new UnsupportedOperationException("Operation " + operator.name() + " not supported for double type with value " + value);
        }
    }

}
