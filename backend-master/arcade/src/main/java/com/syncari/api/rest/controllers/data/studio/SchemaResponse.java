package com.syncari.api.rest.controllers.data.studio;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.syncari.utils.KeyValue;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class SchemaResponse {
    Map<String, KeyValue> meta = new HashMap<>();
	List<EntityVersions> data = new ArrayList<>();
}