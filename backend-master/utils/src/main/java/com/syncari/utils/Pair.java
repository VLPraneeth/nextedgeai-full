package com.syncari.utils;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@ToString
@EqualsAndHashCode
@Getter
public class Pair<X, Y> {

    public final X x;
    public final Y y;

    protected Pair(X x, Y y) {
        this.x = x;
        this.y = y;
    }

    public static <X, Y> Pair<X, Y> of(X a, Y b) {
        return new Pair(a, b);
    }
}
