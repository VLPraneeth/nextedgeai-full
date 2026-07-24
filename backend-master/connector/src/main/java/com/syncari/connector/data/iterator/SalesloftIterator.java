package com.syncari.connector.data.iterator;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.jooq.lambda.function.Function2;

import com.syncari.connector.EntityData;
import com.syncari.connector.SalesloftEntityPage;
import com.syncari.connector.data.Stats;
import com.syncari.connector.data.WatermarkInfo;


public class SalesloftIterator implements EntityDataBatchIterator {

	List<EntityData> data = new ArrayList<>();
    int pageSize;
    WatermarkInfo baseWatermark;
    protected boolean hasMore = true;    
    Function2<WatermarkInfo, Integer, SalesloftEntityPage> generator;
    Integer nextPageNumber;
    long lastWatermark = -1l;
    private Stats stats = new Stats();
    
    public SalesloftIterator(WatermarkInfo baseWatermark, 
    		Function2<WatermarkInfo, Integer, SalesloftEntityPage> generator,
    		List<EntityData> data, int pageSize) {
    	this.baseWatermark = baseWatermark;
        this.generator = generator;
        this.data = data;
        this.pageSize = pageSize;
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
	
	protected boolean hasMore(SalesloftEntityPage results) {
        return results.isHasMore();
    }

	@Override
    public long getLastOffset() {
        return (!hasMore || nextPageNumber == null) ? 0 : nextPageNumber.longValue();
    }

    @Override
    public Offset getOffsetInfo() {
        return new Offset(Offset.OffsetType.PAGE_NUMBER, pageSize);
    }

}
