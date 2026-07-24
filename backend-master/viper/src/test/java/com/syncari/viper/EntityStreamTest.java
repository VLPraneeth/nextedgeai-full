package com.syncari.viper;

import akka.Done;
import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
import akka.stream.ActorMaterializerSettings;
import akka.stream.javadsl.Source;
import com.syncari.core.model.Instance;
import com.syncari.core.model.Organization;
import com.syncari.core.model.User;
import com.syncari.viper.streams.operators.CollectWhile;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.lang.Thread.currentThread;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EntityStreamTest {

    @Test
    public void checkinHook() throws InterruptedException {
        ActorSystem viper = ActorSystem.create("viper");
        ActorMaterializer actorMaterializer = ActorMaterializer.create(ActorMaterializerSettings.apply(viper).withInputBuffer(1, 1), viper);

        Set<Long> savedLastCheckins = new HashSet<>();
        Function<Long,Long> checkinHandler = l -> {
            long now = System.currentTimeMillis();
            savedLastCheckins.add(now);
            return now;
        };
        ViperContext viperContext = new ViperContext(new Organization(), new Instance(), new User());
        Supplier<Long> intervaler = () -> 5l;
        EntityStream g = new EntityStream("dummyStream", intervaler, checkinHandler, viperContext);
        Source<Long, NotUsed> src = Source.fromGraph(g);

        CompletionStage<Done> completionStage = src
                .mapConcat(f -> List.of(g.getLastToken(), g.getLastToken()))

                .mapAsync(2, value -> CompletableFuture.supplyAsync(() -> value, actorMaterializer.executionContext()))

                .via(new CollectWhile<>(s -> s.toString(), (state -> {
                    System.out.println(currentThread().getName() + ":State " + state);
                    return Set.copyOf(state.y).size() == 1 && state.y.size() == 2;
                })))

                .runForeach(o -> {
                    System.out.println(currentThread().getName() + ":Granting next token " + g.getLastToken());
                    g.grantToken();
                }, actorMaterializer);

        //3 minutes
        Thread.sleep(30*1000);
        //3 minutes, schedule is set to 5 seconds, so we get around 5-6
        assertTrue(savedLastCheckins.size()>=5);
        assertTrue(savedLastCheckins.size()<=6);
        assertEquals(savedLastCheckins.stream().max(Comparator.comparingLong(e->e)).get(),g.getLastCheckIn());
    }

}