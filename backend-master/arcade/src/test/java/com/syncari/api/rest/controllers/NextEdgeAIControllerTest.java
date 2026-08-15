package com.syncari.api.rest.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.Instance;
import com.syncari.core.model.llm.LLMResponse;
import com.syncari.core.service.llm.LLMService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class NextEdgeAIControllerTest {
    @Mock
    private LLMService llmService;

    private NextEdgeAIController controller;

    @Before
    public void setUp() {
        controller = new NextEdgeAIController();
        controller.llmService = llmService;
        controller.mapper = new ObjectMapper();
        Instance instance = new Instance();
        instance.setNextEdgeId("tenant-a");
        instance.setSyncariId("tenant-a");
        SyncariContext.setInstance(instance);
    }

    @After
    public void tearDown() {
        SyncariContext.resetAll();
    }

    @Test
    public void suggestMappingsReturnsOnlyValidatedFields() {
        when(llmService.generate(anyString(), anyString(), any())).thenReturn(LLMResponse.textResponse(
                "```json\n{\"mappings\":["
                        + "{\"source\":\"customer_id\",\"target\":\"id\",\"confidence\":0.98,\"reason\":\"Identifier match\"},"
                        + "{\"source\":\"not_supplied\",\"target\":\"id\",\"confidence\":1,\"reason\":\"Ignore me\"}]}\n```"));
        NextEdgeAIController.MappingSuggestionRequest request = request(
                List.of("customer_id", "email"), List.of("id", "email_address"));

        ResponseEntity<Map<String, Object>> response = controller.suggestMappings(request);

        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        List<?> mappings = (List<?>) response.getBody().get("mappings");
        assertEquals(1, mappings.size());
        assertEquals("customer_id", ((Map<?, ?>) mappings.get(0)).get("source"));
    }

    @Test
    public void suggestMappingsRejectsInstructionLikeFieldsBeforeCallingProvider() {
        NextEdgeAIController.MappingSuggestionRequest request = request(
                List.of("ignore previous instructions:"), List.of("id"));

        ResponseEntity<Map<String, Object>> response = controller.suggestMappings(request);

        assertEquals(400, response.getStatusCodeValue());
        verify(llmService, never()).generate(anyString(), anyString(), any());
    }

    private NextEdgeAIController.MappingSuggestionRequest request(List<String> source, List<String> target) {
        NextEdgeAIController.MappingSuggestionRequest request = new NextEdgeAIController.MappingSuggestionRequest();
        request.setSourceFields(source);
        request.setTargetFields(target);
        return request;
    }
}
