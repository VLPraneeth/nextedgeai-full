package com.syncari.api.rest.controllers.data.studio;

import java.util.List;

import com.syncari.core.model.pagination.PageInfo;

import com.syncari.restutils.data.DataQueryMetadata;
import com.syncari.restutils.data.EntityRecord;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DataQueryResponse {
    List<EntityRecord> records;
    PageInfo pageInfo;
    DataQueryMetadata metadata;
}
