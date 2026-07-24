package com.syncari.viper;

import akka.Done;
import akka.japi.Pair;
import akka.stream.UniqueKillSwitch;
import com.syncari.core.model.*;
import com.syncari.core.model.util.Status;
import com.syncari.core.repositories.syncari.OrganizationRepo;
import com.syncari.core.service.MappingGraphService;
import com.syncari.core.service.StreamService;
import com.syncari.core.service.UserService;
import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;
import scala.concurrent.Promise;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;


public class StreamManagerTest {

    @Test
    public void singleNodeAcquiresAllStreams() {

        MappingGraphService mappingGraphService= mock(MappingGraphService.class);

        GraphRunner graphRunner = mock(GraphRunner.class);

        StreamService streamService = mock(StreamService.class);
        OrganizationRepo organizationRepo = mock(OrganizationRepo.class);
        UserService userService = mock(UserService.class);
        StreamManager streamManager = createStreamManager();
        streamManager.totalNodes=1;
        when(organizationRepo.findAllActiveCustomers()).thenReturn(getOrgs(2));
        when(graphRunner.start(any(),any(),any(),any())).thenReturn(Pair.create(new UniqueKillSwitch(Promise.successful(Done.done())), CompletableFuture.completedFuture(Done.done())));
        when( userService.getSystemUser()).thenReturn(new User());
        when( mappingGraphService.retrieve(anyString())).thenReturn(Optional.of(new MappingGraph()));

        streamManager.streamService = streamService;
        streamManager.graphService = mappingGraphService;
        streamManager.graphRunner = graphRunner;
        streamManager.organizationRepo = organizationRepo;
        streamManager.userService = userService;
        when(streamService.totalActiveStreams()).thenReturn(
                12,13
        );
        when(streamService.claim(any(),eq(27))).thenReturn(getStreams(12, SyncStream.Status.CLAIMED));
        when(streamService.claim(any(),eq(15))).thenReturn(getStreams(13, SyncStream.Status.CLAIMED));
        streamManager.pollForNewStreams();
        assertEquals(25, streamManager.switches.size());
        streamManager.pollForNewStreams();
        //We've simulated that all streams are running. Expect the same number
        assertEquals(25, streamManager.switches.size());
    }

    @Test
    public void deadStreamsCleanedUpAtStart() {

        MappingGraphService mappingGraphService= mock(MappingGraphService.class);

        GraphRunner graphRunner = mock(GraphRunner.class);

        StreamService streamService = mock(StreamService.class);
        OrganizationRepo organizationRepo = mock(OrganizationRepo.class);
        UserService userService = mock(UserService.class);
        //we'll remove dead streams at the beginning
        StreamManager streamManager = createStreamManager(true);
        streamManager.totalNodes=1;
        when(organizationRepo.findAllActiveCustomers()).thenReturn(getOrgs(2));
        when(graphRunner.start(any(),any(),any(),any())).thenReturn(Pair.create(new UniqueKillSwitch(Promise.successful(Done.done())), CompletableFuture.completedFuture(Done.done())));
        when( userService.getSystemUser()).thenReturn(new User());
        when( mappingGraphService.retrieve(anyString())).thenReturn(Optional.of(new MappingGraph()));

        streamManager.streamService = streamService;
        streamManager.graphService = mappingGraphService;
        streamManager.graphRunner = graphRunner;
        streamManager.organizationRepo = organizationRepo;
        streamManager.userService = userService;
        when(streamService.totalActiveStreams()).thenReturn(
                12,13
        );
        when(streamService.claim(any(),eq(27))).thenReturn(getStreams(12, SyncStream.Status.CLAIMED));
        when(streamService.claim(any(),eq(15))).thenReturn(getStreams(13, SyncStream.Status.CLAIMED));
        streamManager.pollForNewStreams();
        assertEquals(25, streamManager.switches.size());
        streamManager.pollForNewStreams();
        //All streams are done. So we will clean it up now
        assertEquals(0, streamManager.switches.size());
    }
    public StreamManager createStreamManager() {
        return  createStreamManager(false);
    }
    public StreamManager createStreamManager(boolean removeDeadStreams) {
        return new StreamManager() {
            @Override
            protected void attacheStreamCompletionHandler(SyncStream claim, Pair<UniqueKillSwitch, CompletionStage<Done>> stream, ViperContext ctx) {
                //do nothing
            }
            @Override
            protected void removeDeadStreams(){
                if(removeDeadStreams) super.removeDeadStreams();
            }
        };
    }

