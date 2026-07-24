package com.syncari.core.security;

import com.syncari.core.model.misc.RoleConstants;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public abstract class Permissions {
	// Analytics-Dashboard permissions
	public static final String ANALYTICS = "ANALYTICS";

	// Reference Data permissions
	public static final String READ_REFERENCE_DATA = "READ_REFERENCE_DATA";
	public static final String WRITE_REFERENCE_DATA = "WRITE_REFERENCE_DATA";

	// Connector permissions
	public static final String WRITE_CONNECTOR = "WRITE_CONNECTOR";
	public static final String READ_CONNECTOR = "READ_CONNECTOR";
	public static final String TEST_CONNECTION = "TEST_CONNECTION";

	// Profile permissions
	public static final String WRITE_PROFILE = "WRITE_PROFILE";
	public static final String READ_PROFILE = "READ_PROFILE";

	// Tag permissions
	public static final String ASSIGN_TAG = "ASSIGN_TAG";
	public static final String REMOVE_TAG = "REMOVE_TAG";
	public static final String READ_TAG = "READ_TAG";

	// Sync studio permissions
	public static final String READ_STUDIO = "READ_STUDIO";
	public static final String WRITE_STUDIO = "WRITE_STUDIO";
	public static final String EXECUTE_REALTIME_PIPELINE = "EXECUTE_REALTIME_PIPELINE";
	
	// Data studio permissions
	public static final String READ_DATA_STUDIO = "READ_DATA_STUDIO";
	public static final String WRITE_DATA_STUDIO = "WRITE_DATA_STUDIO";

	// TxLog Permissions
	public static final String VIEW_TRANSACTIONS = "VIEW_TRANSACTIONS";

	// Datastore permission
	public static final String PROVISION_DATASTORE = "PROVISION_DATASTORE";
	public static final String READ_DATASTORE = "READ_DATASTORE";
	public static final String CREATE_DATASTORE = "CREATE_DATASTORE";
	public static final String UPDATE_DATASTORE = "UPDATE_DATASTORE";
	public static final String DELETE_DATASTORE = "DELETE_DATASTORE";
	public static final String ACTIVATE_DATASTORE = "ACTIVATE_DATASTORE";
	public static final String DEACTIVATE_DATASTORE = "DEACTIVATE_DATASTORE";

	// Role permissions
	public static final String ADD_ROLE = "ADD_ROLE";
	public static final String ADD_ROLE_EXP = "hasAuthority('ADD_ROLE')";
	public static final String EDIT_ROLE = "EDIT_ROLE";
	public static final String DELETE_ROLE = "DELETE_ROLE";
	public static final String LIST_ROLES = "LIST_ROLES";
	public static final String ADD_PRIV_TO_ROLE = "ADD_PRIV_TO_ROLE";
	public static final String REMOVE_PRIV_FROM_ROLE = "REMOVE_PRIV_FROM_ROLE";
	public static final String ADD_ROLE_TO_USR = "ADD_ROLE_TO_USR";
	public static final String REMOVE_ROLE_FROM_USR = "REMOVE_ROLE_FROM_USR";

	// Provisioning permissions
	@OrgAdminPermissions
	public static final String SUB_EDIT = "SUB_EDIT";
    @OrgAdminPermissions
	public static final String WRITE_BRAND = "WRITE_BRAND";
    @OrgAdminPermissions
    public static final String READ_BRAND = "READ_BRAND";
	@OrgAdminPermissions
	public static final String ADD_INSTANCE = "ADD_INSTANCE";
	@OrgAdminPermissions
	public static final String EDIT_INSTANCE = "EDIT_INSTANCE";
	@OrgAdminPermissions
	public static final String DELETE_INSTANCE = "DELETE_INSTANCE";
	public static final String INVITE_USER = "INVITE_USER";
	public static final String REINVITE_USER = "REINVITE_USER";
	public static final String LIST_USER = "LIST_USER";
	public static final String LIST_INSTANCE = "LIST_INSTANCE";
	public static final String LIST_INSTANCE_STATE = "LIST_INSTANCE_STATE";

	@OrgAdminPermissions
	public static final String REMOVE_USER = "REMOVE_USER";
	@OrgAdminPermissions
	public static final String DELETE_USR = "DELETE_USR";
	@OrgAdminPermissions
	public static final String DEACTIVATE_USER = "DEACTIVATE_USER";
	@OrgAdminPermissions
	public static final String ACTIVATE_USER = "ACTIVATE_USER";
	public static final String ENABLE_FEATURE = "ENABLE_FEATURE";
	public static final String DISABLE_FEATURE = "DISABLE_FEATURE";
	public static final String GET_FEATURE_STATUS = "GET_FEATURE_STATUS";
	public static final String PROVISION_TRIAL_ORG = "PROVISION_TRIAL_ORG";

	// Enable insights permission
	public static final String ENABLE_INSIGHTS = "ENABLE_INSIGHTS";


	//SSO Permission
	public static final String READ_SSO = "READ_SSO";
	public static final String WRITE_SSO = "WRITE_SSO";

	//Insights Permission
	public static final String READ_INSIGHTS = "READ_INSIGHTS";

	// Insights Dashboard Permission
	public static final String CREATE_DASHBOARD = "CREATE_DASHBOARD";
	public static final String PUBLISH_DASHBOARD = "PUBLISH_DASHBOARD";
	public static final String UPDATE_DASHBOARD = "UPDATE_DASHBOARD";
	public static final String DELETE_DASHBOARD = "DELETE_DASHBOARD";
	
	// Insights Dashboard Permission
	public static final String CREATE_DATACARD = "CREATE_DATACARD";
	public static final String VIEW_DATACARD = "VIEW_DATACARD";
	public static final String PUBLISH_DATACARD = "PUBLISH_DATACARD";
	public static final String UPDATE_DATACARD = "UPDATE_DATACARD";
	public static final String DELETE_DATACARD = "DELETE_DATACARD";

	// Dataset Permission
	public static final String CREATE_DATASET = "CREATE_DATASET";
	public static final String VIEW_DATASET = "VIEW_DATASET";
	public static final String PUBLISH_DATASET = "PUBLISH_DATASET";
	public static final String UPDATE_DATASET = "UPDATE_DATASET";
	public static final String DELETE_DATASET = "DELETE_DATASET";
	public static final String EXPORT_DATASET = "EXPORT_DATASET";
	public static final String DOWNLOAD_EXPORTED_DATASET = "DOWNLOAD_EXPORTED_DATASET";
	public static final String VIEW_EXPORT_JOBS = "VIEW_EXPORT_JOBS";
	public static final String CANCEL_EXPORT = "CANCEL_EXPORT";
	public static final String DELETE_EXPORT = "DELETE_EXPORT";
	public static final String SERVICE_CREDENTIAL = "SERVICE_CREDENTIAL";

	// Insights Sharing Permissions
	public static final String CREATE_ALLOWED_DOMAINS = "CREATE_ALLOWED_DOMAINS";
	public static final String READ_ALLOWED_DOMAINS = "READ_ALLOWED_DOMAINS";
	public static final String DELETE_ALLOWED_DOMAINS = "DELETE_ALLOWED_DOMAINS";
	public static final String SHARE_DASHBOARD = "SHARE_DASHBOARD";
	public static final String READ_ALL_SHARED_DASHBOARD = "READ_ALL_SHARED_DASHBOARD";
	public static final String READ_SHARED_DASHBOARD_DETAILS = "READ_SHARED_DASHBOARD_DETAILS";
	public static final String DELETE_SHARED_DASHBOARD_DETAILS = "DELETE_SHARED_DASHBOARD_DETAILS";
	public static final String UPDATE_SHARED_DASHBOARD_EXPIRY = "UPDATE_SHARED_DASHBOARD_EXPIRY";



	// Quickstart Permissions
	public static final String QUICKSTART_SHARE = "QS_SHARE";

	public static final String QUICKSTART_ORG_SHARE = "QS_ORG_SHARE";

	@SuperAdminPermissions
	public static final String QUICKSTART_PUBLISH = "QS_APPROVE";

	// Custom Actions Permissions
	public static final String ACTION_WRITE = "ACTION_WRITE";
	public static final String ACTION_READ = "ACTION_READ";
	public static final String ACTION_SHARE = "ACTION_SHARE";
	
	// Reference Data permissions
	public static final String READ_FILE_DATA = "READ_FILE_DATA";
	public static final String WRITE_FILE_DATA = "WRITE_FILE_DATA";
	public static final String DELETE_FILE_DATA = "DELETE_FILE_DATA";
	
	// Error Notification permissions
	public static final String READ_ERROR_NOTIFICATION_EMAIL = "READ_ERROR_NOTIFICATION_EMAIL";
	public static final String WRITE_ERROR_NOTIFICATION_EMAIL = "WRITE_ERROR_NOTIFICATION_EMAIL";
	public static final String READ_ERROR_NOTIFICATION_WEBHOOK = "READ_ERROR_NOTIFICATION_WEBHOOK";
	public static final String WRITE_ERROR_NOTIFICATION_WEBHOOK = "WRITE_ERROR_NOTIFICATION_WEBHOOK";
	
	// Abac permissions
	public static final String READ_ABAC = "READ_ABAC";
	public static final String WRITE_ABAC = "WRITE_ABAC";
    public static final String DELETE_ABAC = "DELETE_ABAC";

	// Debug Mode permissions
	@SuperAdminPermissions
	public static final String READ_DEBUG_MODE = "READ_DEBUG_MODE";
	@SuperAdminPermissions
	public static final String EDIT_DEBUG_MODE = "EDIT_DEBUG_MODE";

	@SuperAdminPermissions
	public static final String LIST_ORG = "LIST_ORG";
	@SuperAdminPermissions
	public static final String PROVISION_ORG = "PROVISION_ORG";
	@SuperAdminPermissions
	public static final String RESET_SUBSCRIPTION = "RESET_SUBSCRIPTION";
	@SuperAdminPermissions
	public static final String GHOST_LOGIN = "GHOST_LOGIN";

	//Custom Synapse approver
	@SuperAdminPermissions
	public static final String APPROVE_CUSTOM_SYNAPSE = "APPROVE_CUSTOM_SYNAPSE";

	// Data Fix Support Tool permissions
	@SuperAdminPermissions
	public static final String READ_DATA_FIX = "READ_DATA_FIX";
	@SuperAdminPermissions
	public static final String APPROVE_UPDATE_QUERY = "APPROVE_UPDATE_QUERY";
	@SuperAdminPermissions
	public static final String EXECUTE_UPDATE_QUERY = "EXECUTE_UPDATE_QUERY";

	public static List<String> allPermissions() {
		Predicate<Field> isPSF = f -> Modifier.isFinal(f.getModifiers()) && Modifier.isPublic(f.getModifiers())
				&& Modifier.isStatic(f.getModifiers());
		Function<Field, String> getConstantValue = f -> {
			try {
				return f.get(Permissions.class).toString();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		};
		return List.of(Permissions.class.getDeclaredFields()).stream().filter(isPSF).map(getConstantValue)
				.collect(Collectors.toList());

	}

	public static List<String> adminPermissions() {
		Predicate<Field> isPSF = f -> Modifier.isFinal(f.getModifiers()) && Modifier.isPublic(f.getModifiers())
				&& !f.isAnnotationPresent(SuperAdminPermissions.class) && Modifier.isStatic(f.getModifiers());
		Function<Field, String> getConstantValue = f -> {
			try {
				return f.get(Permissions.class).toString();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		};
		return List.of(Permissions.class.getDeclaredFields()).stream().filter(isPSF).map(getConstantValue)
				.collect(Collectors.toList());
	}
	
	public static List<String> instanceAdminPermissions() {
		Predicate<Field> isPSF = f -> Modifier.isFinal(f.getModifiers()) && Modifier.isPublic(f.getModifiers())
				&& !f.isAnnotationPresent(SuperAdminPermissions.class)
				&& !f.isAnnotationPresent(OrgAdminPermissions.class)
				&& Modifier.isStatic(f.getModifiers());
		Function<Field, String> getConstantValue = f -> {
			try {
				return f.get(Permissions.class).toString();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		};
		return List.of(Permissions.class.getDeclaredFields()).stream().filter(isPSF).map(getConstantValue)
				.collect(Collectors.toList());
	}

	public static List<String> syncManagerPermissions() {
		List<String> list = new ArrayList<String>();
		list.add(ANALYTICS);
		list.add(VIEW_TRANSACTIONS);
		list.add(GET_FEATURE_STATUS);
		list.add(READ_INSIGHTS);
		list.add(VIEW_DATASET);
		list.add(READ_FILE_DATA);
		list.add(WRITE_FILE_DATA);
		list.add(QUICKSTART_SHARE);
		list.add(LIST_INSTANCE_STATE);
		list.addAll(getConnectorPermissions());
		list.addAll(getProfilePermissions());
		list.addAll(getTagPermissions());
		list.addAll(getRefDataPermissions());
		list.addAll(getStudioPermissions());
		list.addAll(getErrorNotificationPermissions());
		return list;
	}

	private static Collection<? extends String> getErrorNotificationPermissions() {
		return List.of(READ_ERROR_NOTIFICATION_EMAIL, WRITE_ERROR_NOTIFICATION_EMAIL, READ_ERROR_NOTIFICATION_WEBHOOK, WRITE_ERROR_NOTIFICATION_WEBHOOK);
	}

	public static List<String> viewerPermissions() {
		List<String> list = new ArrayList<String>();
		list.add(ANALYTICS);
		list.add(VIEW_TRANSACTIONS);
		list.add(READ_REFERENCE_DATA);
		list.add(READ_CONNECTOR);
		list.add(READ_STUDIO);
		list.add(READ_DATA_STUDIO);
		list.add(READ_TAG);
		list.add(GET_FEATURE_STATUS);
		list.add(READ_INSIGHTS);
		list.add(VIEW_DATASET);
		list.add(READ_FILE_DATA);
		list.add(READ_ERROR_NOTIFICATION_EMAIL);
		list.add(READ_ERROR_NOTIFICATION_WEBHOOK);
		list.add(QUICKSTART_SHARE);
		list.add(LIST_INSTANCE_STATE);
		list.addAll(getProfilePermissions());
		list.add(READ_ABAC);
		return list;
	}

	public static List<String> dashboardAuthorPermissions() {
		List<String> dashboardAuthorPermissions = new ArrayList<>(viewerPermissions());
		dashboardAuthorPermissions.addAll(getAllDashboardPermissions());
		return dashboardAuthorPermissions;
	}

	public static List<String> dashboardlightViewerPermissions() {
		List<String> dashboardlightViewerPermissions = new ArrayList<>(getProfilePermissions());
		dashboardlightViewerPermissions.addAll(getAllLightDashboardViewerPermissions());
		return dashboardlightViewerPermissions;
	}


	public static List<String> synapseApproverPermissions() {
		List<String> synapseApproverPermissions = new ArrayList<>(viewerPermissions());
		synapseApproverPermissions.addAll(getAllSynapseApproverPermissions());
		return synapseApproverPermissions;
	}


	public static List<String> ghostPermissions() {
		List<String> viewerPermissions = viewerPermissions();
		viewerPermissions.add(LIST_INSTANCE);
		viewerPermissions.add(LIST_USER);
		viewerPermissions.add(Permissions.LIST_ORG);
		viewerPermissions.add(Permissions.LIST_ROLES);
		viewerPermissions.add(Permissions.GHOST_LOGIN);
		// Data Fix Support Tool permissions
		viewerPermissions.add(READ_DATA_FIX);
		viewerPermissions.add(APPROVE_UPDATE_QUERY);
		viewerPermissions.add(EXECUTE_UPDATE_QUERY);
		return viewerPermissions;
	}
	
	public static List<String> getPermissionForRole(String role, boolean isSuperAdmin){
		if (isSuperAdmin){
			return allPermissions();
		}else{
			switch (role){
				case RoleConstants.ORG_ADMIN:
					return adminPermissions();
				case RoleConstants.SYNC_MANAGER:
					return syncManagerPermissions();
				case RoleConstants.VIEWER:
					return viewerPermissions();
				case RoleConstants.GHOST:
					return ghostPermissions();
				case RoleConstants.INSTANCE_ADMIN:
					return instanceAdminPermissions();
				default:
					throw new RuntimeException(String.format("Role %s does not exists.", role));
			}
		}

	}

	private static List<String> getRefDataPermissions() {
		return List.of(READ_REFERENCE_DATA, WRITE_REFERENCE_DATA);
	}

	public static List<String> getProfilePermissions() {
		return List.of(READ_PROFILE, WRITE_PROFILE);
	}

	private static List<String> getConnectorPermissions() {
		return List.of(READ_CONNECTOR, WRITE_CONNECTOR, TEST_CONNECTION);
	}

	private static List<String> getTagPermissions() {
		return List.of(ASSIGN_TAG, REMOVE_TAG, READ_TAG);
	}

	private static List<String> getStudioPermissions() {
		return List.of(READ_STUDIO, WRITE_STUDIO, READ_DATA_STUDIO);
	}

	protected static List<String> getAllDashboardPermissions() {
		return List.of(
				READ_INSIGHTS,
				CREATE_DATASET, PUBLISH_DATASET, UPDATE_DATASET, DELETE_DATASET, VIEW_DATASET, // Dataset permissions
				CREATE_DATACARD, VIEW_DATACARD, PUBLISH_DATACARD, UPDATE_DATACARD, DELETE_DATACARD,
				CREATE_DASHBOARD, PUBLISH_DASHBOARD, UPDATE_DASHBOARD, DELETE_DASHBOARD,READ_ALL_SHARED_DASHBOARD,
				CREATE_ALLOWED_DOMAINS,READ_ALLOWED_DOMAINS,DELETE_ALLOWED_DOMAINS,READ_ALL_SHARED_DASHBOARD,
				DELETE_SHARED_DASHBOARD_DETAILS,SHARE_DASHBOARD
		);
	}

	protected static List<String> getAllLightDashboardViewerPermissions() {
		return List.of(
				READ_ALL_SHARED_DASHBOARD
		);
	}

	protected static List<String> getAllSynapseApproverPermissions() {
		return List.of(
				APPROVE_CUSTOM_SYNAPSE
		);
	}

	
	public static List<String> getBasePermissions() {
		return List.of(READ_PROFILE, WRITE_PROFILE, SUB_EDIT, GET_FEATURE_STATUS);
	}
	
	public static List<String> getCustomRoleExcludedPermissions() {
		return List.of(ENABLE_FEATURE, DISABLE_FEATURE, ADD_ROLE_EXP, GET_FEATURE_STATUS, PROVISION_TRIAL_ORG);
	}
	
}
