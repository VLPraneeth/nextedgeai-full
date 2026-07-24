package com.syncari.viper.streams.operators;

import akka.stream.Attributes;
import akka.stream.FlowShape;
import akka.stream.Inlet;
import akka.stream.Outlet;
import akka.stream.stage.*;
import com.syncari.utils.Pair;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

@Slf4j
public class CollectWhile<T> extends GraphStage<FlowShape<T, Iterable<T>>> {

    Inlet<T> in = Inlet.create("CollectWhile.in");
    Outlet<Iterable<T>> out = Outlet.create("CollectWhile.out");
    private Function<T, String> keyFunction;
    private Predicate<Pair<T, List<T>>> isLast;

    public CollectWhile(Function<T, String> keyFunction, Predicate<Pair<T, List<T>>> isLast){

        this.keyFunction = keyFunction;
        this.isLast = isLast;
    }

    private long THRESHOLD_IN_MILLIS = 5 * 60 * 1000;

    @Override
    public FlowShape<T, Iterable<T>> shape(){
        return FlowShape.of(in, out);
    }
    @Override
    public GraphStageLogic createLogic(Attributes inheritedAttributes) throws Exception {

        return  new GraphStageLogic(shape()) {
            private Map<String, List<T>> buffer = new HashMap<>();
            private Map<String, Long> age = new HashMap<>();

            {
                setHandlers(in, out, new AbstractInOutHandler() {
                    @Override
                    public void onPull() {
                        pull(in);
                    }

                    @Override
                    public void onUpstreamFinish(){
                        buffer.forEach((key, collected) -> push(out, collected));
                        buffer.clear();
                        age.clear();
                        completeStage();
                    }

                    @Override
                    public void onPush() throws Exception {
                        var t = grab(in);
                        String key = keyFunction.apply(t);
                        age.forEach((k, timestamp) -> {
                            if (System.currentTimeMillis() - timestamp > THRESHOLD_IN_MILLIS) {
                                log.warn("%s has been sitting for more than %s seconds waiting", k, THRESHOLD_IN_MILLIS);
                            }
                        });
                        var collectedSoFar = buffer.getOrDefault(key, new ArrayList<>());
                        collectedSoFar.add(t);
                        buffer.put(key, collectedSoFar);
                        age.put(key, System.currentTimeMillis());
                        if (isLast.test(Pair.of(t, buffer.get(key)))) {
                            push(out, buffer.get(key));
                            buffer.remove(key);
                            age.remove(key);
                        } else {
                            pull(in);
                        }
                    }
                });
            }
        };

    }
}
