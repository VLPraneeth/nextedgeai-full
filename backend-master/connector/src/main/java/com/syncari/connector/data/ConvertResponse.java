package com.syncari.connector.data;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ConvertResponse {
	private List<ConvertResult> data = new ArrayList<ConvertResult>();
}
