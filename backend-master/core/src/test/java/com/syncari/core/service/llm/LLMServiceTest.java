package com.syncari.core.service.llm;


import com.syncari.core.TestConfig;
import com.syncari.core.model.llm.*;
import com.syncari.core.repositories.customer.llm.LLMPromptRepo;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(SpringRunner.class)
@SpringBootTest
@TestPropertySource(locations = "classpath:test_application.properties")
@ContextConfiguration(classes = TestConfig.class, loader = AnnotationConfigContextLoader.class)
@Ignore
public class LLMServiceTest {
    @MockBean
    LLMPromptRepo promptRepo;
    @Autowired
    LLMService llmService;

    @Test
    public void testBasicPrompts() {
        final LLMPrompt prompt = new LLMPrompt();
        prompt.setProvider(LLMProvider.OPENAI);
        prompt.setPromptConfig(Map.of("model", "gpt-4", "temperature", 0));
        prompt.setUserPrompt("Whats the capital of {{country}}? Respond only with the capital and nothing else");
        Mockito.when(promptRepo.findByKeyAndProvider("test_key", "OPENAI")).thenReturn(Optional.of(prompt));
        final LLMResponse ask = llmService.ask("test_key", new LLMContext().add("country", "United States"));
        assertTrue(ask.isSuccess());
        assertEquals("Washington D.C.", ask.getResponse());
        assertEquals(ResponseType.MARKDOWN, ask.getType());
    }
}