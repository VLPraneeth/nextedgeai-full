package com.syncari.api.rest.controllers.data;

import org.modelmapper.ModelMapper;

public interface BaseDTO<T, V> {
    // Add any trasient or DTO level modifications in this method
    public V augment(V object);

    // Generic implementation that just copies the values from T model to V DTO.
    default V toDto(T object, Class<V> type) {
        ModelMapper modelMapper = new ModelMapper();
        V dtoObject = modelMapper.map(object, type);
        return augment(dtoObject);
    }
}
