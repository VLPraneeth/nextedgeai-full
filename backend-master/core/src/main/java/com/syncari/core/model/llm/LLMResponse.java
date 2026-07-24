package com.syncari.core.model.llm;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;


@Data
@AllArgsConstructor
@Accessors(chain = true)
public class LLMResponse implements Serializable {
    public static final LLMResponse BLANK = new LLMResponse("", ResponseType.TEXT, true, "");
    public static final LLMResponse UNKNOWN_PROMPT = new LLMResponse("", ResponseType.TEXT, false, "");
    private final String response;
    private final ResponseType type;
    private final boolean success;
    private final String errorMessage;

    public static LLMResponse textResponse(String response) {
        return new LLMResponse(response, ResponseType.TEXT, true, null);
    }

    public static LLMResponse response(String response, ResponseType type) {
        return new LLMResponse(response, type, true, null);
    }

    public static LLMResponse error(String errorMessage) {
        return new LLMResponse(null, ResponseType.TEXT, false, errorMessage);
    }

    public static LLMResponse error(String errorMessage, ResponseType type) {
        return new LLMResponse(null, type, false, errorMessage);
    }

    public static LLMResponse unknownPrompt(String promptKey) {
        return new LLMResponse(null, ResponseType.TEXT, false, String.format("Cannot find promp '%s' ", promptKey));
    }
}
