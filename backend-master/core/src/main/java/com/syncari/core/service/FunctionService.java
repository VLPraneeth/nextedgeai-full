package com.syncari.core.service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.syncari.core.SyncariContext;
import com.syncari.core.functions.FunctionsSeed;
import com.syncari.core.model.FunctionDefinition;
import com.syncari.core.model.util.Scope;
import com.syncari.core.repositories.customer.FunctionDefinitionRepo;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class FunctionService {
    @Autowired
    FunctionDefinitionRepo functionDefinitionRepo;
    LoadingCache<String, List<FunctionDefinition>> metadatCache = CacheBuilder.newBuilder().maximumSize(100000)
            .build(new CacheLoader<>() {
                @Override
                public List<FunctionDefinition> load(String syncariId) {
                    //discard db seeds which don't have an in-memory seed
					return functionDefinitionRepo.findAll().stream().map(f -> populated(f))
							.filter(f -> f.getDisplayName() != null)
							.sorted(Comparator.comparing(FunctionDefinition::getDisplayName))
							.collect(Collectors.toList());
                }
            });
    
    public Optional<FunctionDefinition> findByNameAndScope(String name, Scope scope) {
        return metadatCache.getUnchecked(SyncariContext.getSyncariId()).stream()
                .filter(f -> f.getName().equalsIgnoreCase(name) && f.getScope().equals(scope)).findFirst();
    }

    public List<FunctionDefinition> findByScope(Scope scope) {
        return metadatCache.getUnchecked(SyncariContext.getSyncariId()).stream()
                .filter(f -> f.getScope().equals(scope)).collect(Collectors.toList());
    }

    public Optional<FunctionDefinition> findById(String id) {
        return metadatCache.getUnchecked(SyncariContext.getSyncariId()).stream()
                .filter(f -> f.getId().equalsIgnoreCase(id)).findFirst();
    }
    
    private FunctionDefinition populated(FunctionDefinition findByName) {
        return FunctionsSeed.populateFunction(findByName);
    }
}