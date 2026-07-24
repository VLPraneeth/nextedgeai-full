package com.syncari.core.model.misc;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PageRequest {
	int pageNumber;
	int limit;
}
