package com.syncari.core.mapper;

import com.syncari.core.service.mapper.AutoFieldMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class AutoFieldMapperFactory {

    @Autowired
    @Qualifier("searchBasedMapper")
    private AutoFieldMapper luceneMapper;

    @Autowired
    @Qualifier("llmMapper")
    private AutoFieldMapper llmMapper;

    public AutoFieldMapper getMapper(MapperType mapperType) {
        switch (mapperType) {
            case SYNC_AI:
                return llmMapper;
            case BASIC_SEARCH:
            default:
                return luceneMapper;
        }
    }
}