    @Test
    public void streamsDistributedAcrossMultipleNodes() {

        MappingGraphService mappingGraphService= mock(MappingGraphService.class);

        GraphRunner graphRunner = mock(GraphRunner.class);

        StreamService streamService = mock(StreamService.class);
        OrganizationRepo organizationRepo = mock(OrganizationRepo.class);
        UserService userService = mock(UserService.class);

        var streamManager1 = createStreamManager();
        streamManager1.totalNodes=3;
        streamManager1.streamService = streamService;
        streamManager1.graphService = mappingGraphService;
        streamManager1.graphRunner = graphRunner;
        streamManager1.organizationRepo = organizationRepo;
        streamManager1.userService = userService;

        var streamManager2 = createStreamManager();
        streamManager2.totalNodes=3;
        streamManager2.streamService = streamService;
        streamManager2.graphService = mappingGraphService;
        streamManager2.graphRunner = graphRunner;
        streamManager2.organizationRepo = organizationRepo;
        streamManager2.userService = userService;

        var streamManager3 = createStreamManager();
        streamManager3.totalNodes=3;
        streamManager3.streamService = streamService;
        streamManager3.graphService = mappingGraphService;
        streamManager3.graphRunner = graphRunner;
        streamManager3.organizationRepo = organizationRepo;
        streamManager3.userService = userService;

        when(organizationRepo.findAllActiveCustomers()).thenReturn(getOrgs(2));
        when(graphRunner.start(any(),any(),any(),any())).thenReturn(Pair.create(new UniqueKillSwitch(Promise.successful(Done.done())), CompletableFuture.completedFuture(Done.done())));
        when( userService.getSystemUser()).thenReturn(new User());
        when( mappingGraphService.retrieve(anyString())).thenReturn(Optional.of(new MappingGraph()));

        when(streamService.totalActiveStreams()).thenReturn(
                12,13
        );

        when(streamService.claim(any(),eq(10))).thenReturn(getStreams(10, SyncStream.Status.CLAIMED));
        when(streamService.claim(any(),eq(0))).thenReturn(getStreams(0, SyncStream.Status.CLAIMED));

        streamManager1.pollForNewStreams();
        assertEquals(10, streamManager1.switches.size());
        streamManager1.pollForNewStreams();
        assertEquals(10, streamManager1.switches.size());

        when(streamService.claim(any(),eq(10))).thenReturn(getStreams(3, SyncStream.Status.CLAIMED));
        when(streamService.claim(any(),eq(7))).thenReturn(getStreams(7, SyncStream.Status.CLAIMED));

        streamManager2.pollForNewStreams();
        assertEquals(10, streamManager2.switches.size());
        streamManager2.pollForNewStreams();
        assertEquals(10, streamManager2.switches.size());

        when(streamService.claim(any(),eq(10))).thenReturn(getStreams(0, SyncStream.Status.CLAIMED));
        when(streamService.claim(any(),eq(10))).thenReturn(getStreams(7, SyncStream.Status.CLAIMED));

        streamManager3.pollForNewStreams();
        assertEquals(7, streamManager3.switches.size());
        streamManager3.pollForNewStreams();
        assertEquals(7, streamManager3.switches.size());

    }
    @Test
    public void newlyAddedStreamsDistributedEvenly() {

        MappingGraphService mappingGraphService= mock(MappingGraphService.class);

        GraphRunner graphRunner = mock(GraphRunner.class);

        StreamService streamService = mock(StreamService.class);
        OrganizationRepo organizationRepo = mock(OrganizationRepo.class);
        UserService userService = mock(UserService.class);

        var streamManager1 = createStreamManager();
        streamManager1.totalNodes=2;
        streamManager1.streamService = streamService;
        streamManager1.graphService = mappingGraphService;
        streamManager1.graphRunner = graphRunner;
        streamManager1.organizationRepo = organizationRepo;
        streamManager1.userService = userService;

        var streamManager2 = createStreamManager();
        streamManager2.totalNodes=2;
        streamManager2.streamService = streamService;
        streamManager2.graphService = mappingGraphService;
        streamManager2.graphRunner = graphRunner;
        streamManager2.organizationRepo = organizationRepo;
        streamManager2.userService = userService;



        when(organizationRepo.findAllActiveCustomers()).thenReturn(getOrgs(2));
        when(graphRunner.start(any(),any(),any(),any())).thenReturn(Pair.create(new UniqueKillSwitch(Promise.successful(Done.done())), CompletableFuture.completedFuture(Done.done())));
        when( userService.getSystemUser()).thenReturn(new User());
        when( mappingGraphService.retrieve(anyString())).thenReturn(Optional.of(new MappingGraph()));

        when(streamService.totalActiveStreams()).thenReturn(
                12,13
        );

        when(streamService.claim(any(),eq(14))).thenReturn(getStreams(13, SyncStream.Status.CLAIMED));
        when(streamService.claim(any(),eq(1))).thenReturn(getStreams(1, SyncStream.Status.CLAIMED));

        streamManager1.pollForNewStreams();
        assertEquals(14, streamManager1.switches.size());
        when(streamService.totalActiveStreams()).thenReturn(
                12,13
        );
        when(streamService.claim(any(),eq(0))).thenReturn(getStreams(0, SyncStream.Status.CLAIMED));
        when(streamService.claim(any(),eq(0))).thenReturn(getStreams(0, SyncStream.Status.CLAIMED));

        streamManager1.pollForNewStreams();
        assertEquals(14, streamManager1.switches.size());


        when(streamService.totalActiveStreams()).thenReturn(
                12,13
        );
        when(streamService.claim(any(),eq(14))).thenReturn(getStreams(0, SyncStream.Status.CLAIMED),getStreams(12, SyncStream.Status.CLAIMED));
        streamManager2.pollForNewStreams();
        assertEquals(12, streamManager2.switches.size());

        //simulate 12 new streams added to first instance
        when(streamService.totalActiveStreams()).thenReturn(24,13);
        when(streamService.claim(any(),eq(6))).thenReturn(getStreams(6, SyncStream.Status.CLAIMED));
        when(streamService.claim(any(),eq(0))).thenReturn(getStreams(0, SyncStream.Status.CLAIMED));

        streamManager1.pollForNewStreams();
        assertEquals(20, streamManager1.switches.size());

        when(streamService.totalActiveStreams()).thenReturn(24,13);
        when(streamService.claim(any(),eq(8))).thenReturn(getStreams(6, SyncStream.Status.CLAIMED));
        when(streamService.claim(any(),eq(2))).thenReturn(getStreams(0, SyncStream.Status.CLAIMED));

        streamManager2.pollForNewStreams();
        assertEquals(18, streamManager2.switches.size());

    }


    private List<SyncStream> getStreams(int num, SyncStream.Status status) {
        List<SyncStream> streams =  new ArrayList<>();
        for(int i=0;i<num;i++){
            SyncStream stream = new SyncStream().setGraphId(ObjectId.get().toHexString()).setStatus(status);
            stream.setId(ObjectId.get().toHexString());
            streams.add(stream);
        }
        return streams;
    }

    private List<Organization> getOrgs(int num) {
        List<Organization> orgs=  new ArrayList<>();
        for(int i=0;i<num;i++){
            Organization org = new Organization();
            org.setName("Org"+i);
            org.setId("Org"+i);
            org.setStatus(Status.ACTIVE);
            var instance =new Instance();
            instance.setName(org.getName()+"-instance "+i);
            instance.setSyncariId("00000"+i);
            instance.setStatus(Status.ACTIVE);
            org.setInstances(List.of(instance));
            orgs.add(org);
        }
        return orgs;
    }
}