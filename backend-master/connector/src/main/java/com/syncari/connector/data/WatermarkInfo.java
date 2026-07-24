package com.syncari.connector.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.Wither;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Data
@Accessors(chain = true)
@AllArgsConstructor
@Wither
public class WatermarkInfo {
	private static final int NON_INITIALIZED = -1;
	// Timestamp stored in UTC epoc milliseconds
	long start;
	long end;
	long offset;
	boolean initial;
    @JsonProperty(value="isResync")
	boolean isResync;
    @JsonProperty(value="isTest")
	boolean isTest;
	@JsonProperty(value="partialResync")
	boolean partialResync; // is this full resync or partial resync, useful for some synapses
	int limit;
    String changeStream;
	public final static String dateFormat = "yyyy-MM-dd HH:mm:ss";
	StreamState streamState;
	PruneState pruneState;

	public WatermarkInfo(long start, long end, boolean isInitial, long offset) {
		this.start = start;
		this.end = end;
		this.initial = isInitial;
		this.offset = offset;
	}

	public WatermarkInfo() {
	}

	@Override
	public String toString() {
		return "Watermark [start=" + format(start) + ", end=" + format(end) + ", offset=" + offset +
				", initial=" + initial + ", isResync=" + isResync + ", changeStream=" + changeStream + 
                ", limit=" + limit + ", partialResync=" + partialResync +"]";
	}
	
	public boolean hasEnd() {
		return end != NON_INITIALIZED;
	}
	
	public boolean hasStart() {
		return start != NON_INITIALIZED;
	}
	
	public void addOffset(long delta) {
		if(delta < 0) throw new RuntimeException("Offset cannot be less than 0");
		offset = offset + delta;
	}

	public WatermarkInfo moveBy(long value){
		this.start = end;
		this.end = end+value;
		return this;
	}
	public long getDurationMs(){
		long now = Instant.now().toEpochMilli();
		return (hasEnd() ? end : now) - (hasStart()? start : now);
	}
	private String format(long dateInEpocMilli) {
		if(dateInEpocMilli == NON_INITIALIZED) return "";
		DateTimeFormatter df = DateTimeFormatter.ofPattern(dateFormat).withZone(ZoneOffset.UTC);
		return df.format(Instant.ofEpochMilli(dateInEpocMilli));
	}

	public WatermarkInfo copy() {
		return new WatermarkInfo(start, end, initial, offset).setTest(isTest).setLimit(limit).setResync(isResync).setPartialResync(partialResync).setPartialResync(partialResync);
	}

	public StreamState getStreamState() {
		if(streamState == null) {
			streamState = new StreamState();
		}
		return streamState;
	}
	
}
