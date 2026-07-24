package com.syncari.core.service.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.core.model.llm.LLMPrompt;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class LLMServiceBedrockTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private LLMService service;

    @Before
    public void setUp() {
        service = new LLMService();
        service.bedrockModelId = "apac.amazon.nova-lite-v1:0";
    }

    @Test
    public void buildsNovaPayload() {
        LLMPrompt prompt = new LLMPrompt().setPromptConfig(Map.of(
                "max_tokens", 256,
                "temperature", 0.2,
                "top_p", 0.8));

        Map<String, Object> payload = service.bedrockPayload(prompt, "system prompt", "user prompt");

        assertEquals(Map.of("maxTokens", 256, "temperature", 0.2, "topP", 0.8), payload.get("inferenceConfig"));
        assertEquals(List.of(Map.of("text", "system prompt")), payload.get("system"));
        assertEquals(List.of(Map.of(
                "role", "user",
                "content", List.of(Map.of("text", "user prompt")))), payload.get("messages"));
    }

    @Test
    public void extractsNovaText() throws Exception {
        assertEquals("NEXTEDGE_AI_READY", service.extractBedrockText(mapper.readTree(
                "{\"output\":{\"message\":{\"content\":[{\"text\":\"NEXTEDGE_AI_READY\"}]}}}")));
    }

    @Test
    public void rejectsMalformedNovaResponse() throws Exception {
        assertNull(service.extractBedrockText(mapper.readTree("{\"output\":{}}")));
    }
}
