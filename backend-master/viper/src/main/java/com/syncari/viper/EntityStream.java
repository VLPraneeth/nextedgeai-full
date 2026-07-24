package com.syncari.viper;


import akka.stream.Attributes;
import akka.stream.Outlet;
import akka.stream.SourceShape;
import akka.stream.stage.AbstractOutHandler;
import akka.stream.stage.GraphStage;
import akka.stream.stage.GraphStageLogic;
import akka.stream.stage.TimerGraphStageLogic;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Configurable;
import scala.concurrent.duration.FiniteDuration;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;


@Configurable
@Slf4j
public class EntityStream extends GraphStage<SourceShape<Long>> {
    private String streamId;
    private Supplier<Long> pollingIntervalSupplier;
    private Function<Long, Long> checkinHandler;
    private ViperContext context;
    public final Outlet<Long> out = Outlet.create("EntityStreamSource.out");
    private final SourceShape<Long> shape = SourceShape.of(out);
    private final Queue<Long> buffer = new ArrayDeque<>();
    private long lastToken = -1l;
    private long pollingInterval;

    public Long getLastCheckIn() {
        return lastCheckIn;
    }

    private long lastCheckIn = 0l;
    public static final FiniteDuration CHECKIN_SCHEDEULE_INTERVAL = FiniteDuration.apply(5, TimeUnit.SECONDS);
    private FiniteDuration checkinSchedule = CHECKIN_SCHEDEULE_INTERVAL;
    public EntityStream(String streamId, Long pollingIntervalSeconds, Function<Long, Long> checkinHandler, ViperContext context) {
        this(streamId, () -> pollingIntervalSeconds, checkinHandler, context);
    }
    public EntityStream(String streamId, Supplier<Long> pollingIntervalSupplier, Function<Long, Long> checkinHandler, ViperContext context) {
        this.streamId = streamId;
        this.pollingIntervalSupplier = pollingIntervalSupplier;
        this.pollingInterval = pollingIntervalSupplier.get();
        this.checkinSchedule = FiniteDuration.apply(Math.min(CHECKIN_SCHEDEULE_INTERVAL.toSeconds(),pollingInterval), TimeUnit.SECONDS);
        this.checkinHandler = checkinHandler;
        this.context = context;
        buffer.add(System.currentTimeMillis());
    }

    public void grantToken() {
        long token = System.currentTimeMillis();
        log.debug("Granting token {} for Stream {}", token, streamId);
        buffer.add(token);
    }

    @Override
    public SourceShape<Long> shape() {
        return shape;
    }

    @Override
    public GraphStageLogic createLogic(Attributes inheritedAttributes) {
        return new TimerGraphStageLogic(shape) {
            private boolean stopped = false;

            @Override
            public void postStop() throws Exception {
                stopped = true;
            }

            @Override
            public void preStart() throws Exception {

                Runnable runnable = new Runnable() {
                    @Override
                    public void run() {
                        context.with(() -> {
                            if (!stopped && !isClosed(out)) {
                                try {
                                    lastCheckIn = checkinHandler.apply(lastCheckIn);
                                } catch (Exception e) {
                                    //Don't propagate the error here, because we want the checkin thread to continue
                                    log.error("Error in checkin thread {}", e.getMessage(), e);
                                }
                                materializer().scheduleOnce(checkinSchedule, this);
                            } else {
                                log.info("Stream has stopped. Shutting down scheduler for {}",streamId);
                            }
                        });
                    }
                };
                materializer().scheduleOnce(checkinSchedule, runnable);
            }

            {
                //Seed the buffer
                setHandler(out, new AbstractOutHandler() {

                    @Override
                    public void onPull() throws Exception {
                        context.with(() -> {
                            log.debug("On Pull Handler {}",streamId);
                            if (!buffer.isEmpty()) {
                                log.debug("Buffer Not Empty, pushing {}",streamId);
                                pushHead();
                            } else {
                                log.debug("Buffer empty, waiting for more work {}",streamId) ;
                                schedulePoll();
                            }
                        });
                    }
                });
            }

            @Override
            public void onTimer(Object timerKey) {
                context.with(() -> {
                    if (!isClosed(out)) {
                        log.debug("Looking for work through scheduler for Stream {}", streamId);
                        //doPoll();
                        if (!buffer.isEmpty()) {
                            log.debug("Work found through scheduler. Pushing work now for Stream {}", streamId);
                            pushHead();
                        } else {
                            log.debug("Scheduler did not find work for Stream {}. Waiting again", streamId);
                            schedulePoll();
                        }
                    }
                });
            }


            private void pushHead() {
                final Long head = buffer.poll();
                if (head != null) {
                    lastToken = head;
                    push(out, head);
                }
            }

            private void schedulePoll() {
                scheduleOnce("poll_" + streamId, Duration.ofSeconds(calculateInterval()));
            }
        };
    }

    /**
     *
     * @return
     */
    private long calculateInterval() {
        final Long interval = pollingIntervalSupplier.get();
        pollingInterval = interval;
        log.debug("Calculated interval for stream {} :{} ",streamId,interval);

        return interval;
    }

    public long getLastToken() {
        return lastToken;
    }
}
