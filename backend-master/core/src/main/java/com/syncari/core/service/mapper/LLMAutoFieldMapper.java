package com.syncari.core.service.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.llm.LLMContext;
import com.syncari.core.model.llm.LLMResponse;
import com.syncari.core.service.llm.LLMService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component("llmMapper")
@Slf4j
public class LLMAutoFieldMapper implements AutoFieldMapper {
    @Autowired
    LLMService llmService;
    @Autowired
    ObjectMapper objectMapper;
    private static final String systemPrompt = "SYSTEM:You are an automatic field mapper for a given list of source fields and destinations. You will only print JSON and nothing else\n" +
            "INPUT:\n" +
            "sources - firstName:First Name,lastName:Last Name,zipCode:Zip\n" +
            "destinations - fName:First,lName:Last,postCode:Postal Code\n" +
            "OUTPUT:  {\"firstName\":\"fName\",\"lastName\":\"lName\",\"zipCode\",\"postalCode\"}";
    private static final String userPrompt =
            "User:\n" +
            "INPUT:\n" +
            "sources - {{sources}}\n" +
            "destinations - {{destinations}}";

    private static String formatAttrib(AttributeDefinition first) {
        return String.format("%s:%s", first.getApiName(), first.getDisplayName());
    }

    @Override
    @SneakyThrows
    public Map<AttributeDefinition, AttributeDefinition> automap(List<AttributeDefinition> src, List<AttributeDefinition> dest) {
        Map<String, AttributeDefinition> srcMap = src.stream().collect(Collectors.toMap(
                AttributeDefinition::getApiName, f -> f
        ));
        Map<String, AttributeDefinition> destMap = dest.stream().collect(Collectors.toMap(
                AttributeDefinition::getApiName, f -> f
        ));
        String sources = src.stream().map(LLMAutoFieldMapper::formatAttrib)
                .reduce((f, s) -> f + "," + s).toString();
        String destinations = dest.stream().map(LLMAutoFieldMapper::formatAttrib)
                .reduce((f, s) -> f + "," + s).toString();
        final LLMResponse response = llmService.generate(systemPrompt, userPrompt,
                new LLMContext("sources", sources, "destinations", destinations));
        final Map rawMappings = objectMapper.readValue(response.getResponse(), Map.class);
        Map<AttributeDefinition, AttributeDefinition> mappings = new HashMap<>();
        rawMappings.forEach((s, d) -> {
            final String mappedSrc = s.toString().toLowerCase();
            final String mappedDest = d.toString().toLowerCase();
            if (srcMap.containsKey(mappedSrc) && destMap.containsKey(mappedDest)) {
                mappings.put(srcMap.get(mappedSrc), destMap.get(mappedDest));
            } else {
                log.error("Either source {} or dest {} is incorrect from LLM mapping. Ignoring entry", s, d);
            }
        });
        return mappings;
    }
}
