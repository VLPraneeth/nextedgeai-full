package com.syncari.core.utils;

import org.apache.commons.lang3.StringUtils;
import redis.clients.jedis.search.querybuilder.Value;
import redis.clients.jedis.search.querybuilder.Values;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

public class RedisValues {

    //List of special chars used comes from
    // https://github.com/RediSearch/RediSearch/blob/master/src/toksep.h
    private static char[] SPECIAL_CHARS = "\t ,./(){}[]:;~@@#$%^&*-=+|\\'\"<>?".toCharArray();


    private static Set<Character> SPECIAL_CHAR_SET = new HashSet<>();

    static {
        for (int i = 0; i < SPECIAL_CHARS.length; i++) {
            SPECIAL_CHAR_SET.add(SPECIAL_CHARS[i]);
        }
    }

    private RedisValues() {
    }

    private static String escape(String value) {
        if (StringUtils.isEmpty(value)) {
            return value;
        }
        char[] chars = value.toCharArray();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < chars.length; i++) {
            if (SPECIAL_CHAR_SET.contains(chars[i])) {
                builder.append("\\");
            }
            builder.append(chars[i]);
        }
        return builder.toString();
    }

    public static Value escapedTags(String... tags) {
        String[] escaped = Arrays.stream(tags).map(t -> escape(t)).toArray(String[]::new);
        return Values.tags(escaped);
    }

    public static Value escapedTags(Function<String, String> transform, String... tags) {
        String[] escaped = Arrays.stream(tags).map(t -> transform.apply(escape(t))).toArray(String[]::new);
        return Values.tags(escaped);
    }
}
