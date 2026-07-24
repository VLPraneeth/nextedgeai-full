package com.syncari.core.model.llm;

import com.syncari.core.model.misc.Audit;
import com.syncari.core.token.TokenHelper;
import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

@Data
@Accessors(chain = true)
public class LLMPrompt extends Audit {
    private String key;
    private LLMProvider provider;
    private String systemPrompt;
    private String userPrompt;

    private Map<String, Object> promptConfig = Map.of();

    public String getResolvedSystemPrompt(LLMContext context, TokenHelper tokenHelper) {
        return StringUtils.isBlank(systemPrompt) ? "" : tokenHelper.resolveJTwigToken(context.toMap(), systemPrompt).x;
    }

    public String getResolvedUserPrompt(LLMContext context, TokenHelper tokenHelper) {
        return StringUtils.isBlank(userPrompt) ? "" : tokenHelper.resolveJTwigToken(context.toMap(), userPrompt).x;
    }
}
