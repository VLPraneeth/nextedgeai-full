package com.syncari.core.model.llm;

public enum LLMProvider {
    OPENAI {
        @Override
        public String endpoint() {
            return "https://api.openai.com/v1/chat/completions";
        }
    }, BEDROCK, GEMINI, CLAUDE, GROQ_LLAMA3;

    public String endpoint() {
        return null;
    }
}
