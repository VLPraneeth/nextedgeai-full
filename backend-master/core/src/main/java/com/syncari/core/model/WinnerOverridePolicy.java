package com.syncari.core.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import static com.syncari.utils.I18n.*;

@Getter
public enum WinnerOverridePolicy {
    ALWAYS (i18n("winner_always_override")),
    NEVER (i18n("winner_never_override")),
    WHEN_BLANK(i18n("winner_override_when_blank"));

    public String label;
    WinnerOverridePolicy(String label) {
        this.label = label;
    }

    @JsonCreator
    public static WinnerOverridePolicy fromJson(@JsonProperty("value") String value) {
        return WinnerOverridePolicy.valueOf(value);
    }

}
