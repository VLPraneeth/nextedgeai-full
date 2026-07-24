package com.syncari.api.rest.controllers.data.studio;

import java.util.List;

import com.syncari.core.model.pagination.PageInfo;

import lombok.Data;

@Data
public class DataFilterResponse {
    List<DataFilterDTO> filters;
    PageInfo pageInfo;
}
