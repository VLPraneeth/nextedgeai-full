package com.syncari.core.model.misc;

import com.syncari.core.model.util.SyncDirection;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Data
@ToString
@Accessors(chain = true)
public class Watermark {
	private static final int NON_INITIALIZED = -1;
	public static final long CLOCK_SKEW_TOLERANCE_SECONDS = 5;
	// Timestamp stored in UTC epoc
	long start;
	long end;
	long offset;
	boolean initial;
	boolean isResync;
	boolean partialResync; // is this full resync or partial resync, useful for some synapses
	long limit;
    String changeStream;
	//Either inbound or outbound. It cannot be BIDI, because this is a watermark
	SyncDirection direction = SyncDirection.INBOUND;
	public final static String dateFormat = "yyyy-MM-dd HH:mm:ss";
	private StreamState streamState;
	private PruneState pruneState;

	public Watermark(long start, long end, boolean isInitial, long offset) {
		this.start = start;
		this.end = end;
		this.initial = isInitial;
		this.offset = offset;
	}

	public Watermark() {
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

	public boolean inRange(Instant timestamp){
		return timestamp.toEpochMilli() >= start && timestamp.toEpochMilli() < end;
	}
	public boolean inRange(long timestampMillis){
		return timestampMillis >= start && timestampMillis < end;
	}

	public Watermark moveBy(long windowSizeInMilli) {
		//if(isInitial()) return this;
		if(windowSizeInMilli < 0) throw new RuntimeException("Watermark move by window cannot be less than 0");
		long finalEnd = end + windowSizeInMilli;
		long now = Instant.now().minus(CLOCK_SKEW_TOLERANCE_SECONDS, ChronoUnit.SECONDS).toEpochMilli();
		return new Watermark(end, Math.min(finalEnd, now), initial, 0L).setResync(isResync).setPartialResync(partialResync).setChangeStream(changeStream);
	}

	public Optional<Watermark> moveTo(long toDate) {
		if (toDate < end) return Optional.empty();
		if (toDate > System.currentTimeMillis()) return Optional.empty();
		return Optional.of(new Watermark(end, toDate, initial, offset).setResync(isResync).setPartialResync(partialResync).setChangeStream(changeStream)
				.setStreamState(streamState));
	}

	public Watermark moveToNow() {
		if(isInitial() || isResync()) return this;
		long now = Instant.now().minus(CLOCK_SKEW_TOLERANCE_SECONDS, ChronoUnit.SECONDS).toEpochMilli();
		if(end > now) end = now;
		return new Watermark(end,now, initial, 0L).setResync(isResync).setPartialResync(partialResync).setChangeStream(changeStream);
	}

	private String format(long dateInEpocMilli) {
		if(dateInEpocMilli == NON_INITIALIZED) return "";
		DateTimeFormatter df = DateTimeFormatter.ofPattern(dateFormat).withZone(ZoneOffset.UTC);
		return df.format(Instant.ofEpochMilli(dateInEpocMilli));
	}

    public Watermark getCopy() {
        return new Watermark(start, end, initial, offset).setLimit(limit).setResync(isResync).setPartialResync(partialResync).setChangeStream(changeStream);
    }

}
