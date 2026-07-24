package com.syncari.core;

import java.util.List;
import java.util.Optional;
import java.util.Stack;
import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;

import com.syncari.core.model.Instance;
import com.syncari.core.model.Organization;
import com.syncari.core.model.Resource;
import com.syncari.core.model.ResourceType;
import com.syncari.core.model.User;

public class SyncariContext {
	private static ThreadLocal<Organization> organizationThreadLocal = new ThreadLocal<>();
	private static ThreadLocal<Instance> instanceThreadLocal = new ThreadLocal<>();
	private static ThreadLocal<User> userThreadLocal = new ThreadLocal<>();
	private static ThreadLocal<Boolean> ghostThreadLocal =ThreadLocal.withInitial(()-> false);
	private static ThreadLocal<Boolean> readOnlyOpThreadLocal =ThreadLocal.withInitial(()-> false);
	public static ThreadLocal<Stack<List<Object>>> contextStack =ThreadLocal.withInitial(()->new Stack());
	public static void setOrganziation(Organization organization) {
		organizationThreadLocal.set(organization);
	}


	public static void push(){
		if(getOrganziation()!=null && getInstance()!=null && getUser()!=null) {
			contextStack.get().push(List.of(getOrganziation(), getInstance(), getUser()));
		}
	}

	public static void restore(){
		if(!contextStack.get().empty()) {
			var previousContext = contextStack.get().pop();
			setOrganziation((Organization) previousContext.get(0));
			setInstance((Instance) previousContext.get(1));
			setUser((User) previousContext.get(2));
		}
	}

	public static void runWithContext(Organization organization, Instance instance, User user, Runnable block){
		SyncariContext.push();
		SyncariContext.setOrganziation(organization);
		SyncariContext.setInstance(instance);
		SyncariContext.setUser(user);
		try{
			block.run();
		}finally {
			SyncariContext.restore();
		}
	}

	public static <T>T runWithSupplier(Organization organization, Instance instance, User user, Supplier<T> block){
	    SyncariContext.setOrganziation(organization);
	    SyncariContext.setInstance(instance);
	    SyncariContext.setUser(user);
	    return block.get();
	}

	public static void setInstance(Instance instance) {
		instanceThreadLocal.set(instance);
	}

    // TODO: fix typo and all uses
	public static Organization getOrganziation() {
		return organizationThreadLocal.get();
	}

	public static Instance getInstance() {
		return instanceThreadLocal.get();
	}

	public static String getSyncariId() {
		Instance instance = instanceThreadLocal.get();
		if(instance == null) throw new RuntimeException("Instance not set in syncari context");
		if(StringUtils.isBlank(instance.getSyncariId())) throw new RuntimeException("SyncariId not set in syncari context");
		return instance.getSyncariId();
	}

	public static String getDatabase() {
		Instance instance = instanceThreadLocal.get();
		Optional<Resource> resource = instance.getResource(ResourceType.DATABASE);
		return resource.map(r -> r.getConfiguration().get("database")).orElseThrow();
	}

	public static void setUser(User user) {
		userThreadLocal.set(user);
	}

	public static void setGhost(boolean ghosted) {
	    ghostThreadLocal.set(ghosted);
	}

	public static boolean isGhost() {
	    return ghostThreadLocal.get();
	}

	public static User getUser() {
		return userThreadLocal.get();
	}

	public static void setReadOnlyOp(boolean readOnlyOp) {
		readOnlyOpThreadLocal.set(readOnlyOp);
	}

	public static boolean isReadOnlyOp() {
		return readOnlyOpThreadLocal.get();
	}

	public static void resetAll() {

		userThreadLocal.remove();
		organizationThreadLocal.remove();
		instanceThreadLocal.remove();
		ghostThreadLocal.remove();
		readOnlyOpThreadLocal.remove();
	}
}
