package com.syncari.connector.zoho;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.syncari.connector.EntityData;
import com.syncari.connector.data.Stats;
import com.syncari.connector.data.WatermarkInfo;
import com.syncari.connector.data.iterator.AbstractEntityDataBatchIterator;
import com.syncari.connector.data.iterator.EntityDataBatchIterator;
import com.syncari.connector.data.iterator.Offset;
import com.syncari.connector.data.iterator.Offset.OffsetType;

import org.jooq.lambda.function.Function2;

public class ZohoIterator extends AbstractEntityDataBatchIterator implements EntityDataBatchIterator {

    // SYN-4493 Customers can have the minimum tier for pulling data from zoho. Example. 36000/hour
    // The framework has a default of 2K/sync cycle. Here we reduce it to 400 to make progress for each sync cycle.
    // 400 in order to accomodate at least 2 batches (800 records) including schema calls and getRecords calls.
    public static final int MAX_RECORDS_PER_ENTITY_PER_SYNC_CYCLE = 400;

	List<EntityData> data = new ArrayList<>();
    WatermarkInfo baseWatermark;
    protected boolean hasMore = true;    
    Function2<WatermarkInfo, Integer, ZohoEntityPage> generator;
    Integer nextPageNumber;
    long lastWatermark = -1l;
    private Stats stats = new Stats();
    final int pageSize;
    
    public ZohoIterator(WatermarkInfo baseWatermark, 
    		Function2<WatermarkInfo, Integer, ZohoEntityPage> generator,
    		List<EntityData> data, int pageSize) {
    	this.baseWatermark = baseWatermark;
        this.generator = generator;
        this.data = data;
        this.pageSize = pageSize;
    }

    @Override
    public int getMaxRecordsPerEntitySyncCycle() {
        return MAX_RECORDS_PER_ENTITY_PER_SYNC_CYCLE;
    }
    
	@Override
	public boolean hasNext() {
    	if(!data.isEmpty()){
    		return true;
		}
		if(!hasMore || nextPageNumber == null){
            return false;
        }
		
		var results = generator.apply(baseWatermark, nextPageNumber);
		data = results.getData();
		hasMore = hasMore(results);
		nextPageNumber = results.getNextPage();
		return data.size() > 0;
	}

	@Override
	public List<EntityData> next() {
		var temp = data;
		if(!data.isEmpty()) {
			EntityData entityData = data.get(data.size() - 1);
			lastWatermark = getWatermarkValue(entityData);
		}
		
		if(baseWatermark.getLimit() > 0){
            if(temp.size() >= baseWatermark.getLimit()) {
                temp = temp.stream().limit(baseWatermark.getLimit()).collect(Collectors.toList());
                nextPageNumber = null;
            } else {
                baseWatermark.setLimit(baseWatermark.getLimit() - temp.size());
            }
        }
		
		data = new ArrayList<>();
		
		return temp;
	}
	
	public void setNextPageNumber(Integer nextPageNumber) {
		this.nextPageNumber = nextPageNumber;
	}

	protected long getWatermarkValue(EntityData entityData) {
        return entityData.getLastModified();
    }

	@Override
	public long getLastWatermark() {
		return lastWatermark;
	}

	@Override
	public Stats getStats() {
		return stats;
	}
	
	protected boolean hasMore(ZohoEntityPage results) {
        return results.isHasMore();
    }

	@Override
    public long getLastOffset() {
        return (!hasMore || nextPageNumber == null) ? 0 : nextPageNumber.longValue();
    }

    @Override
    public Offset getOffsetInfo() {
        return new Offset(OffsetType.PAGE_NUMBER, pageSize);
    }
}