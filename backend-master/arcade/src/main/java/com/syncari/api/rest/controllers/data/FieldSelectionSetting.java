package com.syncari.api.rest.controllers.data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class FieldSelectionSetting implements Serializable {
	private List<FieldMapping> fieldMapping = new ArrayList<>();
}
