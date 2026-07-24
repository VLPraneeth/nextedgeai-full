package com.syncari.core.repositories.customer.llm;

import com.syncari.core.model.llm.LLMPrompt;
import com.syncari.core.repositories.SyncariRepo;

import java.util.Optional;

public interface LLMPromptRepo extends SyncariRepo<LLMPrompt> {
    Optional<LLMPrompt> findByKeyAndProvider(String key, String provider);
}
