//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
const AppConstants = {
  CSRF_TOKEN: 'XSRF-TOKEN',
  BEARER_TOKEN: 'BEARER_TOKEN',
  // TODO: Move this to the data url
  API_URL: '$ARCADE_TARGET/swagger-ui.html',
  THOUGHTSPOT_URL: process.env.REACT_APP_THOUGHTSPOT_URL || '',

  SUBSCRIPTION_STATUS: {
    ACTIVE: 'ACTIVE',
    DELETED: 'DELETED',
  },

  CONNECTOR_STATUS: {
    NEW: 'NEW',
    DELETED: 'DELETED',
    ACTIVE: 'ACTIVE',
    AUTHENTICATED: 'AUTHENTICATED',
    ACTIVATING: 'ACTIVATING',
    ERROR: 'ERROR',
    INACTIVE: 'INACTIVE',
  },

  SYNCARI_CONNECTOR_NAME: 'syncari',

  SCHEMA_REFRESH_STATUS: {
    NEW: 'NEW',
    SUCCESS: 'SUCCESS',
    ERROR: 'ERROR',
    PROCESSING: 'PROCESSING',
  },

  USER_STATUS: {
    ACTIVE: 'active',
    INACTIVE: 'inactive',
    PENDING: 'pending',
  },

  INSTANCE_STATUS: {
    ACTIVE: 'ACTIVE',
    INACTIVE: 'INACTIVE',
    DELETING: 'DELETING',
    DELETED: 'DELETED',
  },

  TRUE: 'true',
  FALSE: 'false',
  ON: 'on',
  OFF: 'off',

  FIELD_STATUS: {
    ACTIVE: 'ACTIVE',
  },

  REDUCER_NAME: {
    CONNECTOR: 'connector',
    USER: 'user',
    SUBSCRIPTION: 'subscription',
    CREDENTIAL: 'credential',
    INSTANCE: 'instance',
    ENTITY: 'entity',
    PIPELINE_FUNCTION: 'pipelineFunction',
    PIPELINE_ACTION: 'pipelineAction',
    ENTITY_PIPELINE: 'entityPipeline',
    FIELD_PIPELINE: 'fieldPipeline',
    REFERENCE_DATA: 'referenceData',
    TAG: 'tag',
    TRANSACTION: 'transaction',
    LOGS: 'logs',
    NOTIFICATION: 'notification',
    REPORT: 'report',
    DASHBOARD: 'dashboard',
  },

  CONNECTOR_METADATA_TYPE: {
    SYNAPSE: 'Synapse',
    SERVICE: 'Service',
  },

  ENTITY_TYPES: {
    STANDARD: 'standard',
    CUSTOM: 'custom',
  },

  FLOW_TYPE_COLOR: {
    ACTION: '#7B63E2',
    FUNCTION: '#4FC5C2',
    CONNECTOR: '#3EC675',
    DEFAULT: '#1890FF',
    CONNECTOR_ENTITY: '#FFFFFF',
    CONNECTOR_ATTRIBUTE: '#FFFFFF',
  },

  EDGE_COLOR: {
    ENTITY: '#DCE2E7',
    PIPELINE: '#A3B1BF',
  },

  EDGE_TYPE: {
    USER_PREFERENCE: 'userPreferenceEdge',
  },

  FLOW_TYPE_NODE_COLOR: {
    ACTION: '#A9A1DA',
    FUNCTION: '#B9E1E0',
    CONNECTOR: '',
    SYNCARI: '#578CEB',
  },

  SCHEMA_NODE_COLORS: {
    UNMAPPED: '#AAB6BE',
    PUBLISHED: '#93c47d',
    DRAFT: '#f7b16b',
    PUBLISHED_WITH_DRAFT: '#578CEB',
    ERROR: '#E16666',
    NO_STATUS: '#578CEB',
  },

  GRAPH_NODE_SHAPES: {
    BASE: 'base-node',
    LOGO_ONLY: 'logo-only-node',
    CORE_ENTITY: 'core-entity-node',
    FUNCTION: 'function-node',
    PREDICATE_FUNCTION: 'predicate-function-node',
    CASE_BRANCH_FUNCTION: 'case-branch-function',
    LOOP_SIDE_FUNCTION: 'loop-side-function-node',
    ACTION: 'action-node',
    CUSTOM_GROUP: 'customGroup',
    ENTITY_SINK: 'entity-sink-node',
    ENTITY_SOURCE: 'entity-source-node',
    CONNECTOR: 'connector-node',
    SYNCARI_CIRCLE: 'syncari-circle-node',
    SYNCARI_CIRCLE_WITH_INTRO: 'syncari-circle-node-with-intro',
    ENTITY_NODE: 'entity-node',
  },

  NODE_TYPE: {
    CUSTOM_GROUP: 'CUSTOM_GROUP',
    ENTITY_SINK: 'ENTITY_SINK',
    ENTITY_SOURCE: 'ENTITY_SOURCE',
    CORE_ENTITY: 'CORE_ENTITY',
    CORE_ATTRIBUTE: 'CORE_ATTRIBUTE',
    ATTRIBUTE_SOURCE: 'ATTRIBUTE_SOURCE',
    ATTRIBUTE_SINK: 'ATTRIBUTE_SINK',
    FUNCTION: 'FUNCTION',
    ACTION: 'ACTION',
    CONNECTOR_ENTITY: 'CONNECTOR_ENTITY',
    CONNECTOR_ATTRIBUTE: 'CONNECTOR_ATTRIBUTE',
  },

  SCOPE: {
    ATTRIBUTE: 'ATTRIBUTE',
    ENTITY: 'ENTITY',
    CORE_ENTITY: 'CORE_ENTITY',
    CORE_ATTRIBUTE: 'CORE_ATTRIBUTE',
  },

  INPUT_TYPE: {
    AUTOCOMPLETE: 'autocomplete',
    BOOLEAN: 'boolean',
    CASE: 'case',
    CASE_PREDICATE: 'casePredicate',
    CHECKBOX: 'checkbox',
    COMPLEX: 'complex',
    COMPOSITE: 'composite',
    CONDITION: 'condition',
    CONFIRMATION_INFO_BOX: 'confirmationInfoBox',
    DATE: 'date',
    DATETIME: 'datetime',
    DOUBLE: 'double',
    EMAIL: 'email',
    EMAILBODY: 'emailBody',
    EMAILLIST: 'emailList',
    EXTERNAL_ID: 'externalId',
    FILELINK: 'filelink',
    HTTP_TEST: 'httptest',
    ID: 'id',
    IMAGE: 'image',
    INFOBOX: 'infoBox',
    INTEGER: 'integer',
    MULTISELECT: 'multiselect',
    MULTISELECT_FIELD: 'multiselectfield',
    MULTIVALUETEXT: 'multivaluetext',
    OBJECT: 'object',
    PASSWORD: 'password',
    PHONE: 'phone',
    PICKLIST: 'picklist',
    PICKLIST_COMBO: 'combolist',
    POLYMORPHIC_REFERENCE: 'polymorphicreference',
    PREDICATE: 'predicate',
    RADIO: 'radio',
    REFERENCE: 'reference',
    REFERENCE_DATA: 'reference_data',
    RICHTEXT: 'richtext',
    SELECT_TEXT: 'selecttext',
    STRING: 'string',
    TAB: 'tab',
    TABLE: 'table',
    TAG: 'tag',
    TEXT: 'text',
    TEXTAREA: 'textarea',
    TIMESTAMP: 'timestamp',
    URL: 'url',
  },

  INPUT_RENDER_TYPE: {
    SCHEDULE: 'schedule',
    STRING: 'string',
    TOKENS: 'tokens',
    FIELD_MERGE_POLICY_RETAIN_FIELD: 'fieldmergepolicyretainfield',
    COMPOSITE_INPUT: 'compositeinput',
    DATASET_VARIABLE_PICKER: 'datasetVariablePicker',
    DATA_SOURCE_FIELD_PICKER: 'datasourcefieldpicker',
    DATASET_GROUP_FIELD_PICKER: 'datasetGroupFieldPicker',
    DATASET_SELECTED_FIELD_PICKER: 'datasetSelectedFieldPicker',
  },

  SKULL_RENDER_TYPE: {
    DISPLAY_TEXT: 'displayText',
    INSTANCE_PICKER: 'instancePicker',
    JUMP_TO_STEP_LABEL: 'jumpToStepLabel',
    SCHEMA_MATCHER: 'schemaMatcher',
    MERGE_OPTIONS: 'mergeOptions',
    PIPELINE_PICKER: 'pipelinePicker',
    PIPELINE_PICKER_PREVIEW: 'pipelinePickerPreview',
    QUICK_START_INSTALL_REVIEW: 'quickStartInstallReview',
    QUICK_START_INSTALL_ERROR_RESOLUTION: 'quickStartInstallErrorResolution',
    QUICK_START_INSTALL_POST_INSTALLATION: 'quickStartPostInstallation',
    SKULL_COLUMNS: 'skullColumns',
    ACTION_CONFIGURATION: 'actionConfiguration',
    VARIABLES_CONFIGURATION: 'variablesConfiguration',
    DATA_SET_CONFIGURATION: 'datasetConfiguration',
    CUSTOM_ACTION_REVIEW: 'customActionReview',
    ACTION_BODY: 'actionBody',
    SDK_CUSTOM_SYNAPSE: 'sdkCustomSynapse',
    HTTP_CUSTOM_SYNAPSE: 'httpCustomSynapse',
    HTTP_CUSTOM_SYNAPSE_ENTITY: 'httpCustomSynapseEntity',
    WEBHOOK_CUSTOM_SYNAPSE: 'webhookCustomSynapse',
    SET_VALUE_FIELD: 'setValueField',
    SET_VALUE_FIELD1: 'setValueField1',
    UPDATE_EXTERNAL_RECORD: 'updateExternalRecord',
  },

  INPUT_AUTOCOMPLETE_OPTIONS: {
    // autocomplete='off' is not respected by browsers anymore.
    // autocomplete='new-password' needs to be set in order for browsers to not autofill inputs
    // https://developer.mozilla.org/en-US/docs/Web/Security/Securing_your_site/Turning_off_form_autocompletion#preventing_autofilling_with_autocompletenew-password
    OFF: 'new-password',
  },

  // datatypes that has multiple
  // supporting inputs
  PARENT_DATATYPE: ['predicate'],

  INPUT_DISPLAY_MODE: {
    READONLY: 'readonly',
  },

  GRAPH_LOCATION: {
    CENTER_X: 535,
    CENTER_Y: 180,
  },

  // See NODE_TYPE and GRAPH_NODE_SHAPES for the key and values
  NODE_TYPE_SHAPE_MAP: {
    CORE_ENTITY: 'core-entity-node',
    CORE_ATTRIBUTE: 'standard-entity',
    CUSTOM_ENTITY: 'custom-entity',
    CUSTOM: 'custom-entity',
    ENTITY_SINK: 'entity-sink-node',
    ENTITY_SOURCE: 'entity-source-node',
    FUNCTION: 'function-node',
    ACTION: 'action-node',
    ATTRIBUTE_SINK: 'logo-only-node',
    ATTRIBUTE_SOURCE: 'logo-only-node',
    CONNECTOR_ENTITY: 'logo-only-node',
  },

  PIPELINE_CONTEXT: {
    FIELD: 'field',
    ENTITY: 'entity',
  },

  GRAPH_STATUS: {
    NEW: 'NEW',
    APPROVED: 'APPROVED',
    DRAFT: 'DRAFT',
    APPROVED_WITH_DRAFT: 'APPROVED_WITH_DRAFT',
  },

  SYNCARI_NODE_STATUS: {
    UNMAPPED: 'UNMAPPED',
    PUBLISHED: 'PUBLISHED',
    DRAFT: 'DRAFT',
    PUBLISHED_WITH_DRAFT: 'PUBLISHED_WITH_DRAFT',
    ERROR: 'ERROR',
    NO_STATUS: 'NO_STATUS',
  },

  NODE_ACTION: {
    UPDATE: 'update',
    UPDATE_CONFIG: 'updateConfig',
    CHANGE_DATA: 'changeData',
    ADD: 'add',
    REMOVE: 'remove',
  },

  PASSWORD_MASK: '························',

  TAG_TYPES: {
    ENTITY: 'entity',
    ATTRIBUTE: 'attribute',
  },

  LIST_TYPES: {
    CONNECTOR: 'synapse',
    DATA_QUALITY_STUDIO: 'data-quality-studio',
    ENTITY: 'entity',
    FIELD: 'field',
    FILE: 'file',
    FOLDER: 'folder',
    REFERENCE_DATA: 'reference-data',
    PIPELINE: 'pipeline',
    NODE_ID: 'nodeid',
    RECORD: 'record',
    INSIGHTS_DASHBOARD: 'insights-studio',
    QUICK_START: 'quick-start',
    DATASET: 'dataset',
    DATA_CARD: 'datacard',
    ERROR_NOTIFICATIONS_EMAIL: 'email',
    ERROR_NOTIFICATIONS_WEBHOOK: 'webhook',
  },

  // Supported preference that will get
  // stored in the store
  USER_PREF: {
    ENTITY_GRAPH: 'entityGraph',
    CONNECTOR_GRAPH: 'connectorGraph',
    ERROR_NOTIFICATION: 'errorNotification',
    DASHBOARD: 'dashboard',
    SCHEMA_STUDIO: 'schemaStudio',
    SYNC_STUDIO: 'syncStudio',
    DATA_STUDIO_STUDIO: 'dataStudio',
  },

  GRAPH_ACTION: {
    REMOVE: 'remove',
    UPDATE: 'update',
    ADD: 'add',
  },

  GRAPH_ITEM_TYPE: {
    NODE: 'node',
    EDGE: 'edge',
    GROUP: 'group',
  },

  MODAL_MODE: {
    EDIT: 'edit',
    ADD: 'add',
  },

  MARKUP: {
    NEW_LINE: '<br />',
  },

  GRAPH_CONFIG_ID_OBJECT_PATH: 'configuration.configId',

  RENDERER: {
    LINK: 'LINK',
    CHECK: 'CHECK',
    LIST_ITEM: 'LI',
    DATE_TIME: 'DATE_TIME',
    TRANSACTION_DATE: 'TRANSACTION_DATE',
    TRANSACTION_CHANGES: 'TRANSACTION_CHANGES',
    TRANSACTION_ORIGINAL_SOURCES: 'TRANSACTION_ORIGINAL_SOURCES',
    TRANSACTION_RECORD_ID: 'TRANSACTION_RECORD_ID',
    CONNECTOR_STATUS: 'CONNECTOR_STATUS',
  },

  AUTH_TYPES: {
    NETSUITE_TBA: 'NetSuiteTokenBasedAuthentication',
    USER_PASSWORD_TOKEN: 'UserPasswordToken',
    API_KEY: 'ApiKey',
    API_SECRET_KEY: 'ApiSecretKey',
    OAUTH: 'Oauth',
    USER_PASSWORD: 'UserPassword',
    SIMPLE_OAUTH: 'SimpleOAuth',
    ONE_CLICK_OAUTH: 'OneClickOAuth',
  },

  REFDATAMETA_CONST: {
    SOURCE_TYPE: {
      CSV_UPLOAD: 'upload',
    },
    META_ID: 'metaId',
    NAME: 'name',
    TYPE: 'type',
    SECRETKEY: 'secretKey',
    ACCESSKEY: 'accessKey',
    FILENAME: 'fileName',
    FILE: 'file',
  },

  MERGE_TRANSACTION_CONST: {
    TAB_SIZE: 'default',
    TAB_TYPE: 'card',
    OPERATION: 'merge',
    REPORT_ONLY: 'merge_report_only',
    EXTERNAL_DELETE: 'external_delete',
  },

  FETCH_STATUS: {
    IDLE: 'idle',
    LOADING: 'loading',
    SUCCESS: 'success',
    ERROR: 'error',
  },

  SYNAPSE_NAMES: {
    AMAZON_DYNAMO_DB: 'Amazon DynamoDB',
    IMPORTED_FILES: 'Imported Files',
    MARKETO: 'Marketo',
    SYNCARI: 'Syncari',
    POSTGRESQL: 'PostgreSQL',
  },

  SYNC_STATUS: {
    RUNNING: 'RUNNING',
    PAUSING: 'PAUSING',
    PAUSED: 'PAUSED',
    ERROR: 'ERROR',
    STALLED: 'STALLED',
    UNPUBLISHED: 'UNPUBLISHED',
    TEST: 'TEST',
    RESYNCING: 'RESYNCING',
    QUEUED: 'QUEUED',
    RETRYING: 'RETRYING',
  },

  KEYBOARD_EVENT_KEYS: {
    BACKSPACE: 'Backspace',
    COPY: 'c',
    DELETE: 'Delete',
    enter: 'Enter',
    escape: 'Escape',
    PASTE: 'v',
    SHIFT: 'Shift',
    SPACE: ' ',
  },

  GROUP_COLORS: {
    GRAY: '#efefef',
    BLUE: '#b9e6ff',
    ORANGE: '#ffe7c6',
    YELLOW: '#feffb9',
    GREEN: '#d8ffd1',
    PINK: '#ffcefd',
    PURPLE: '#ceb7ff',
  },

  SIMULATE_TRIAL_INSTANCE: 'simulate_trial_instance',
  ACTIVE_DATA_STUDIO_FILTER_ID: 'active_data_studio_filter_id',

  COPIED_NODES_CLIPBOARD: 'COPIED_NODES_CLIPBOARD',

  // DELAYS
  TOOLTIP_DELAY_SECONDS: 1,

  PREDICATE_FUNCTION_NAME: 'predicate',
  LOOP_FUNCTION_NAME: 'loop',
  DECISION_FUNCTION_NAME: 'filter',
  SWITCH_FUNCTION_NAME: 'case',
  FOR_EACH_FUNCTION_NAME: 'forEach',
  END_LOOP_FUNCTION_NAME: 'endLoop',

  CASE_BRANCH_FUNCTION_NAME: 'caseBranch',
  POLLING_INTERVAL_MS: 10000,
  SUBSCRIPTION_TYPES: {
    STANDARD: 'standard',
    TRIAL: 'trial',
    PARTNER: 'partner',
  },
} as const;

export default AppConstants;
