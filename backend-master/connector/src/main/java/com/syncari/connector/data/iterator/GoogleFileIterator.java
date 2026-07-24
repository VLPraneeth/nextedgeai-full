package com.syncari.connector.data.iterator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.syncari.connector.EntityData;
import com.syncari.connector.data.Stats;
import com.syncari.connector.data.WatermarkInfo;

public class GoogleFileIterator implements EntityDataBatchIterator {
    WatermarkInfo baseWatermark;
	List<GoogleSheetsIterator> files = new ArrayList<>();
	int fileOffset = 0;
	private Stats stats = new Stats();

	public GoogleFileIterator(WatermarkInfo baseWatermark, List<GoogleSheetsIterator> files) {
		this.baseWatermark = baseWatermark;
		this.files = files;
	}

	@Override
	public boolean hasNext() {
	    if(files.isEmpty() || fileOffset > files.size()-1) return false;
	    GoogleSheetsIterator currentIterator = files.get(fileOffset);
		if(currentIterator.hasNext()) return true;
		fileOffset = fileOffset + 1;
		if(fileOffset > files.size()-1) return false;
		return files.get(fileOffset).hasNext();
	}

	@Override
	public List<EntityData> next() {
	    return files.get(fileOffset).next();
	}

	@Override
	/**
	 * Last watermark is updated ONLY after consuming next() record
	 */
	public long getLastWatermark() {
		return Instant.now().toEpochMilli();
	}

	@Override
	public Stats getStats() {
		return stats;
	}

}
