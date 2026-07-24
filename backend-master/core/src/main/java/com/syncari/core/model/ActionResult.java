package com.syncari.core.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.Wither;

import java.util.HashMap;

@Getter
@AllArgsConstructor
@ToString
@Wither
public class ActionResult {
    private final Object result;
    private boolean status;

    private Throwable error;

    public static final Object NO_RESULTS = new HashMap<>();

    public ActionResult(boolean status, Object result) {
        this.result = result;
        this.status = status;
    }

    public ActionResult(boolean status) {
        this.result = NO_RESULTS;
        this.status = status;
    }
}
