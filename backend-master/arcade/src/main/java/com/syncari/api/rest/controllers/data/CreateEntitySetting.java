package com.syncari.api.rest.controllers.data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class CreateEntitySetting implements Serializable {
	private List<EntityMapping> entityMapping = new ArrayList<>();
}
