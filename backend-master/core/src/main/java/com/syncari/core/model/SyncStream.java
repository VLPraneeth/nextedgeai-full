package com.syncari.core.model;

import com.syncari.core.model.misc.PipelineError;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.List;

/**
 * This class represents a stream of entity instances flowing INTO a system.
 *
 */
@Data
@Accessors(chain = true)
public class SyncStream extends  UUIDAuditModel{

    private String graphId;

    //a globally unique ID for the processor that claimed this stream
    private String processorId;
    //When was the last time a stream processor checkin for this stream?
    private Instant checkin;
    //One of the below enum values
    private Status status;

    private String details;
    private Instant lastSuccessfulSync;

    private Instant lastCleanup;
    private PipelineError errorDetail;
    private String pausedBy;
    public static List<Status> ACTIVE_STATUS_LIST = List.of(Status.RUNNING, Status.READY,
            Status.PAUSING, Status.PAUSED, Status.ERROR);

    public static List<Status> LAG_REPORT_LIST = List.of(Status.RUNNING, Status.READY, Status.ERROR);

    public  enum Status{
        //New stream
        NEW,
        //Ready to be claimed
        READY,
        //Claimed by a processor. processorId must be set at this point
        CLAIMED,
        //Processor has begun processing the underlying stream of entities
        RUNNING,
        //The stream has been marked inactive externally. The processor must relinquish control
        // during its next checkin
        INACTIVE,
        //Pause command has been issued. Stream is still in its previous state
        PAUSING,
        //Paused
        PAUSED,
        //Stop command has been issued. Stream is still in its previous state
        STOPPING,
        //Explicitly stopped
        STOPPED,
        ERROR,

        // Do not use this for writing, it is only for reading status for karibu
        RESYNCING,
        QUEUED
    }

    public long lagInMillis(){
        return lastSuccessfulSync == null ? 0l : Instant.now().toEpochMilli() - lastSuccessfulSync.toEpochMilli();
    }
}
