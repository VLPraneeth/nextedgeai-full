package com.syncari.api.rest.controllers;

import static com.syncari.core.security.Permissions.TEST_CONNECTION;

import com.syncari.core.model.llm.LLMContext;
import com.syncari.core.model.llm.LLMResponse;
import com.syncari.core.service.llm.LLMService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/v1/nextedge-ai")
public class NextEdgeAIController {
    private static final long VERIFY_COOLDOWN_MILLIS = Duration.ofMinutes(1).toMillis();

    @Autowired
    LLMService llmService;

    private final AtomicLong nextVerificationAt = new AtomicLong(0);

    @Secured(TEST_CONNECTION)
    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verify() {
        if (!reserveVerificationWindow()) {
            return ResponseEntity.status(429).body(Map.of(
                    "success", false,
                    "error", "NextEdge AI verification is limited to once per minute"));
        }
        LLMResponse response = llmService.generate(
                "You are the private NextEdge AI provider health verifier.",
                "Reply with exactly NEXTEDGE_AI_READY and no other text.",
                new LLMContext());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("provider", "Amazon Bedrock");
        result.put("success", response.isSuccess());
        if (response.isSuccess()) {
            result.put("response", response.getResponse());
        } else {
            result.put("error", response.getErrorMessage());
        }
        return ResponseEntity.ok(result);
    }

    private boolean reserveVerificationWindow() {
        long now = System.currentTimeMillis();
        while (true) {
            long next = nextVerificationAt.get();
            if (now < next) {
                return false;
            }
            if (nextVerificationAt.compareAndSet(next, now + VERIFY_COOLDOWN_MILLIS)) {
                return true;
            }
        }
    }
}
