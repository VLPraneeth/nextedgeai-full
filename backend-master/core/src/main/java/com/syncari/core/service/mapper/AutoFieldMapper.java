package com.syncari.core.service.mapper;

import com.syncari.core.model.AttributeDefinition;
import lombok.SneakyThrows;

import java.util.List;
import java.util.Map;

public interface AutoFieldMapper {
    @SneakyThrows
    Map<AttributeDefinition, AttributeDefinition> automap(List<AttributeDefinition> src, List<AttributeDefinition> dest);
}
