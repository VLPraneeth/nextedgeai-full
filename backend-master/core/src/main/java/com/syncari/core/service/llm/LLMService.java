package com.syncari.core.service.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.core.model.llm.*;
import com.syncari.core.repositories.customer.llm.LLMPromptRepo;
import com.syncari.core.service.secrets.SecretManager;
import com.syncari.core.token.TokenHelper;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;

import javax.annotation.PreDestroy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Component
public class LLMService {
    private static final String OPENAI_API_KEY = "openai_apikey";
    private static final String OPENAI_COMPLETION_ENDPOINT = "https://api.openai.com/v1/chat/completions";

    @Autowired
    protected TokenHelper tokenHelper;
    @Autowired
    RestTemplate llmRestTemplate;
    @Autowired
    LLMPromptRepo promptRepo;
    @Autowired
    SecretManager secretManager;
    @Autowired
    ObjectMapper mapper;

    @Value("${NEXTEDGE_LLM_PROVIDER:bedrock}")
    String configuredProvider;
    @Value("${NEXTEDGE_BEDROCK_MODEL_ID:apac.amazon.nova-lite-v1:0}")
    String bedrockModelId;
    @Value("${NEXTEDGE_LLM_KILL_SWITCH:/nextedge-ai/llm/enabled}")
    String llmKillSwitch;
    @Value("${AWS_REGION:ap-south-1}")
    String awsRegion;

    private volatile BedrockRuntimeClient bedrockClient;
    private volatile SsmClient ssmClient;

    public LLMResponse ask(String promptKey, LLMContext context) {
        LLMProvider provider = defaultProvider();
        Optional<LLMPrompt> optionalPrompt = promptRepo.findByKeyAndProvider(promptKey, provider.name());
        if (optionalPrompt.isEmpty() && provider != LLMProvider.OPENAI) {
            optionalPrompt = promptRepo.findByKeyAndProvider(promptKey, LLMProvider.OPENAI.name());
            optionalPrompt.ifPresent(prompt -> prompt.setProvider(provider));
        }
        return optionalPrompt.map(prompt -> evaluate(prompt, context)).orElse(LLMResponse.unknownPrompt(promptKey));
    }

    public LLMResponse generate(String system, String user, LLMContext context) {
        LLMPrompt prompt = new LLMPrompt()
                .setSystemPrompt(system)
                .setUserPrompt(user)
                .setProvider(defaultProvider())
                .setPromptConfig(Map.of("model", bedrockModelId, "max_tokens", 1024, "temperature", 0, "top_p", 0.01));
        return evaluate(prompt, context);
    }

    private LLMProvider defaultProvider() {
        try {
            return LLMProvider.valueOf(configuredProvider.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            log.warn("Unknown NextEdge AI LLM provider '{}'; using Bedrock", configuredProvider);
            return LLMProvider.BEDROCK;
        }
    }

    private LLMResponse evaluate(LLMPrompt prompt, LLMContext context) {
        switch (prompt.getProvider()) {
            case BEDROCK:
                return askBedrock(prompt, context);
            case OPENAI:
                return askOpenAI(prompt, context);
            default:
                return LLMResponse.error("The configured NextEdge AI provider is not available");
        }
    }

    @SneakyThrows
    private LLMResponse askBedrock(LLMPrompt prompt, LLMContext context) {
        if (!isLlmEnabled()) {
            return LLMResponse.error("NextEdge AI is temporarily disabled by the AWS cost guardrail");
        }

        String system = prompt.getResolvedSystemPrompt(context, tokenHelper);
        String user = prompt.getResolvedUserPrompt(context, tokenHelper);
        Map<String, Object> payload = bedrockPayload(prompt, system, user);

        try {
            InvokeModelRequest request = InvokeModelRequest.builder()
                    .modelId(bedrockModelId)
                    .contentType("application/json")
                    .accept("application/json")
                    .body(SdkBytes.fromUtf8String(mapper.writeValueAsString(payload)))
                    .build();
            InvokeModelResponse result = bedrock().invokeModel(request);
            JsonNode response = mapper.readTree(result.body().asUtf8String());
            String text = extractBedrockText(response);
            if (StringUtils.isBlank(text)) {
                return LLMResponse.error("NextEdge AI received an invalid model response");
            }
            log.info("NextEdge AI completed a Bedrock request with model {} ({} response characters)", bedrockModelId, text.length());
            return LLMResponse.response(text, ResponseType.MARKDOWN);
        } catch (Exception error) {
            log.error("NextEdge AI Bedrock request failed: {}", error.getMessage());
            return LLMResponse.error("NextEdge AI could not complete the request");
        }
    }

    Map<String, Object> bedrockPayload(LLMPrompt prompt, String system, String user) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (bedrockModelId.contains("amazon.nova")) {
            Map<String, Object> inferenceConfig = new LinkedHashMap<>();
            inferenceConfig.put("maxTokens", numericConfig(prompt, "max_tokens", 1024));
            inferenceConfig.put("temperature", numericConfig(prompt, "temperature", 0));
            inferenceConfig.put("topP", numericConfig(prompt, "top_p", 0.01));
            payload.put("inferenceConfig", inferenceConfig);
            if (!StringUtils.isBlank(system)) {
                payload.put("system", List.of(Map.of("text", system)));
            }
            payload.put("messages", List.of(Map.of(
                    "role", "user",
                    "content", List.of(Map.of("text", user)))));
            return payload;
        }

        payload.put("anthropic_version", "bedrock-2023-05-31");
        payload.put("max_tokens", numericConfig(prompt, "max_tokens", 1024));
        payload.put("temperature", numericConfig(prompt, "temperature", 0));
        payload.put("top_p", numericConfig(prompt, "top_p", 0.01));
        if (!StringUtils.isBlank(system)) {
            payload.put("system", system);
        }
        payload.put("messages", List.of(Map.of("role", "user", "content", user)));
        return payload;
    }

