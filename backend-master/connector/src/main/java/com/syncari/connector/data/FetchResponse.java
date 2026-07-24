package com.syncari.connector.data;

import java.util.ArrayList;
import java.util.List;

import com.syncari.connector.data.iterator.EntityDataBatchIterator;

import lombok.Data;

@Data
public class FetchResponse {
	WatermarkInfo watermark;
	EntityDataBatchIterator iterator;
	List<BatchJob> batchJobs = new ArrayList<>();
	private Long timeTaken = 0l;

	public FetchResponse(WatermarkInfo watermark,EntityDataBatchIterator iterator){
		this.watermark = watermark;
		this.iterator = iterator;
	}
}
