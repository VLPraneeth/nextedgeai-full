//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
const ARCADE_PREFIX = '/arcade';

export const ARCADE_V1_PREFIX = `${ARCADE_PREFIX}/api/v1`;

export const getArcadeUrl = (url: string) => `${ARCADE_PREFIX}${url}`;
const getProxyUrl = (url: string) => `${url}`;

// prettier-ignore
const DataUrlConstants = {
  LOGIN                : getArcadeUrl('/api/v1/authenticate'),
  LOGOUT               : getArcadeUrl('/logout'),

  // Connector
  CONNECTOR            : getArcadeUrl('/api/v1/connector/'),
  GET_CONNECTOR        : getArcadeUrl('/api/v1/connector/{connectorId}'),
  UPDATE_CONNECTOR     : getArcadeUrl('/api/v1/connector/{connectorId}'),
  ACTIVATE_CONNECTOR   : getArcadeUrl('/api/v1/connector/activate/{name}'),
  DEACTIVATE_CONNECTOR : getArcadeUrl('/api/v1/connector/deactivate/{name}'),
  TEST_CONNECTOR       : getArcadeUrl('/api/v1/connector/test/{name}'),
  DELETE_CONNECTOR     : getArcadeUrl('/api/v1/connector/{name}'),
  EDIT_CONNECTOR       : getArcadeUrl('/api/v1/connector/{name}'),
  APPROVE_CONNECTOR    : getArcadeUrl('/api/v1/connector/approve/{name}'),
  DISCARD_CONNECTOR    : getArcadeUrl('/api/v1/connector/discard/{name}'),
  CONNECTORS_METADATA  : getArcadeUrl('/api/v1/connector/describe'),
  SET_CONNECTOR_SETTING: getArcadeUrl('/api/v1/connector/setting/{connectorId}'),

  // Oauth
  OAUTH_INITIATE       : getArcadeUrl('/api/v1/oauth/initiate/{connectorId}'),

  // Application
  PHONE_HOME           : getArcadeUrl('/api/v1/application/phoneHome'),
  PHONE_HOME_V2        : getProxyUrl('/phoneHome'),
  SUPPORT_LINK         : getProxyUrl('/support/{helpPath}'),

  // Subscription
  SUBSCRIPTION         : getArcadeUrl('/api/v1/organization/'),
  DELETE_SUBSCRIPTION  : getArcadeUrl('/api/v1/organization/{orgId}'),

  // Branding
  BRAND                : getArcadeUrl('/api/v1/brand'),
  BRAND_RESET          : getArcadeUrl('/api/v1/brand/reset'),
  ORG_LOGO             : getArcadeUrl('/api/v1/brand/logo'),
  ORG_LOGOSQUARE       : getArcadeUrl('/api/v1/brand/logoSquare'),

  /* GET, POST */
  ORGANIZATION_SSO     : getArcadeUrl('/api/v1/sso/{orgId}'),

  // User
  RESET_PASSWORD       : getArcadeUrl('/api/v1/user/resetpassword/{userId}'),
  SET_PASSWORD         : getArcadeUrl('/api/v1/user/setpassword/{invitationId}'),
  FORGOT_PASSWORD      : getArcadeUrl('/api/v1/user/forgotPassword'),
  USER                 : getArcadeUrl('/api/v1/organization/users'),
  INVITE_USER          : getArcadeUrl('/api/v1/organization/user'),
  RESEND_INVITE_USER   : getArcadeUrl('/api/v1/organization/user/reinvite/{userId}'),
  PROFILE_PHOTO        : getArcadeUrl('/api/v1/user/photo'),

  // PATCH method
  UPDATE_USER_ROLES    : getArcadeUrl('/api/v1/organization/user/{userId}/roles'),

  // POST method
  DEACTIVATE_USER      : getArcadeUrl('/api/v1/organization/user/{userId}/deactivate'),
  ACTIVATE_USER        : getArcadeUrl('/api/v1/organization/user/{userId}/activate'),
  REMOVE_USER          : getArcadeUrl('/api/v1/organization/user/{userId}/remove'),

  // DELETE method
  DELETE_USER          : getArcadeUrl('/api/v1/organization/user/{userId}'),

  ADD_ROLE_TO_USER     : getArcadeUrl('/api/v1/authz/user/{userId}/role/{roleId}'),
  PROFILE              : getArcadeUrl('/api/v1/user'),
  BASE_PREFERENCE      : getArcadeUrl('/api/v1/user/preference'),
  PREFERENCE           : getArcadeUrl('/api/v1/user/preference/{key}'),
  CUSTOM_PREFERENCE    : getArcadeUrl('/api/v1/user/preference/customPreference'),

  GET_ALL_ROLES        : getArcadeUrl('/api/v1/authz/roles'),
  GET_ALL_ROLES_ALL_INSTANCES : getArcadeUrl('/api/v1/authz/roles/all'),
  UPDATE_PASSWORD      : getArcadeUrl('/api/v1/user/updatepassword/{userId}'),
  ERROR_CATALAG        : getArcadeUrl('/api/v1/user/errorCatalogMetaData'),
  ERROR_NOTIFICATION   : getArcadeUrl('/api/v1/user/preference/errorNotification'),
  REQUEST_GHOST_ACCESS : getArcadeUrl('/api/v1/specter/ghost'),
  REVOKE_GHOST_ACCESS  : getArcadeUrl('/api/v1/specter/revokeGhost'),
  GET_GHOST_ACCESS     : getArcadeUrl('/api/v1/specter/ghostAccess'),
  GET_SYNCARI_DEV_USERS: getArcadeUrl('/api/v1/specter/syncariDevUsers'),


  PREFERENCE_DATA_STUDIO  : getArcadeUrl('/api/v1/user/preference/dataStudio/columns/{entityId}'),

  PREFERENCE_SCHEMA_STUDIO_ENTITY_COLUMNS: getArcadeUrl('/api/v1/user/preference/schemaStudio/entityColumns'),
  PREFERENCE_SCHEMA_STUDIO_FIELD_COLUMNS: getArcadeUrl('/api/v1/user/preference/schemaStudio/fieldColumns'),

  PREFERENCE_SYNC_STUDIO_FIELD_FILTERS: getArcadeUrl('/api/v1/user/preference/syncStudio/fieldFilters/{entityId}'),
  PREFERENCE_SYNC_STUDIO_HIDDEN_FIELDS: getArcadeUrl('/api/v1/user/preference/syncStudio/hiddenFields/{entityId}'),
  PREFERENCE_SYNC_STUDIO_PIPELINE_VIEWPORTS: getArcadeUrl('/api/v1/user/preference/syncStudio/pipelineViewports/{pipelineId}'),

  // Instance
  INSTANCE              : getArcadeUrl('/api/v1/organization/instance'),
  INSTANCE_STATE        : getArcadeUrl('/api/v1/organization/instanceState/{instanceId}'),
  USER_INSTANCES        : getArcadeUrl('/api/v1/user/instances'),
  SWITCH_INSTANCE       : getArcadeUrl('/api/v1/user/switch/instance/{instanceId}'),
  EXTEND_TRIAL_INSTANCE : getArcadeUrl('/api/v1/organization/extendTrial'),
  COPY_INSTANCE         : getArcadeUrl('/api/v1/specter/instance/copy/{sourceInstanceId}/{destinationInstanceId}'),

  // use DELETE method
  DELETE_INSTANCE      : getArcadeUrl('/api/v1/organization/instance/{instanceId}'),

  // Entitites
  ENTITIES             : getArcadeUrl('/api/v1/schema'),
  ENTITY               : getArcadeUrl('/api/v1/schema/entity/{syncariEntityId}'),
  ENTITY_FOR_VERSION   : getArcadeUrl('/api/v1/schema/entity/{syncariEntityId}/{graphVersion}'),
  ENTITY_FOR_VERSION_QS: getArcadeUrl('/api/v1/schema/entity/{syncariEntityId}/{graphVersion}/quickstart'),
  SYNAPSE_ENTITIES     : getArcadeUrl('/api/v1/schema/{connectorId}?detailed={detailed}'),
  REFRESH_SCHEMA       : getArcadeUrl('/api/v1/schema/refresh/{connectorId}'),
  ENTITY_MAPPING       : getArcadeUrl('/api/v1/schema/entityMapping/{connectorId}'),
  FIELD_MAPPING        : getArcadeUrl('/api/v1/schema/fieldMapping/{syncariEntityId}/{synapseEntityId}'),
  CREATE_FIELD_MAPPING : getArcadeUrl('/api/v1/schema/fieldMapping/{connectorId}'),

  // Dashboard V2
  GET_DASHBOARDS       : getArcadeUrl('/api/v2/dashboard'),
  GET_DASHBOARD        : getArcadeUrl('/api/v2/dashboard/{dashboardName}'),
  GET_WIDGET           : getArcadeUrl('/api/v2/dashboard/{dashboardName}/widget/{widgetName}'),

  // Pipeline functions
  ENTITY_FUNCTIONS     : getArcadeUrl('/api/v1/functions/entity/{entityId}'),
  FIELD_FUNCTIONS      : getArcadeUrl('/api/v1/functions/field/{graphId}'),

  // Pipeline actions
  ENTITY_ACTIONS       : getArcadeUrl('/api/v1/actions/entity/{entityId}'),
  FIELD_ACTIONS        : getArcadeUrl('/api/v1/actions/field/{fieldId}'),

  // Data Score
  /* GET */
  DATASCORE_FOR_ENTITY : getArcadeUrl('/api/v1/dfi/entity/{entityId}'),

  // DataQuality
  DFI_RULES_FOR_ENTITY : getArcadeUrl('/api/v1/dfi/rules/entity/{entityId}'),
  PUBLISH_DFI_RULES_FOR_ENTITY : getArcadeUrl('/api/v1/dfi/rules/entity/{entityId}/publish'),
  IS_CUSTOM_RULE_ASSIGNMENT_EXISTS : getArcadeUrl('/api/v1/dfi/rules/isCustomRuleAssignmentExists'),
  // Pipeline - Sync Studio
  PIPELINE      : getArcadeUrl('/api/v1/pipeline'),

  // Entity Pipeline
  ENTITY_PIPELINE         : getArcadeUrl('/api/v1/pipeline/entityPipeline/{entityId}'),
  ENTITY_PIPELINE_DRAFT   : getArcadeUrl('/api/v1/pipeline/entityPipeline/{entityId}/NEW'),
  ENTITY_PIPELINE_APPROVED: getArcadeUrl('/api/v1/pipeline/entityPipeline/{entityId}/APPROVED'),
  APPROVE_ENTITY_PIPELINE : getArcadeUrl('/api/v1/pipeline/approveEntityPipeline/{entityId}'),
  DISCARD_ENTITY_PIPELINE : getArcadeUrl('/api/v1/pipeline/discardEntityPipeline/{entityId}'),
  CONNECTOR_ENTITIES      : getArcadeUrl('/api/v1/connectorEntities/{entityId}'),
  VALIDATE_ENTITY_PIPELINE: getArcadeUrl('/api/v1/pipeline/entityPipeline/{entityId}/validate'),
  FIELD_DRAFT_SUMMARY     : getArcadeUrl('/api/v1/pipeline/entityPipeline/{entityId}/fieldDraftSummary'),
  DELETE_ENTITY_PIPELINE  : getArcadeUrl('/api/v1/pipeline/deleteEntityPipeline/{entityId}'),
  CREATE_ENTITY_PIPELINE  : getArcadeUrl('/api/v1/pipeline/createEntityPipeline/{entityId}'),
  STOP_ENTITY_PIPELINE    : getArcadeUrl('/api/v1/pipeline/entityPipeline/stop/{entityId}'),
  START_ENTITY_PIPELINE   : getArcadeUrl('/api/v1/pipeline/entityPipeline/start/{entityId}'),
  TEST_ENTITY_PIPELINE    : getArcadeUrl('/api/v1/pipeline/entityPipeline/test/{entityId}'),
  RESYNC_ENTITY_SOURCE    : getArcadeUrl('/api/v1/pipeline/entityPipeline/resyncEntity/{entityId}'),
  CANCEL_RESYNC_ENTITY    : getArcadeUrl('/api/v1/pipeline/cancelResyncEntity/{entityId}'),
  GET_RESYNC_DETAILS      : getArcadeUrl('/api/v1/pipeline/entityPipeline/resyncStatus/{entityId}'),
  ENTITY_PIPELINE_STATUS  : getArcadeUrl('/api/v1/pipeline/entityPipeline/status/{entityId}'),
  ENTITY_PIPELINE_STATUSES: getArcadeUrl('/api/v1/pipeline/entityPipeline/status'),
  ENTITY_PIPELINE_ERROR   : getArcadeUrl('/api/v1/pipeline/entityPipeline/errorSummary/{entityId}'),
  ENTITY_AUTO_MAP         : getArcadeUrl('/api/v1/pipeline/entityPipeline/{syncariEntityId}/automap/{sourceEntityId}/{mapperType}'),

  REALTIME_PIPELINE : getArcadeUrl('/api/v1/user/realtimeIpWhitelist'),

  // Field Pipeline
  FIELD_PIPELINE                : getArcadeUrl('/api/v1/pipeline/fieldPipeline/{fieldId}'),
  FIELD_PIPELINE_DRAFT          : getArcadeUrl('/api/v1/pipeline/fieldPipeline/{fieldId}/NEW'),
  FIELD_PIPELINE_APPROVED       : getArcadeUrl('/api/v1/pipeline/fieldPipeline/{fieldId}/APPROVED'),
  APPROVE_FIELD_PIPELINE        : getArcadeUrl('/api/v1/pipeline/approveFieldPipeline/{fieldId}'),
  DISCARD_FIELD_PIPELINE        : getArcadeUrl('/api/v1/pipeline/discardFieldPipeline/{fieldId}'),
  ATTRIBUTE_NODES               : getArcadeUrl('/api/v1/attributeNodes/{attributeId}'),
  VALIDATE_FIELD_PIPELINE       : getArcadeUrl('/api/v1/pipeline/fieldPipeline/{fieldId}/validate'),
  DELETE_FIELD_PIPELINE         : getArcadeUrl('/api/v1/pipeline/deleteFieldPipeline/{fieldId}'),
  CREATE_FIELD_PIPELINE         : getArcadeUrl('/api/v1/pipeline/createFieldPipeline/{fieldId}'),
  MARK_FIELD_PIPELINE_READY     : getArcadeUrl('/api/v1/pipeline/markFieldPipelineReady/{fieldId}'),
  MARK_FIELD_PIPELINE_NOT_READY : getArcadeUrl('/api/v1/pipeline/markFieldPipelineNotReady/{fieldId}'),

  ASYNC_NODE_CONFIG             : getArcadeUrl('/api/v1/nodeConfig/{nodeId}'),
  NODE_CONFIG_ADDITIONAL_CONFIG : getArcadeUrl('/api/v1/nodeConfig/loadAdditionalConfig'),

  // Tests
  FIELD_PIPELINE_TESTS           : getArcadeUrl('/api/v1/test/fieldPipeline/{fieldPipelineId}'),
  FIELD_PIPELINE_PICKLIST_VALUES : getArcadeUrl('/api/v1/test/fieldPipeline/{fieldPipelineId}/nodeId/{nodeId}/fields/picklistValues'),
  FIELD_PIPELINE_RUN_TESTS       : getArcadeUrl('/api/v1/test/fieldPipeline/{fieldPipelineId}/run'),
  FIELD_PIPELINE_RUN_TEST        : getArcadeUrl('/api/v1/test/fieldPipeline/{fieldPipelineId}/run/{runId}'),
  FIELD_PIPELINE_TEST            : getArcadeUrl('/api/v1/test/fieldPipeline/{fieldPipelineId}/testId/{testId}'),

  ENTITY_PIPELINE_TESTS           : getArcadeUrl('/api/v1/test/entityPipeline/{fieldPipelineId}'),
  ENTITY_PIPELINE_PICKLIST_VALUES : getArcadeUrl('/api/v1/test/entityPipeline/{fieldPipelineId}/nodeId/{nodeId}/fields/picklistValues'),
  ENTITY_PIPELINE_RUN_TESTS       : getArcadeUrl('/api/v1/test/entityPipeline/{fieldPipelineId}/run'),
  ENTITY_PIPELINE_RUN_TEST        : getArcadeUrl('/api/v1/test/entityPipeline/{fieldPipelineId}/run/{runId}'),
  ENTITY_PIPELINE_TEST            : getArcadeUrl('/api/v1/test/entityPipeline/{fieldPipelineId}/testId/{testId}'),

  LIVE_TEST_RUNS                  : getArcadeUrl('/api/v1/test/entityPipeline/{graphId}/test'),
  LIVE_TEST_RUN                   : getArcadeUrl('/api/v1/test/entityPipeline/{graphId}/test/{runId}'),

  // Reference data
  REFERENCE_DATA       : getArcadeUrl('/api/v1/referenceData'),
  PREVIEW_REF_DATA     : getArcadeUrl('/api/v1/referenceData/preview/{refMetaId}'),
  ACTIVATE_REF_DATA    : getArcadeUrl('/api/v1/referenceData/activate/{refMetaId}'),
  DEACTIVATE_REF_DATA  : getArcadeUrl('/api/v1/referenceData/deactivate/{refMetaId}'),
  DELETE_REF_DATA      : getArcadeUrl('/api/v1/referenceData/{refMetaId}'),
  DOWNLOAD_REF_DATA    : getArcadeUrl('/api/v1/referenceData/download/{refMetaId}'),

  // ABAC
  ABAC_ATTRIBUTE: getArcadeUrl('/api/v1/abac/attribute'),
  ABAC_ATTRIBUTE_ITEM: getArcadeUrl('/api/v1/abac/attribute/{id}'),
  ABAC_ATTRIBUTE_USER: getArcadeUrl('/api/v1/abac/attribute/user'),
  ABAC_ATTRIBUTE_SUPPORTED_DATATYPES: getArcadeUrl('/api/v1/abac/attribute/supportedDataTypes'),
  ABAC_ATTRIBUTE_VALUE: getArcadeUrl('/api/v1/abac/attribute_value'),
  ABAC_ATTRIBUTE_VALUE_ITEM: getArcadeUrl('/api/v1/abac/attribute_value/{id}'),
  ABAC_POLICY: getArcadeUrl('/api/v1/abac/policy'),
  ABAC_POLICY_ITEM: getArcadeUrl('/api/v1/abac/policy/{id}'),
  ABAC_RESOURCE: getArcadeUrl('/api/v1/abac/resource/{type}'),
  ABAC_RESOURCE_VALUES: getArcadeUrl('/api/v1/abac/resource/{type}/attribute_value'),
  ABAC_RESOURCE_ATTRIBUTES: getArcadeUrl('/api/v1/abac/resource/{type}/{id}/attribute'),
  ABAC_RESOURCE_TOKENS: getArcadeUrl('/api/v1/abac/resource/{type}/{id}/token'),
  ABAC_RESOURCE_TYPE: getArcadeUrl('/api/v1/abac/resource_type'),

  // Tokens
  // GET
  TOKENS_FOR_NODE      : getArcadeUrl('/api/v1/token/{nodeId}'),

  // Tags
  TAG                  : getArcadeUrl('/api/v1/tag/{partialName}'),
  ADD_TAG              : getArcadeUrl('/api/v1/tag/assign'),
  REMOVE_TAG           : getArcadeUrl('/api/v1/tag/remove'),

  TRANSACTIONS_BASE       : getArcadeUrl('/api/v1/transaction/'),
  TRANSACTION_KPIS        : getArcadeUrl('/api/v1/transaction/kpis'),
  TRANSACTIONS_BY_MESSAGE : getArcadeUrl('/api/v1/transaction/errors/{syncCycleId}/{nodeId}'),

  // Notifications
  GET_NOTIFICATIONS             : getArcadeUrl('/api/v1/notification'),
  GET_NOTIFICATIONS_UNREAD_COUNT: getArcadeUrl('/api/v1/notification/unreadcount'),
  ARCHIVE_ALL_NOTIFICATIONS     : getArcadeUrl('/api/v1/notification/archiveAll'),
  MARK_ALL_NOTIFICATIONS_AS_READ: getArcadeUrl('/api/v1/notification/readAll'),
  ARCHIVE_NOTIFICATIONS          : getArcadeUrl('/api/v1/notification/archive'),
  MARK_NOTIFICATIONS_AS_READ     : getArcadeUrl('/api/v1/notification/read'),
  MARK_NOTIFICATIONS_AS_UNREAD   : getArcadeUrl('/api/v1/notification/unread'),

  // Logs
  GET_SYNC_ERRORS       : getArcadeUrl('/api/v1/report/syncErrors/{startDate}/{endDate}'),
  GET_SYNC_ERRORS_BY_MESSAGE: getArcadeUrl('/api/v1/report/syncErrors/{syncCycleId}/{nodeId}'),
  DOWNLOAD_SYNC_ERRORS       : getArcadeUrl('/api/v1/report/syncErrors/download'),

  GET_PICKLIST_VALUES   : getArcadeUrl('/api/v1/picklist/values'),

  // Specter
  SET_OAUTH             : getArcadeUrl('/api/v1/specter/setOauthConfig/{fromSyncariId}/{toSyncariId}'),
  GHOST_LOGIN           : getArcadeUrl('/api/v1/specter/switch/instance/{syncariId}'),

  SERVICE_CREDENTIAL      : getArcadeUrl('/api/v1/service/credential'),
  SERVICE_CREDENTIAL_ITEM : getArcadeUrl('/api/v1/service/credential/{credentialId}'),

  // Schema Studio
  /** GET */
  GET_SCHEMA_FOR_CONNECTOR: getArcadeUrl('/api/v1/studio/schema/{connectorId}'),

  CREATE_ENTITY           : getArcadeUrl('/api/v1/studio/schema/entity'),
  UPDATE_ENTITY           : getArcadeUrl('/api/v1/studio/schema/entity/{entityId}'),
  CLONE_ENTITY            : getArcadeUrl('/api/v1/pipeline/entityPipeline/{entityId}/clone'),
  CREATE_FIELD            : getArcadeUrl('/api/v1/studio/schema/entity/{entityId}/attribute'),
  UPDATE_FIELD            : getArcadeUrl('/api/v1/studio/schema/entity/{entityId}/attribute/{fieldId}'),
  ENTITY_DETAIL           : getArcadeUrl('/api/v1/studio/schema/entityDetail/{entityId}'),

  /** GET */
  GET_FIELDS_FOR_ENTITY   : getArcadeUrl('/api/v1/studio/schema/entity/{entityId}'),
  /** POST */
  CREATE_DRAFT_FOR_ENTITY : getArcadeUrl('/api/v1/studio/schema/entityDraft/{entityId}'),
  /** POST */
  PUBLISH_ENTITY_SCHEMA   : getArcadeUrl('/api/v1/studio/schema/approveEntity/{entityId}'),
  /** DELETE */
  DISCARD_ENTITY_SCHEMA   : getArcadeUrl('/api/v1/studio/schema/discardEntityDraft/{entityId}'),

  // Data Studio
  DATA_STUDIO_ROOT        : getArcadeUrl('/api/v1/studio/data'),
  /** GET */
  GET_ENTITY_FILTERS_LIST : getArcadeUrl('/api/v1/studio/data/filters'),
  GET_ENTITY_RECORDS_COUNT: getArcadeUrl('/api/v1/studio/data/meta'),
  /** POST */
  GET_ENTITY_RECORDS_LIST : getArcadeUrl('/api/v1/studio/data/entity/{entityId}/records'),
  CREATE_ENTITY_FILTER    : getArcadeUrl('/api/v1/studio/data/filters'),
  /** PUT */
  UPDATE_ENTITY_FILTER    : getArcadeUrl('/api/v1/studio/data/filters/{filterId}'),
  /** DELETE */
  DELETE_ENTITY_FILTER    : getArcadeUrl('/api/v1/studio/data/filters/{filterId}'),
  /** PATCH */
  BOOKMARK_ENTITY_FILTER  : getArcadeUrl('/api/v1/studio/data/filters/{filterId}'),
  /** GET */
  GET_RECORD_DATA         : getArcadeUrl('/api/v1/studio/data/entity/{entityId}/records/{recordId}'),
  /** GET */
  GET_RECORD_DOCUMENT     : getArcadeUrl('/api/v1/studio/data/entity/{entityId}/records/downloadFile/{recordId}'),
  /** POST */
  CREATE_RECORD_DATA      : getArcadeUrl('/api/v1/studio/data/entity/{entityId}/record'),
  /** PUT */
  UPDATE_RECORD_DATA      : getArcadeUrl('/api/v1/studio/data/entity/{entityId}/records/{recordId}'),
  /** DELETE */
  DELETE_RECORD_DATA      : getArcadeUrl('/api/v1/studio/data/entity/{entityId}/records/{recordId}'),
  DELETE_ENTITY           : getArcadeUrl('/api/v1/studio/data/entity/{entityId}'),
  /** DOWNLOAD */
  DOWNLOAD_ENTITY_RECORDS_LIST : getArcadeUrl('/api/v1/studio/data/entity/{entityId}/records/download'),
  DOWNLOAD_TXN_RECORDS_LIST : getArcadeUrl('/api/v1/transaction/download'),

  // Data Studio Batch
  DATA_STUDIO_BATCH                : getArcadeUrl('/api/v1/studio/data/batch/{batchId}'),
  DATA_STUDIO_BATCH_CANCEL         : getArcadeUrl('/api/v1/studio/data/batch/{batchId}/cancel'),
  DATA_STUDIO_ENTITY_BATCHES       : getArcadeUrl('/api/v1/studio/data/batch/entity/{entityId}'),
  DATA_STUDIO_RECORDS_BATCH        : getArcadeUrl('/api/v1/studio/data/batch/entity/{entityId}/records'),
  DATA_STUDIO_RECORDS_BATCH_DELETE : getArcadeUrl('/api/v1/studio/data/batch/entity/{entityId}/records/delete'),

  // Quick Start
  QUICK_START_AUTHOR_LIST          : getArcadeUrl('/api/v1/quickstart/author/list'),
  QUICK_START_AUTHOR_CONFIG        : getArcadeUrl('/api/v1/quickstart/author/config'),
  QUICK_START_DYNAMIC_STEP         : getArcadeUrl('/api/v1/quickstart/author/dynamicStep/{stepNumber}'),
  QUICK_START_BY_ID                : getArcadeUrl('/api/v1/quickstart/author/{quickStartId}'),
  QUICK_START_CREATE_QUICK_START   : getArcadeUrl('/api/v1/quickstart'),
  QUICK_START_QUICK_START_ICON     : getArcadeUrl('/api/v1/quickstart/icon/{quickStartId}/{status}'),
  QUICK_START_CREATE_DRAFT         : getArcadeUrl('/api/v1/quickstart/author/createDraft/{quickStartId}'),
  QUICK_START_DISCARD_DRAFT        : getArcadeUrl('/api/v1/quickstart/author/discardDraft/{quickStartId}'),
  QUICK_START_APPROVE_DRAFT        : getArcadeUrl('/api/v1/quickstart/author/approveDraft/{quickStartId}'),
  QUICK_START_GET_DRAFT            : getArcadeUrl('/api/v1/quickstart/author/{quickStartId}/draft'),
  QUICK_START_GET_APPROVED         : getArcadeUrl('/api/v1/quickstart/author/{quickStartId}/approved'),
  QUICK_START_MARKETPLACE_LIST     : getArcadeUrl('/api/v1/quickstart/install/marketplace'),
  QUICK_START_SHARED_LIST          : getArcadeUrl('/api/v1/quickstart/install/shared'),
  QUICK_START_GET_INSTALL          : getArcadeUrl('/api/v1/quickstart/install/{quickStartId}'),
  QUICK_START_INSTALL_DYNAMIC_STEP : getArcadeUrl('/api/v1/quickstart/install/dynamicStep/{stepNumber}'),
  QUICK_START_INSTALL              : getArcadeUrl('/api/v1/quickstart/install/{quickStartId}'),
  QUICK_START_INSTALL_CANCEL       : getArcadeUrl('/api/v1/quickstart/install/{quickStartId}/cancel'),
  QUICK_START_AUTHOR_INSTANCES     : getArcadeUrl('/api/v1/quickstart/author/instances'),
  QUICK_START_AUTHOR_PUBLISH       : getArcadeUrl('/api/v1/quickstart/author/{quickStartId}/publish'),

  // Quick Start Legacy
  QUICK_STARTS_LEGACY       : getArcadeUrl('/api/v1/quickstart/legacy/list'),
  QUICK_START_LEGACY_DYNAMIC_STEPS  : getArcadeUrl('/api/v1/quickstart/legacy/{quickStartName}/dynamicSteps/{stepNumber}'),
  QUICK_START_LEGACY_EXECUTE        : getArcadeUrl('/api/v1/quickstart/legacy/execute/{quickStartName}'),
  QUICK_START_LEGACY_HISTORY        : getArcadeUrl('/api/v1/quickstart/legacy/history/{quickStartName}'),

  // Skull endpoints
  GET_METADATA_VALUES        : getArcadeUrl('/api/v1/metadata/values'),

  // Entity Pipeline Fragment
  ENTITY_PIPELINE_FRAGMENT      : getArcadeUrl('/api/v1/fragment/entityPipeline'),
  ENTITY_PIPELINE_FRAGMENT_ITEM : getArcadeUrl('/api/v1/fragment/entityPipeline/{fragmentId}'),
  ENTITY_PIPELINE_FRAGMENT_SHARE: getArcadeUrl('/api/v1/fragment/entityPipeline/{fragmentId}/share'),
  ENTITY_PIPELINE_FRAGMENT_HIDE : getArcadeUrl('/api/v1/fragment/entityPipeline/{fragmentId}/hide'),
  ENTITY_PIPELINE_FRAGMENT_SHOW : getArcadeUrl('/api/v1/fragment/entityPipeline/{fragmentId}/show'),

  // Field Pipeline Fragment
  FIELD_PIPELINE_FRAGMENT      : getArcadeUrl('/api/v1/fragment/fieldPipeline'),
  FIELD_PIPELINE_FRAGMENT_ITEM : getArcadeUrl('/api/v1/fragment/fieldPipeline/{fragmentId}'),
  FIELD_PIPELINE_FRAGMENT_SHARE: getArcadeUrl('/api/v1/fragment/fieldPipeline/{fragmentId}/share'),
  FIELD_PIPELINE_FRAGMENT_HIDE : getArcadeUrl('/api/v1/fragment/fieldPipeline/{fragmentId}/hide'),
  FIELD_PIPELINE_FRAGMENT_SHOW : getArcadeUrl('/api/v1/fragment/fieldPipeline/{fragmentId}/show'),

  GET_DATA_STORE        : getArcadeUrl('/api/v1/datastore'),
  PROVISION_DATA_STORE  : getArcadeUrl('/api/v1/datastore/provision'),
  DATA_STORE_LAG        : getArcadeUrl('/api/v1/datastore/lag'),
  GET_DATA_STORE_LIST   : getArcadeUrl('/api/v1/datastore'),
  GET_DATA_STORE_DESCRIBE   : getArcadeUrl('/api/v1/datastore/metadata/describe'),
  UPDATE_DATA_STORE   : getArcadeUrl('/api/v1/datastore/{dataStoreId}'),
  ACTIVATE_DATA_STORE   : getArcadeUrl('/api/v1/datastore/{dataStoreId}/activate'),
  DEACTIVATE_DATA_STORE   : getArcadeUrl('/api/v1/datastore/{dataStoreId}/deactivate'),

  GET_ZENDESK_JWT_TOKEN : getArcadeUrl('/api/v1/user/zendeskJwtToken'),

  // Fast Mapper
  MAPPING: getArcadeUrl('/api/v1/pipeline/{entityId}/mapping'),
  REMOVE_MAPPING: getArcadeUrl('/api/v1/pipeline/{entityId}/mapping/bulkDelete'),

  // Custom Action
  CUSTOM_ACTION_LIST          : getArcadeUrl('/api/v1/actions/author/list'),
  CUSTOM_ACTION               : getArcadeUrl('/api/v1/actions/http'),
  CUSTOM_ACTION_VALIDATE      : getArcadeUrl('/api/v1/actions/http/validate'),
  CUSTOM_ACTION_TESTING       : getArcadeUrl('/api/v1/actions/http/testing'),
  CUSTOM_ACTION_CREATE_DRAFT  : getArcadeUrl('/api/v1/actions/{customActionId}/createDraft'),
  CUSTOM_ACTION_DISCARD_DRAFT : getArcadeUrl('/api/v1/actions/{customActionId}/discardDraft'),
  CUSTOM_ACTION_GET_DRAFT     : getArcadeUrl('/api/v1/actions/{customActionId}/draft'),
  CUSTOM_ACTION_ITEM          : getArcadeUrl('/api/v1/actions/{actionId}'),
  CUSTOM_ACTION_PUBLISH       : getArcadeUrl('/api/v1/actions/{actionId}/publish'),
  CUSTOM_ACTION_SHARE         : getArcadeUrl('/api/v1/actions/share/{actionId}?shareWithOrg={shareWithOrg}&shareGlobally={shareGlobally}'),

  // Custom Synapse
  SDK_CUSTOM_SYNAPSE                    : getArcadeUrl('/api/v1/connectormeta'),
  SDK_CUSTOM_SYNAPSE_TEST               : getArcadeUrl('/api/v1/connectormeta/test'),
  SDK_CUSTOM_SYNAPSE_DOWNLOAD_FILES     : getArcadeUrl('/api/v1/connectormeta/{connectorMetaDefinitionId}/downloadFiles'),
  SDK_CUSTOM_SYNAPSE_DOWNLOAD_ERROR_LOGS: getArcadeUrl('/api/v1/connectormeta/{connectorMetaDefinitionId}/downloadErrorLog'),
  SDK_CUSTOM_SYNAPSE_UPDATE_DRAFT       : getArcadeUrl('/api/v1/connectormeta/{connectorMetaDefinitionId}/updateDraft'),
  SDK_CUSTOM_SYNAPSE_STATUS             : getArcadeUrl('/api/v1/connectormeta/{connectorMetaDefinitionId}/status'),
  SDK_CUSTOM_SYNAPSE_GET_DRAFT          : getArcadeUrl('/api/v1/connectormeta/{connectorMetaDefinitionId}/draft'),
  SDK_CUSTOM_SYNAPSE_SUBMIT_FOR_APPROVAL: getArcadeUrl('/api/v1/connectormeta/{connectorMetaDefinitionId}/submitForApproval'),
  SDK_CUSTOM_SYNAPSE_WITHDRAW_APPROVAL  : getArcadeUrl('/api/v1/connectormeta/{connectorMetaDefinitionId}/withdrawApproval'),
  SDK_CUSTOM_SYNAPSE_APPROVE            : getArcadeUrl('/api/v1/connectormeta/{connectorMetaDefinitionId}/approve'),
  SDK_CUSTOM_SYNAPSE_CREATE_DRAFT       : getArcadeUrl('/api/v1/connectormeta/{customSynapseId}/createDraft'),
  SDK_CUSTOM_SYNAPSE_DISCARD_DRAFT      : getArcadeUrl('/api/v1/connectormeta/{customSynapseId}/discardDraft'),
  SDK_CUSTOM_SYNAPSE_DOWNLOAD_SAMPLE    : getArcadeUrl('/api/v1/connectormeta/samplesynapse'),
  CUSTOM_SYNAPSE_ALL                    : getArcadeUrl('/api/v1/connectormeta/customSynapses/all'),
  CUSTOM_SYNAPSE_ITEM                   : getArcadeUrl('/api/v1/connectormeta/{connectorMetaDefinitionId}'),
  CUSTOM_SYNAPSE_ICON                   : getArcadeUrl('/api/v1/connectormeta/{connectorMetaDefinitionId}/icon'),
  CUSTOM_SYNAPSE_SHARE                  : getArcadeUrl('/api/v1/connectormeta/{connectorMetaDefinitionId}/share'),
  CUSTOM_SYNAPSE_SHARE_SCOPE            : getArcadeUrl('/api/v1/connectormeta/sharing/scope'),

  HTTP_CUSTOM_SYNAPSE                   : getArcadeUrl("/api/v1/connectormeta/httpsource"),
  HTTP_CUSTOM_SYNAPSE_DATATYPES         : getArcadeUrl("/api/v1/connectormeta/httpsource/supportedDataTypes"),
  HTTP_CUSTOM_SYNAPSE_AUTHTYPES         : getArcadeUrl("/api/v1/connectormeta/httpsource/supportedAuthTypes"),
  HTTP_CUSTOM_SYNAPSE_TEST              : getArcadeUrl("/api/v1/connectormeta/httpsource/test"),
  HTTP_CUSTOM_SYNAPSE_CREATE_DRAFT      : getArcadeUrl("/api/v1/connectormeta/httpsource/{metadataId}/createDraft"),
  HTTP_CUSTOM_SYNAPSE_DISCARD_DRAFT     : getArcadeUrl("/api/v1/connectormeta/httpsource/{metadataId}/discardDraft"),
  HTTP_CUSTOM_SYNAPSE_UPDATE_DRAFT      : getArcadeUrl("/api/v1/connectormeta/httpsource/{metadataId}/updateDraft"),
  HTTP_CUSTOM_SYNAPSE_PUBLISH           : getArcadeUrl("/api/v1/connectormeta/httpsource/{metadataId}/approve"),

  HTTP_CUSTOM_SYNAPSE_ENTITY            : getArcadeUrl("/api/v1/connectormeta/httpsource/{metadataId}/entity"),
  HTTP_CUSTOM_SYNAPSE_ENTITY_ITEM       : getArcadeUrl("/api/v1/connectormeta/httpsource/{metadataId}/entity/{entityId}"),
  HTTP_CUSTOM_SYNAPSE_ENTITY_SCHEMA     : getArcadeUrl("/api/v1/connectormeta/httpsource/schema/generate"),
  HTTP_CUSTOM_SYNAPSE_ENTITY_PAGINATION : getArcadeUrl("/api/v1/connectormeta/httpsource/pagination"),

  WEBHOOK_CUSTOM_SYNAPSE                   : getArcadeUrl("/api/v1/connectormeta/webhook"),
  WEBHOOK_CUSTOM_SYNAPSE_CREATE_DRAFT      : getArcadeUrl("/api/v1/connectormeta/webhook/{metadataId}/createDraft"),
  WEBHOOK_CUSTOM_SYNAPSE_DISCARD_DRAFT     : getArcadeUrl("/api/v1/connectormeta/webhook/{metadataId}/discardDraft"),
  WEBHOOK_CUSTOM_SYNAPSE_UPDATE_DRAFT      : getArcadeUrl("/api/v1/connectormeta/webhook/{metadataId}/updateDraft"),
  WEBHOOK_CUSTOM_SYNAPSE_PUBLISH           : getArcadeUrl("/api/v1/connectormeta/webhook/{metadataId}/approve"),
  WEBHOOK_CUSTOM_SYNAPSE_AUTHTYPES: getArcadeUrl("/api/v1/connectormeta/webhook/supportedAuthTypes"),
  WEBHOOK_CUSTOM_SYNAPSE_TEST              : getArcadeUrl("/api/v1/connectormeta/webhook/test"),
  WEBHOOK_CUSTOM_SYNAPSE_ENDPOINT_URL: getArcadeUrl("/api/v1/connectormeta/webhook/endpoint"),
  WEBHOOK_CUSTOM_SYNAPSE_LOGS: getArcadeUrl("/api/v1/connector/log"),
  WEBHOOK_CUSTOM_SYNAPSE_LOGS_EXPORT: getArcadeUrl('/api/v1/connector/log/download'),
  WEBHOOK_CUSTOM_SYNAPSE_HTTP_CODES: getArcadeUrl('/api/v1/connectormeta/webhook/httpcodes'),
   
  SYNAPSE_CAPABILITIES_DOC          : getArcadeUrl('/api/v1/connectormeta/capabilities/{metaId}'),
  SYNAPSE_DEFAULT_MAPPINGS          : getArcadeUrl('/api/v1/connectormeta/mappings/{metaId}'),

  PYPI_SYNCARI_SDK_INFO          : getArcadeUrl('/api/v1/syncariSdkInfo'),

  // Pipline visibility
  ENTITY_SYNC_METRICS         : getArcadeUrl('/api/v1/pipeline/entityPipeline/syncMetric/{syncariEntityId}'),

  // Credential
  CREDENTIAL_DESC : getArcadeUrl('/api/v1/credentials/describe'),
  CREDENTIAL      : getArcadeUrl('/api/v1/credentials'),
  CREDENTIAL_ITEM : getArcadeUrl('/api/v1/credentials/{credentialId}'),

  // Imported Files
  IMPORTED_FILES_FOLDERS         : getArcadeUrl('/api/v1/fileData/folder'),
  IMPORT_FILE                    : getArcadeUrl('/api/v1/fileData/file'),
  GET_IMPORT_FILE                : getArcadeUrl('/api/v1/fileData/file/{fileId}'),
  GET_EXPORT_FILE                : getArcadeUrl('/api/v1/fileData/download/{fileId}'),
  GET_IMPORTED_FILE_PREVIEW      : getArcadeUrl('/api/v1/fileData/preview/{fileId}'),
  EDIT_IMPORTED_FOLDER           : getArcadeUrl('/api/v1/fileData/folder/{folderId}'),


  // Insights Studio
  ENABLE_INSIGHTS                   : getArcadeUrl('/api/v1/instance/insights/enable'),
  ENABLE_INSIGHTSPROVIDER           : getArcadeUrl('/api/v1/instance/feature/InsightsProvider/enable'),
  INSIGHTS_DASHBOARDS               : getArcadeUrl('/api/v1/insights/dashboard'),
  INSIGHTS_DASHBOARD                : getArcadeUrl('/api/v1/insights/dashboard/{dashboardId}'),
  INSIGHTS_CREATE_DRAFT_DASHBOARD   : getArcadeUrl('/api/v1/insights/dashboard/{dashboardId}/createDraft'),
  INSIGHTS_DELETE_DRAFT_DASHBOARD   : getArcadeUrl('/api/v1/insights/dashboard/{dashboardId}/discard'),
  INSIGHTS_PUBLISH_DRAFT_DASHBOARD  : getArcadeUrl('/api/v1/insights/dashboard/{dashboardId}/approve'),
  INSIGHTS_DASHBOARD_VARIABLES      : getArcadeUrl('/api/v1/insights/dashboard/variables/{dashboardId}'),
  INSIGHTS_DASHBOARD_VARIABLES_PREF : getArcadeUrl('/api/v1/insights/dashboard/{dashboardId}/updateDashboardVariablePreferences'),

  INSIGHTS_GET_DATACARD             : getArcadeUrl('/api/v1/insights/dashboard/{dashboardId}/datacard/{dataCardId}'),
  INSIGHTS_GET_DATACARD_PAGE        : getArcadeUrl('/api/v1/insights/dashboard/{dashboardId}/datacard/{dataCardId}/readData'),
  INSIGHTS_DATACARDS                : getArcadeUrl('/api/v1/insights/datacard'),
  INSIGHTS_DATACARDS_AUTHOR         : getArcadeUrl('/api/v1/insights/datacard/author'),
  INSIGHTS_DATACARD                 : getArcadeUrl('/api/v1/insights/datacard/{dataCardId}'),
  INSIGHTS_DATACARD_DRAFT           : getArcadeUrl('/api/v1/insights/datacard/{dataCardId}/createDraft'),
  INSIGHTS_DATACARD_DELETE          : getArcadeUrl('/api/v1/insights/datacard/{dataCardId}/delete'),
  INSIGHTS_DATACARD_DISCARD         : getArcadeUrl('/api/v1/insights/datacard/{dataCardId}/discard'),
  INSIGHTS_DATACARD_CREATE_DATASET  : getArcadeUrl('/api/v1/insights/datacard/{dataCardId}/withDataset'),
  INSIGHTS_DATACARD_PREVIEW         : getArcadeUrl('/api/v1/insights/datacard/preview'),
  INSIGHTS_DATACARD_PUBLISH         : getArcadeUrl('/api/v1/insights/datacard/{dataCardId}/approve'),
  INSIGHTS_DATACARD_DEPENDENCIES    : getArcadeUrl('/api/v1/insights/datacard/{dataCardId}/dependencies'),

  // Data card with dataset
  INSIGHTS_DATACARD_WITH_DATASET        : getArcadeUrl('/api/v1/insights/datacard/withDataset'),
  INSIGHTS_DATACARD_WITH_DATASET_SAMPLE : getArcadeUrl('/api/v1/insights/datasets/preview'),
  INSIGHTS_DATACARD_WITH_DATASET_SAMPLE_WITH_QUERY : getArcadeUrl('/api/v1/insights/datasets/previewWithQuery'),

  // Dataset
  INSIGHTS_DATASET                : getArcadeUrl('/api/v1/insights/datasets'),
  INSIGHTS_TS_DATASET             : getArcadeUrl('/api/v1/insights/datasets/insightsprovider'),
  INSIGHTS_DATASET_ENTRY          : getArcadeUrl('/api/v1/insights/datasets/{datasetId}'),
  INSIGHTS_DATASET_PUBLISH        : getArcadeUrl('/api/v1/insights/datasets/{datasetId}/approve'),
  INSIGHTS_DATASET_DELETE         : getArcadeUrl('/api/v1/insights/datasets/{datasetId}/delete'),
  INSIGHTS_DATASETS_DELETE         : getArcadeUrl('/api/v1/insights/datasets/delete'),
  INSIGHTS_DATASET_CREATE_DRAFT   : getArcadeUrl('/api/v1/insights/datasets/{datasetId}/createDraft'),
  INSIGHTS_DATASET_DISCARD_DRAFT  : getArcadeUrl('/api/v1/insights/datasets/{datasetId}/discard'),
  INSIGHTS_DATASET_SAMPLE_RECORD  : getArcadeUrl('/api/v1/insights/datasets/{datasetId}/readSampleData'),
  INSIGHTS_DATASET_ENTITIES       : getArcadeUrl('/api/v1/insights/datasets/datasourcesInfo'),
  INSIGHTS_DATASET_TS_ENTITIES    : getArcadeUrl('/api/v1/insights/datasets/datasourcesInfo/insightsProvider'),
  INSIGHTS_DATASET_FUNCTIONS      : getArcadeUrl('/api/v1/insights/datasets/functions'),
  INSIGHTS_DATASET_CREATE_VARIABLE: getArcadeUrl('/api/v1/insights/datasets/{datasetId}/createVariable'),
  INSIGHTS_DATASET_VARIABLE       : getArcadeUrl('/api/v1/insights/datasets/{datasetId}/getVariables'),
  INSIGHTS_DATASET_DELETE_VARIABLE: getArcadeUrl('/api/v1/insights/datasets/{datasetId}/deleteVariable/{apiName}'),
  INSIGHTS_DATASET_UPDATE_VARIABLE: getArcadeUrl('/api/v1/insights/datasets/{datasetId}/updateVariable'),
  INSIGHTS_DATASET_DATA_SOURCE_FIELDS   : getArcadeUrl('/api/v1/insights/datasets/{dataSourceId}/{dataSourceType}'),
  INSIGHTS_DATASET_SUGGEST_JOINS        : getArcadeUrl('/api/v1/insights/datasets/join/suggestions'),
  INSIGHTS_DATASET_DEPENDENCIES         : getArcadeUrl('/api/v1/insights/datasets/{datasetId}/dependencies'),
  INSIGHTS_DATASET_COUNT                : getArcadeUrl('/api/v1/insights/datasets/count'),
  INSIGHTS_DATASET_AUTO_JOIN            : getArcadeUrl('/api/v1/insights/datasets/autojoin'),
  INSIGHTS_DATASET_EXPORT_JOBS          : getArcadeUrl('/api/v1/insights/datasets/{datasetId}/exportJobs'),
  INSIGHTS_DATASET_EXPORT               : getArcadeUrl('/api/v1/insights/datasets/{datasetId}/export'),
  INSIGHTS_DATASET_CANCEL_EXPORT        : getArcadeUrl('/api/v1/insights/datasets/cancel/{exportJobId}'),
  INSIGHTS_DATASET_DOWNLOAD             : getArcadeUrl('/api/v1/insights/datasets/download/{exportJobId}'),
  INSIGHTS_DATASET_DELETE_EXPORT        : getArcadeUrl('/api/v1/insights/datasets/delete/{exportJobId}'),
  INSIGHTS_DATASET_READ_DATA            : getArcadeUrl('/api/v1/insights/datasets/readData'),
  INSTGHTS_DATASET_GET_QUERY            : getArcadeUrl('/api/v1/insights/datasets/getQuery'),
  INSIGHTS_DATASET_COUNT_QUERY          : getArcadeUrl('/api/v1/insights/datasets/countWithQuery'),
  INSIGHTS_DATASET_SCHEMA               : getArcadeUrl('/api/v1/insights/datasets/getSchema'),
  INSIGHTS_DATASET_FROM_QUERY           : getArcadeUrl('/api/v1/insights/datasets/getDatasetFromQuery'),

  // Insights sharing
  INSIGHTS_SHARING: getArcadeUrl('/api/v1/insightssharing'),
  ALLOWED_DOMAINS: getArcadeUrl('/api/v1/insightssharing/allowedDomains'),
  LIST_DOMAINS: getArcadeUrl('/api/v1/insightssharing/listDomains'),
  EMAIL_DOMAINS_IN_USE: getArcadeUrl('/api/v1/insightssharing/deletedDomainsRecords'),
  INSIGHTS_SHARING_DETAILS: getArcadeUrl('/api/v1/insightssharing/details'),
  INSIGHTS_SHARING_DETAILS_WITH_ID: getArcadeUrl('/api/v1/insightssharing/details/{dashboardId}'),
  INSIGHTS_SHARING_RESHARE: getArcadeUrl('/api/v1/insightssharing/reshare'),
  INSIGHTS_SHARING_ALL_SHARED_DASHBOARDS: getArcadeUrl('/api/v1/insightssharing/allSharedDashboard'),
  INSIGHTS_SHARING_DASHBOARD_WITH_ID: getArcadeUrl('/api/v1/insightssharing/dashboard/{dashboardId}'),

  // Thoughtspot
  INSIGHTS_TS_TOKEN: getArcadeUrl("/api/v1/insights/provider/token"),
  INSIGHTS_TS_LIVEBOARDS: getArcadeUrl("/api/v1/insights/liveboards"),
  INSIGHTS_TS_SHARE: getArcadeUrl('/api/v1/insights/provider/share/{metadataType}/{metadataId}'),

  // Instance Feature
  INSTANCE_FEATURE_ENABLE   : getArcadeUrl('/api/v1/instance/feature/{featureName}/enable'),
  INSTANCE_FEATURE_DISABLE  : getArcadeUrl('/api/v1/instance/feature/{featureName}/disable'),
  INSTANCE_FEATURE          : getArcadeUrl('/api/v1/instance/feature/{featureName}'),
  INSTANCE_FEATURES         : getArcadeUrl('/api/v1/instance/features'),

  // Settings
  SETTINGS_RBAC_ALL_ROLES      : getArcadeUrl('/api/v1/authz/roles'),
  SETTINGS_RBAC_ROLE_DETAILS   : getArcadeUrl('/api/v1/authz/role/{roleId}'),
  SETTINGS_ALL_PERMISSIONS     : getArcadeUrl('/api/v1/authz/privileges'),
  SETTINGS_RBAC_CREATE_ROLE    : getArcadeUrl('/api/v1/authz/role'),

  // Error Notifications
  ERROR_NOTIFICATION_CADENCES: getArcadeUrl('/api/v1/errorNotifications/cadences'),
  ERROR_NOTIFICATION_TYPES: getArcadeUrl('/api/v1/errorNotifications/types'),
  ERROR_NOTIFICATION_CONFIGURATIONS: getArcadeUrl('/api/v1/errorNotifications/configurations'),
  ERROR_NOTIFICATION_CONFIGURATION_ITEM: getArcadeUrl('/api/v1/errorNotifications/configurations/{id}'),
  ERROR_NOTIFICATION_CONFIGURATION_TEST: getArcadeUrl('/api/v1/errorNotifications/configurations/test'),
  ERROR_NOTIFICATION_WEBHOOK_BODY: getArcadeUrl('/api/v1/errorNotifications/configurations/webhook/body'),
  ERROR_NOTIFICATION_INVITE_USER: getArcadeUrl('/api/v1/errorNotifications/invitation/{encInstanceId}/{invitationId}/{status}'),
  ERROR_NOTIFICATION_RESEND_INVITE: getArcadeUrl('/api/v1/errorNotifications/configurations/{id}/{email}/resendOptIn'),

  // Pipeline versions
  PIPELINE_VERSIONS: getArcadeUrl('/api/v1/pipeline/entityPipeline/{syncariEntityId}/version'),
  CREATE_PIPELINE_VERSION: getArcadeUrl('/api/v1/pipeline/entityPipeline/{syncariEntityId}/version'),
  RESTORE_VERSION: getArcadeUrl('/api/v1/pipeline/entityPipeline/{syncariEntityId}/restoreVersion/{versionId}'),
  GET_PIPELINES_FOR_VERSION: getArcadeUrl('/api/v1/pipeline/entityPipeline/{syncariEntityId}/version/{versionId}/pipelines'),
  GET_PIPELINES_FOR_COMPARE: getArcadeUrl('/api/v1/pipeline/entityPipeline/{syncariEntityId}/version/{versionOneId}/versionPipelines/{versionTwoId}'),
  GET_PIPELINES_DIFF: getArcadeUrl('/api/v1/pipeline/{pipelineType}/{pipelineId}/version/{versionOneId}/diff/{versionTwoId}'),

  // Pipeline Details
  GET_PIPELINES_DETAILS: getArcadeUrl('/api/v1/pipeline/entityPipeline/details'),
  GET_PIPELINES_TRANSACTION_DETAILS: getArcadeUrl('/api/v1/pipeline/entityPipeline/details/transactions'),
  GET_PIPELINES_SYNC_METRIC_DETAILS: getArcadeUrl('/api/v1/pipeline/entityPipeline/details/syncMetric'),

  // Pipeline Documentation
  GENERATE_PIPELINE_DOCUMENTATION  : getArcadeUrl('/api/v1/pipeline/entityPipeline/{syncariEntityId}/generateDocumentation/{version}'),
  PIPELINE_DOCUMENTATION           : getArcadeUrl('/api/v1/pipeline/entityPipeline/{syncariEntityId}/documentation/{version}'),
  PIPELINE_SETTINGS_META           : getArcadeUrl('/api/v1/pipeline/entityPipeline/{syncariEntityId}/settingsMetadata'),
  PIPELINE_SETTINGS                : getArcadeUrl('/api/v1/pipeline/entityPipeline/{syncariEntityId}/settings/{version}'),

  PIPELINE_LOGS           : getArcadeUrl('/api/v1/pipeline/nodeAudit'),

  // DFI V2
  RULES                   : getArcadeUrl('/api/v1/dfi/v2/rules/{syncariEntityId}/{version}'),
  RULE                    : getArcadeUrl('/api/v1/dfi/v2/rules/{syncariEntityId}/{version}/{ruleId}'),
  CATEGORIES              : getArcadeUrl('/api/v1/dfi/v2/categories'),
  CATEGORY                : getArcadeUrl('/api/v1/dfi/v2/categories/{categoryId}'),
  RULES_METADATA          : getArcadeUrl('/api/v1/dfi/v2/rules/{syncariEntityId}/metadata'),
  DFI_PROVISION_STATUS        : getArcadeUrl('/api/v1/dfi/v2/dfiProvisionStatus/{syncariEntityId}/{draftStatus}'),
  REFERENCE_DATA_SETS     : getArcadeUrl('/api/v1/dfi/v2/referenceDataSets'),

};

export default DataUrlConstants;