    String extractBedrockText(JsonNode response) {
        JsonNode content = bedrockModelId.contains("amazon.nova")
                ? response.path("output").path("message").path("content")
                : response.path("content");
        if (!content.isArray() || content.size() == 0 || content.get(0).path("text").isMissingNode()) {
            return null;
        }
        return content.get(0).path("text").asText();
    }

    private Number numericConfig(LLMPrompt prompt, String key, Number fallback) {
        Object value = prompt.getPromptConfig().get(key);
        return value instanceof Number ? (Number) value : fallback;
    }

    private boolean isLlmEnabled() {
        try {
            return Boolean.parseBoolean(ssm().getParameter(GetParameterRequest.builder().name(llmKillSwitch).build()).parameter().value());
        } catch (Exception error) {
            log.error("Unable to read the NextEdge AI cost guardrail: {}", error.getMessage());
            return false;
        }
    }

    private BedrockRuntimeClient bedrock() {
        if (bedrockClient == null) {
            synchronized (this) {
                if (bedrockClient == null) {
                    bedrockClient = BedrockRuntimeClient.builder().region(Region.of(awsRegion)).build();
                }
            }
        }
        return bedrockClient;
    }

    private SsmClient ssm() {
        if (ssmClient == null) {
            synchronized (this) {
                if (ssmClient == null) {
                    ssmClient = SsmClient.builder().region(Region.of(awsRegion)).build();
                }
            }
        }
        return ssmClient;
    }

    @PreDestroy
    public void closeAwsClients() {
        if (bedrockClient != null) {
            bedrockClient.close();
        }
        if (ssmClient != null) {
            ssmClient.close();
        }
    }

    @SneakyThrows
    private LLMResponse askOpenAI(LLMPrompt prompt, LLMContext context) {
        Map<String, Object> payload = new HashMap<>(prompt.getPromptConfig());
        List<Map<String, String>> messages = new ArrayList<>();
        String resolvedSystemPrompt = prompt.getResolvedSystemPrompt(context, tokenHelper);
        String resolvedUserPrompt = prompt.getResolvedUserPrompt(context, tokenHelper);
        if (!StringUtils.isBlank(resolvedSystemPrompt)) {
            messages.add(Map.of("role", "system", "content", resolvedSystemPrompt));
        }
        messages.add(Map.of("role", "user", "content", resolvedUserPrompt));
        payload.put("messages", messages);
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + secretManager.getSecret(OPENAI_API_KEY));
        headers.set(HttpHeaders.USER_AGENT, "nextedge.ai/llm-agent/1.0");
        headers.set(HttpHeaders.CONTENT_TYPE, "application/json");
        headers.set(HttpHeaders.ACCEPT, "*/*");
        HttpEntity<String> httpEntity = new HttpEntity<>(mapper.writeValueAsString(payload), headers);
        try {
            ResponseEntity<String> result = llmRestTemplate.exchange(URI.create(OPENAI_COMPLETION_ENDPOINT), HttpMethod.POST, httpEntity, String.class);
            if (result.getStatusCode().isError()) {
                return LLMResponse.error("NextEdge AI provider returned an error");
            }
            return LLMResponse.response(extractResponse(result), ResponseType.MARKDOWN);
        } catch (HttpClientErrorException error) {
            log.error("NextEdge AI OpenAI request failed with status {}", error.getStatusCode());
            return LLMResponse.error("NextEdge AI could not complete the request");
        }
    }

    @SneakyThrows
    private String extractResponse(ResponseEntity<String> result) {
        OpenAIResponse response = mapper.readValue(result.getBody(), OpenAIResponse.class);
        return Optional.ofNullable(response.getContent()).map(Object::toString).orElse("");
    }
}

@Data
class OpenAIResponse {
    private String id;
    private String object;
    private long created;
    private String model;
    private List<Choice> choices = List.of();
    private Usage usage;
    String system_fingerprint;

    public String getContent() {
        if (choices.isEmpty() || choices.get(0).message == null) {
            return null;
        }
        return choices.get(0).message.content;
    }
}

@Data
class Choice {
    int index;
    Message message;
    String logprobs;
    String finish_reason;
}

@Data
class Message {
    String role;
    String content;
}

@Data
class Usage {
    int prompt_tokens;
    int completion_tokens;
    int total_tokens;
}
