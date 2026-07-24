import { ValuesOf } from 'utils/TypeUtils';

// Tags for RTK-Query
//
// Note: It's important that this is not an enum, although tempting. For the helpers below to work,
// we need it to resolve to a const value, and if you use an Enum, it will resolve to the Enum union, not
// the specific enum value passed in. To get enum values to work, you have to use `as const` in several places
// which is a poor dx
export const EntityTag = {
  BATCH: 'Batch',
  ENTITY_DATASCORE: 'EntityDataScore',
  ENTITY_DETAILS: 'EntityDetails',
  IMPORTED_FILES: 'ImportedFiles',
  INSTANCE_FEATURE: 'InstanceFeature',
  NODE_TOKENS: 'NodeTokens',
  QUICK_START_AUTHOR: 'QuickStartAuthor',
  QUICK_START_AUTHOR_INSTANCE: 'QuickStartInstance',
  QUICK_START_INSTALL: 'QuickStartInstall',
  QUICK_START_MARKETPLACE: 'QuickStartMarketplace',
  QUICK_START_SHARED: 'QuickStartShared',
  TRANSACTION: 'Transaction',
  CUSTOM_ACTION: 'CustomAction',
  CUSTOM_SYNAPSE: 'CustomSynapse',
  ALL_CUSTOM_SYNAPSE: 'AllCustomSynapse',
  CUSTOM_SYNAPSE_ENTITY: 'CustomSynapseEntity',
  CUSTOM_SYNAPSE_SHARING: 'CustomSynapseSharing',
  CREDENTIAL: 'Credential',
  CREDENTIAL_METADATA: 'CredentialMetadata',
  INSIGHTS_DASH_DATA_CARD: 'InsightsDashDataCard',
  INSIGHTS_DASHBOARD: 'InsightsDashboard',
  INSIGHTS_DATASET: 'InsightsDataset',
  INSIGHTS_DATA_CARD: 'InsightsDataCard',
  INSIGHTS_DATASET_VARIABLE: 'InsightsDatasetVariable',
  INSIGHTS_DATASET_SOURCE_FIELD: 'InsightsDatasetSourceField',
  PIPELINE_VERSION: 'PipelineVersion',
  RBAC: 'RBAC',
  ABAC_ATTRIBUTE: 'AbacAttribute',
  ABAC_POLICY: 'AbacPolicy',
  ABAC_VALUE: 'AbacValue',
  ERROR_NOTIFICATION: 'ErrorNotification',
  REFERENCE_DATA: 'ReferenceData',
  NOTIFICATIONS: 'Notifications',
  INSIGHTS_DATASET_EXPORT: 'InsightsDatasetExport',
  INSIGHTS_SHARING_DETAILS: 'InsightsSharingDetails',
  INSIGHTS_SHARING_ALLOWED_DOMAINS: 'InsightsSharingAllowedDomains',
  DATA_STORE: 'DataStore',
  PIPELINE_DOCUMENTATION: 'PipelineDocumentation',
  REALTIME_PIPELINE_IP_WHITELIST: 'RealtimePipelineIpWhitelist',
  DFI_V2_RULES: 'DfiV2Rules',
  DFI_V2_CATEGORIES: 'DfiV2Categories',
  DFI_V2_REFERENCE_DATA_SETS: 'DfiV2ReferenceDataSets',
  DFI_PROVISION_STATUS: 'DfiProvisionStatus',
  BRANDING: 'Branding',
  GHOST_ACCESS: 'GhostAccess',
} as const;

export type EntityTags = ValuesOf<typeof EntityTag>;

export type EntityTagObject<Tag extends EntityTags, Id extends unknown = string> = { type: Tag; id: Id };
