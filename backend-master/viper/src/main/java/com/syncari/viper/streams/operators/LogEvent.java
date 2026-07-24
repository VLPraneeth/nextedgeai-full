package com.syncari.viper.streams.operators;

import akka.stream.Attributes;
import akka.stream.FlowShape;
import akka.stream.Inlet;
import akka.stream.Outlet;
import akka.stream.stage.AbstractInOutHandler;
import akka.stream.stage.GraphStage;
import akka.stream.stage.GraphStageLogic;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.Event;
import com.syncari.core.service.EventService;
import com.syncari.viper.ViperContext;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Function;

@Slf4j
public class LogEvent<T> extends GraphStage<FlowShape<T, T>> {
    private Inlet<T> in = Inlet.create("LogEvent.in");
    private Outlet<T> out = Outlet.create("LogEvent.out");
    private EventService eventService;
    private Function<T, Event> extract;
    private ViperContext context;

    public LogEvent(EventService eventService, Function<T, Event> extract, ViperContext context) {
        this.eventService = eventService;
        this.extract = extract;
        this.context = context;
    }

    public GraphStageLogic createLogic(Attributes inheritedAttributes) {
        return new GraphStageLogic(shape()) {
            {
                setHandlers(in, out, new AbstractInOutHandler() {
                    @Override
                    public void onPull() {
                        pull(in);
                    }

                    @Override
                    public void onPush() {
                        try {
                            context.setSyncariContext();
                            var elem = grab(in);
                            var event = extract.apply(elem);
                            eventService.log(event);
                            push(out, elem);
                        } catch (Exception ex) {
                            log.error("Could not log event", ex);
                        }finally {
                            context.resetSyncariContext();
                        }
                    }
                });
            }
        };
    }

    public FlowShape<T, T> shape() {
        return FlowShape.of(in, out);
    }
}