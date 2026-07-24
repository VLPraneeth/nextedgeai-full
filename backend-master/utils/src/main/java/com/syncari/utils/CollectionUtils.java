package com.syncari.utils;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public interface CollectionUtils {
    static <R,T> List<R> map(List<T> input, Function<T, R> mapper){
        return input.stream().map(mapper).collect(Collectors.toList());
    }

    static <R,T> R[] mapToArray(List<T> input, Function<T, R> mapper){
        List<R> collected = input.stream().map(mapper).collect(Collectors.toList());
        return (R[]) collected.toArray();
    }

    static List<String> toList(Object values) {
        if(values==null)return List.of();
        if(List.class.isAssignableFrom(values.getClass())){
            return List.class.cast(values);
        }
        return  Arrays.asList(values.toString().split(","));
    }


}
