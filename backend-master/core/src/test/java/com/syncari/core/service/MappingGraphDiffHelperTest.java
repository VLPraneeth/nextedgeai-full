package com.syncari.core.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.bson.types.ObjectId;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.Edge;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.FunctionCall;
import com.syncari.core.model.FunctionDefinition;
import com.syncari.core.model.InputPort;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.OutputPort;
import com.syncari.core.model.ParameterValue;
import com.syncari.core.model.SimpleFunctionNodeConfig;
import com.syncari.core.model.User;
import com.syncari.core.model.misc.RoleConstants;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Status;
import com.syncari.core.model.versioning.ActionType;
import com.syncari.core.model.versioning.Diff;
import com.syncari.core.model.versioning.Version;
import com.syncari.core.repositories.customer.AttributeRepo;
import com.syncari.core.repositories.customer.EdgeRepo;
import com.syncari.core.repositories.customer.EntityDefinitionRepo;
import com.syncari.core.repositories.customer.LockRepo;
import com.syncari.core.repositories.customer.MappingGraphRepo;
import com.syncari.core.repositories.customer.MappingNodeRepo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DirtiesContext
public class MappingGraphDiffHelperTest extends AbstractSyncariTest {
	@Autowired
	MappingGraphDiffHelper diffHelper;
	@Autowired
	SchemaService schemaService;
	@Autowired
	MappingGraphService mappingGraphService;
	@MockBean
	private EdgeRepo edgeRepo;
	@Autowired
	private FunctionService functionDefinitionRepo;
	@Autowired
	UserService userService;
	@Autowired
	ConnectorService connectorService;
	@Autowired
	AttributeRepo attributeProxyRepo;
	@Autowired
	MappingGraphRepo mappingGraphRepo;
	@Autowired
	EntityDefinitionRepo entityProxyRepo;
	@Autowired
	LockRepo lockRepo;

	@Override
    public void setUp() {
        super.setUp();
        connectorService.publisher = publisher;
        User notificationUser = new User("notif@email.com", "NewPassw0rd", Status.ACTIVE, SyncariContext.getSyncariId());
        notificationUser.addAvailableInstance(SyncariContext.getSyncariId());
        userService.addUser(notificationUser);
        userService.assignRolesToUser(SyncariContext.getOrganziation(), SyncariContext.getInstance(), notificationUser, Set.of(RoleConstants.ORG_ADMIN));
    }

	@Override
	public void tearDown() {
		super.tearDown();
		resetRepos(attributeProxyRepo, entityProxyRepo, edgeRepo, mappingGraphRepo, lockRepo);
		userService.deleteUser(userService.getUser("notif@email.com").getId());
	}

	@Test
	public void hasDiff(){
		EntityDefinition syncariEntity = schemaService.getSyncariEntityByName("account").get();
		MappingGraph defaultEntityGraph = mappingGraphService.createDefaultEntityGraph(syncariEntity.getId());
		MappingGraph v1Graph = mappingGraphService.createVersion(defaultEntityGraph, Version.builder()
				.actionType(ActionType.Manual)
				.id(new ObjectId().toHexString())
    			.name("V1")
    			.summary("V1 Summary")
    			.numberOfChanges(0)
				.build());
		
		MappingGraph v2Graph = mappingGraphService.createVersion(defaultEntityGraph, Version.builder()
				.actionType(ActionType.Manual)
				.id(new ObjectId().toHexString())
    			.name("V2")
    			.summary("V2 Summary")
    			.numberOfChanges(0)
				.build());
		
		FunctionDefinition mask = functionDefinitionRepo.findByNameAndScope("mask", Scope.ATTRIBUTE).get();
		FunctionCall sfdc = mask.withParams(ParameterValue.string("a.b", "sfdc"));

		var node1 = new MappingNode().setName("Save").setApiName("Save").setScope(Scope.ENTITY)
				.setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(sfdc))
				.setMappingGraphId(defaultEntityGraph.getId());
		node1.setId(new ObjectId().toHexString());

		var node2 = new MappingNode().setName("Zzzz").setApiName("Zzzz").setScope(Scope.ENTITY)
				.setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(sfdc))
				.setMappingGraphId(defaultEntityGraph.getId());
		node2.setId(new ObjectId().toHexString());
		 
		 MappingGraph v3Graph = mappingGraphService.createVersion(defaultEntityGraph, Version.builder()
					.actionType(ActionType.Manual)
					.id(new ObjectId().toHexString())
	    			.name("V3")
	    			.summary("V3 Summary")
	    			.numberOfChanges(0)
					.build());
		 
		 assertFalse(diffHelper.hasDiff(v1Graph, v2Graph));

		 MappingNodeRepo nodeRepo = Mockito.mock(MappingNodeRepo.class);
		 when(nodeRepo.findByGraphId(v3Graph.getId())).thenReturn(List.of(node1, node2));
		 assertFalse(diffHelper.hasDiff(v1Graph, v3Graph));
	}
	
	@Test
	public void diff(){
		EntityDefinition syncariEntity = schemaService.getSyncariEntityByName("account").get();
		MappingGraph defaultEntityGraph = mappingGraphService.createDefaultEntityGraph(syncariEntity.getId());
		MappingGraph v1Graph = mappingGraphService.createVersion(defaultEntityGraph, Version.builder()
				.actionType(ActionType.Manual)
				.id(new ObjectId().toHexString())
    			.name("V1")
    			.summary("V1 Summary")
    			.numberOfChanges(0)
				.build());
		
		MappingGraph v2Graph = mappingGraphService.createVersion(defaultEntityGraph, Version.builder()
				.actionType(ActionType.Manual)
				.id(new ObjectId().toHexString())
    			.name("V2")
    			.summary("V2 Summary")
    			.numberOfChanges(0)
				.build());
		
		FunctionDefinition mask = functionDefinitionRepo.findByNameAndScope("mask", Scope.ATTRIBUTE).get();
		FunctionCall sfdc = mask.withParams(ParameterValue.string("a.b", "sfdc"));

		var node1 = new MappingNode().setName("Save").setApiName("Save").setScope(Scope.ENTITY)
				.setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(sfdc))
				.setMappingGraphId(defaultEntityGraph.getId());
		node1.setId(new ObjectId().toHexString());

		var node2 = new MappingNode().setName("Zzzz").setApiName("Zzzz").setScope(Scope.ENTITY)
				.setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(sfdc))
				.setMappingGraphId(defaultEntityGraph.getId());
		node2.setId(new ObjectId().toHexString());

		 MappingNodeRepo nodeRepo = Mockito.mock(MappingNodeRepo.class);
		 when(nodeRepo.findByGraphId(v2Graph.getId())).thenReturn(List.of(node1, node2));
		 List<Diff> diffs = diffHelper.diffGraphs(Optional.of(v1Graph), Optional.of(v2Graph));
	}
}
