package com.syncari.api.rest.controllers.data;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Dependency {
	String name;
	String id;
	String path;
}
