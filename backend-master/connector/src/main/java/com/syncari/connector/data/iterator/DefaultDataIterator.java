package com.syncari.connector.data.iterator;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jooq.lambda.function.Function3;

import lombok.extern.slf4j.Slf4j;
import com.syncari.connector.EntityData;
import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.WatermarkInfo;
import com.syncari.utils.Pair;

@Slf4j
public class DefaultDataIterator extends AbstractEntityDataBatchIterator {
    public static String DEFAULT_ZONE = "UTC";

	protected List<EntityData> data = new ArrayList<>();
	protected WatermarkInfo baseWatermark;
	protected long offset = 0;
	boolean isLastPage = false;
    String timeZone = DEFAULT_ZONE;
	String wmDataType = "datetime";
	protected Function3<WatermarkInfo, Integer, Long, Pair<Long, Stream<EntityData>>> generator;
	protected AttributeSchema watermarkField;
	IteratorHelper helper = new IteratorHelper();


	public DefaultDataIterator(WatermarkInfo baseWatermark, long offset,
							   Function3<WatermarkInfo, Integer, Long, Pair<Long, Stream<EntityData>>> generator,
							   List<EntityData> data, AttributeSchema watermarkField,int maxRecords) {
		this.baseWatermark = baseWatermark;
		this.offset = offset;
		this.generator = generator;
		this.data = data;
		this.watermarkField = watermarkField;
		this.maxRecords = maxRecords;
	}
	public DefaultDataIterator(WatermarkInfo baseWatermark, long offset,
							   Function3<WatermarkInfo, Integer, Long, Pair<Long, Stream<EntityData>>> generator,
							   List<EntityData> data, AttributeSchema watermarkField,int pageSize, int maxRecords) {
		this(baseWatermark,offset,generator,data,watermarkField,maxRecords);
		this.pageSize = pageSize;
	}

    public DefaultDataIterator(WatermarkInfo baseWatermark, long offset,
							   Function3<WatermarkInfo, Integer, Long, Pair<Long, Stream<EntityData>>> generator,
							   List<EntityData> data, AttributeSchema watermarkField,int pageSize, int maxRecords,
							   String timeZone, String wmDataType) {
		this(baseWatermark,offset,generator,data,watermarkField,maxRecords);
		this.pageSize = pageSize;
        this.timeZone = timeZone;
		this.wmDataType = wmDataType;
	}

	public Pair<Long, Stream<EntityData>> generate(
			Function3<WatermarkInfo, Integer, Long, Pair<Long, Stream<EntityData>>> generator) {
		return generator.apply(baseWatermark, getEffectivePageSize(), offset);
	}


	@Override
	public boolean hasNext() {
		// We have already consumed last page. Nothing more here
		if (isLastPage && isConsumed() || hasFetchedMaxRecords()) {
			log.info("Iterator has been drained. Either this is the last page or this cycle has reached max records. " +
				"isLastPage/isConsumed/hasFetchedMaxRecords:{}/{}/{}", isLastPage, isConsumed(), hasFetchedMaxRecords());
			return false;
		}
		// retrieved data is not yet consumed.
		if (!isConsumed())
			return true;
		long now = System.currentTimeMillis();
		var results = generator.apply(baseWatermark, getEffectivePageSize(), offset);
		long done = System.currentTimeMillis();

		Stream<EntityData> entityDataStream = results.y;
		// Since getByRecency does not support end watermark, we have to filter out
		// records
		// that are beyond the end watermark in the last page
		if (results.x < getEffectivePageSize()) {
			log.info("Processing lastpage. baseWatermark: {}", baseWatermark);
		}
        long endWm = getWatermarkEnd();
		log.info("End watermark for this batch {} with timezone {}", endWm, timeZone);
		// if a synapse doesn't give sorted results - remove records beyond wm window for all batches
		entityDataStream = entityDataStream.filter(e -> getWatermarkValue(e) <= baseWatermark.getEnd()
				|| (baseWatermark.isInitial() && !baseWatermark.hasEnd()));
		int recordsToConsume = (int)(maxRecords > 0 ? maxRecords - totalRecordsFetched : results.x);
		data = entityDataStream.limit(recordsToConsume).collect(Collectors.toList());
		stats.addLatencyCount((done-now),data.size());
		offset = nextOffset(results, data);
		isLastPage = isLastPage();
		if (data.isEmpty()) {
			log.info("Iterator has been drained. datasize is 0. offset/isLastPage/pageSize: {}/{}/{} ", offset, isLastPage, pageSize);
		}
		return data.size() > 0;
	}

    protected long getWatermarkEnd() {
		// For non temporal watermark (like integer wm), return the value as is.
		if (!List.of("timestamp", "datetime").contains(wmDataType)) {
			return baseWatermark.getEnd();
		}
        long zoneOffset = OffsetDateTime.ofInstant(Instant.ofEpochMilli(baseWatermark.getEnd()), ZoneId.of(timeZone))
            .get(ChronoField.OFFSET_SECONDS);
        return "UTC".equalsIgnoreCase(timeZone) ? baseWatermark.getEnd() : baseWatermark.getEnd() + zoneOffset * 1000;
    }

	protected boolean isLastPage() {
		return data.size() < getEffectivePageSize();
	}

    protected long nextOffset(Pair<Long, Stream<EntityData>> results, List<EntityData> data) {
        return offset + data.size();
    }

	private boolean isConsumed() {
		return data.isEmpty();
	}

	@Override
	public List<EntityData> next() {
		// reset data to mark it as consumed
		var temp = data;
		if (!data.isEmpty()) {
			EntityData entityData = data.get(data.size() - 1);
			lastWatermark = getWatermarkValue(entityData);
			totalRecordsFetched+=data.size();
		}
		data = new ArrayList<>();

		return temp;
	}

	/**
	 * lastModified TS is the default watermark. Subclasses can override if needed
	 * @param entityData
	 * @return
	 */
	protected long getWatermarkValue(EntityData entityData) {
		return entityData.getLastModified();
	}

    @Override
    public Offset getOffsetInfo() {
        return new Offset(Offset.OffsetType.NONE, getEffectivePageSize());
    }

}
