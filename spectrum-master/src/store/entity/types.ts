import { FieldDataType } from 'components/types';
import { FetchStatus } from 'store/types';
import AppConstants from 'utils/AppConstants';
import { ValuesOf } from 'utils/TypeUtils';

export enum EntityFieldStatus {
  NEW = 'NEW',
  ACTIVE = 'ACTIVE',
  INACTIVE = 'INACTIVE', // To be deprecated
  DELETED = 'DELETED',
  PENDING = 'PENDING',
  PROCESSING = 'PROCESSING',
  COMPLETED = 'COMPLETED',
  ERROR = 'ERROR',
}

export enum EntityFieldDraftStatus {
  NEW = 'NEW',
  DRAFT = 'DRAFT',
  APPROVED = 'APPROVED',
  ARCHIVED = 'ARCHIVED',
}

export enum EntityFieldType {
  standard = 'standard',
  custom = 'custom',
}

export interface EntityField {
  id: string;
  apiName: string;
  enableTooltip?: boolean;
  displayName: string;
  description: string | null;
  dataType: FieldDataType;
  status: EntityFieldStatus;
  type: EntityFieldType | null;
  tags: string[];
  values: string[];
  isMapped: boolean;
  hasChanges: boolean;
  draftStatus: EntityFieldDraftStatus | null;
  readOnly?: boolean;
  reference: boolean;
  required: boolean;
  unique: boolean;
  watermarkField: boolean;
  referenceTo: string | null;
  referenceTargetField: string | null;
  idField: boolean;
  system: boolean;
  multiValueField: boolean;
}

type DraftStatus = ValuesOf<typeof AppConstants.GRAPH_STATUS>;
export type EntityStatus = ValuesOf<typeof AppConstants.SYNCARI_NODE_STATUS>;

export interface Entity {
  id: string;
  apiName: string;
  displayName: string;
  description: string | null;
  subLabel: string; // TODO: type this
  iconPath: string;
  dataStoreName: string;
  pipelineStatus: EntityStatus; //TODO: type this
  type: string; // TODO: type this
  connectedTo: any[]; // TODO: type this
  tags: string[];
  location: string | null; // TODO: type this
  status: string; // TODO: type this
  draftStatus: DraftStatus;
  createdBy: string | null;
  updatedBy: string | null;
  createdAt: string | null; // TODO: this is a date string
  updatedAt: string | null; // TODO: this is a date string
  fields: EntityField[];
  activeFields: EntityField[];
  label?: string;
  entityType?: 'standard' | 'custom';
  metadata?: any;
}

export interface OffsetField {
  name: string;
  id: string;
  label: string;
}

export interface EntityMapping {
  id: string;
  name: string;
  apiName: string;
  offsetFieldList: OffsetField[];
  selectedOffsetFieldId: string;
  selectedConnectorIds: string[];
  offsetFieldReadOnly: boolean;
}

interface ConnectorEntities {
  entityMapping: EntityMapping[];
}

interface Connection {
  id: string;
  sourceEntityId: string;
  targetEntityId: string;
  direction: string; // TODO: type this "OUTBOUND" | "INBOUND" ?
}

interface ManageConnectorEntity {
  connectorId: string;
  name: string;
}

export type ResponseWithStatus<T> = Record<
  string,
  {
    status: FetchStatus;
    data?: T;
  }
>;

export type EntityFieldWithStatus = ResponseWithStatus<EntityField[]>;
export type EntityOnlyWithStatus = ResponseWithStatus<Entity[]>;

export const GET_ENTITIES = 'GET_ENTITIES';
export const GET_ENTITIES_PENDING = 'GET_ENTITIES_PENDING';
export const GET_ENTITIES_FULFILLED = 'GET_ENTITIES_FULFILLED';
export const GET_ENTITIES_FAILED = 'GET_ENTITIES_FAILED';

export const GET_CONNECTOR_ENTITIES_PENDING = 'GET_CONNECTOR_ENTITIES_PENDING';
export const GET_CONNECTOR_ENTITIES_FULFILLED = 'GET_CONNECTOR_ENTITIES_FULFILLED';
export const GET_CONNECTOR_ENTITIES_FAILED = 'GET_CONNECTOR_ENTITIES_FAILED';

export const SHOW_CONNECTOR_ENTITY_MODAL = 'SHOW_CONNECTOR_ENTITY_MODAL';
export const SHOW_CONNECTOR_FIELD_MODAL = 'SHOW_CONNECTOR_FIELD_MODAL';

export const GET_ENTITY_MAPPING_PENDING = 'GET_ENTITY_MAPPING_PENDING';
export const GET_ENTITY_MAPPING_FULFILLED = 'GET_ENTITY_MAPPING_FULFILLED';
export const GET_ENTITY_MAPPING_FAILED = 'GET_ENTITY_MAPPING_FAILED';

export const SAVE_ENTITY_MAPPING_PENDING = 'SAVE_ENTITY_MAPPING_PENDING';
export const SAVE_ENTITY_MAPPING_FULFILLED = 'SAVE_ENTITY_MAPPING_FULFILLED';
export const SAVE_ENTITY_MAPPING_FAILED = 'SAVE_ENTITY_MAPPING_FAILED';

export const GET_FIELD_MAPPING_PENDING = 'GET_FIELD_MAPPING_PENDING';
export const GET_FIELD_MAPPING_FULFILLED = 'GET_FIELD_MAPPING_FULFILLED';
export const GET_FIELD_MAPPING_FAILED = 'GET_FIELD_MAPPING_FAILED';

export const SAVE_FIELD_MAPPING_PENDING = 'SAVE_FIELD_MAPPING_PENDING';
export const SAVE_FIELD_MAPPING_FULFILLED = 'SAVE_FIELD_MAPPING_FULFILLED';
export const SAVE_FIELD_MAPPING_FAILED = 'SAVE_FIELD_MAPPING_FAILED';

export const DELETE_ENTITY_PENDING = 'DELETE_ENTITY_PENDING';
export const DELETE_ENTITY_FULFILLED = 'DELETE_ENTITY_FULFILLED';
export const DELETE_ENTITY_FAILED = 'DELETE_ENTITY_FAILED';

export const GET_ENTITY_RECORD_COUNTS_PENDING = 'GET_ENTITY_RECORD_COUNTS_PENDING';
export const GET_ENTITY_RECORD_COUNTS_FULFILLED = 'GET_ENTITY_RECORD_COUNTS_FULFILLED';
export const GET_ENTITY_RECORD_COUNTS_FAILED = 'GET_ENTITY_RECORD_COUNTS_FAILED';

export const GET_ENTITY_FIELDS_PENDING = 'GET_ENTITY_FIELDS_PENDING';
export const GET_ENTITY_FIELDS_FULFILLED = 'GET_ENTITY_FIELDS_FULFILLED';
export const GET_ENTITY_FIELDS_FAILED = 'GET_ENTITY_FIELDS_FAILED';

export const SHOW_QUICK_START_PUBLISH = 'SHOW_QUICK_START_PUBLISH';

export interface GetEntitiesPending {
  type: typeof GET_ENTITIES_PENDING;
}

export interface GetEntitiesFulfilled {
  type: typeof GET_ENTITIES_FULFILLED;
  payload: any;
}

export interface GetEntitiesFailed {
  type: typeof GET_ENTITIES_FAILED;
  error: any;
}

export interface DeleteEntityPending {
  type: typeof DELETE_ENTITY_PENDING;
}

export interface DeleteEntityFulfilled {
  type: typeof DELETE_ENTITY_FULFILLED;
}

export interface DeleteEntityFailed {
  type: typeof DELETE_ENTITY_FAILED;
  payload: {
    error: string;
  };
}

export interface GetConnectorEntitiesPending {
  type: typeof GET_CONNECTOR_ENTITIES_PENDING;
  connectorId: string;
  detailed?: boolean;
}

export interface GetConnectorEntitiesFulfilled {
  type: typeof GET_CONNECTOR_ENTITIES_FULFILLED;
  payload: any;
}

export interface GetConnectorEntitiesFailed {
  type: typeof GET_CONNECTOR_ENTITIES_FAILED;
  error: any;
  connectorId: string;
  detailed?: boolean;
}

export interface ShowConnectorEntityModal {
  type: typeof SHOW_CONNECTOR_ENTITY_MODAL;
  visible: boolean;
  manageConnectorEntity: ManageConnectorEntity | null;
}

export interface ShowConnectorFieldModal {
  type: typeof SHOW_CONNECTOR_FIELD_MODAL;
  visible: boolean;
  manageConnectorField: any | null;
}

export interface GetEntityMappingPending {
  type: typeof GET_ENTITY_MAPPING_PENDING;
}

export interface GetEntityMappingFulfilled {
  type: typeof GET_ENTITY_MAPPING_FULFILLED;
  payload: any;
}

export interface GetEntityMappingFailed {
  type: typeof GET_ENTITY_MAPPING_FAILED;
}

export interface SaveEntityMappingPending {
  type: typeof SAVE_ENTITY_MAPPING_PENDING;
}

export interface SaveEntityMappingFulfilled {
  type: typeof SAVE_ENTITY_MAPPING_FULFILLED;
}

export interface SaveEntityMappingFailed {
  type: typeof SAVE_ENTITY_MAPPING_FAILED;
}

export interface GetFieldMappingPending {
  type: typeof GET_FIELD_MAPPING_PENDING;
}

export interface GetFieldMappingFulfilled {
  type: typeof GET_FIELD_MAPPING_FULFILLED;
  payload: any;
}

export interface GetFieldMappingFailed {
  type: typeof GET_FIELD_MAPPING_FAILED;
}

export interface SaveFieldMappingPending {
  type: typeof SAVE_FIELD_MAPPING_PENDING;
}

export interface SaveFieldMappingFulfilled {
  type: typeof SAVE_FIELD_MAPPING_FULFILLED;
  payload: any;
}

export interface SaveFieldMappingFailed {
  type: typeof SAVE_FIELD_MAPPING_FAILED;
}

export interface GetEntityRecordCountsPending {
  type: typeof GET_ENTITY_RECORD_COUNTS_PENDING;
  payload: {
    entityApiNames: string[];
  };
}

export interface GetEntityRecordCountsFulfilled {
  type: typeof GET_ENTITY_RECORD_COUNTS_FULFILLED;
  payload: {
    data: { totals: { active: number; deleted: number }; countMap: Record<string, number> };
  };
}

export interface GetEntityRecordCountsFailed {
  type: typeof GET_ENTITY_RECORD_COUNTS_FAILED;
  payload: {
    entityApiNames: string[];
  };
}

export interface GetEntityFieldsPending {
  type: typeof GET_ENTITY_FIELDS_PENDING;
  entityId: string;
}

export interface GetEntityFieldsFulfilled {
  type: typeof GET_ENTITY_FIELDS_FULFILLED;
  entityId: string;
  // TODO: add types
  payload: any[];
}

export interface GetEntityFieldsFailed {
  type: typeof GET_ENTITY_FIELDS_FAILED;
  entityId: string;
  // TODO: add types
  error: Record<string, any>;
}

export interface ConnectorEntitiesOnly {
  data: EntityMapping;
  status: FetchStatus;
}

export interface ShowQuickStartPublishPayload {
  visible: boolean;
  quickStartId: string;
}

export interface ShowQuickStartPublish {
  type: typeof SHOW_QUICK_START_PUBLISH;
  payload: ShowQuickStartPublishPayload;
}

export type EntityAction =
  | GetEntitiesPending
  | GetEntitiesFulfilled
  | GetEntitiesFailed
  | DeleteEntityPending
  | DeleteEntityFulfilled
  | DeleteEntityFailed
  | GetConnectorEntitiesPending
  | GetConnectorEntitiesFulfilled
  | GetConnectorEntitiesFailed
  | ShowConnectorEntityModal
  | ShowConnectorFieldModal
  | GetEntityMappingPending
  | GetEntityMappingFulfilled
  | GetEntityMappingFailed
  | SaveEntityMappingPending
  | SaveEntityMappingFulfilled
  | SaveEntityMappingFailed
  | GetFieldMappingPending
  | GetFieldMappingFulfilled
  | GetFieldMappingFailed
  | SaveFieldMappingPending
  | SaveFieldMappingFulfilled
  | SaveFieldMappingFailed
  | GetEntityRecordCountsPending
  | GetEntityRecordCountsFulfilled
  | GetEntityRecordCountsFailed
  | GetEntityFieldsPending
  | GetEntityFieldsFulfilled
  | ShowQuickStartPublish
  | GetEntityFieldsFailed;

export interface UIEntityField extends EntityField {
  test?: string;
  iconUri: string;
}

export interface EntityState {
  connections?: Connection[];
  connectorEntities?: ConnectorEntities;
  connectorEntitiesFetching: boolean;
  connectorEntityModalVisible: boolean;
  connectorFieldModalVisible: boolean;
  connectorFieldsFetching: boolean;
  connectorFields: EntityField[];
  connectorId?: string;
  entities?: Entity[];
  entitiesFetching: boolean;
  entityRecordsCounts: Record<string, number>;
  entityRecordTotalCounts: { active: number; deleted: number };
  entityRecordsCountsStatus: Record<string, FetchStatus>;
  fieldsFetching: boolean;
  manageConnectorEntity?: ManageConnectorEntity | null;
  manageConnectorField?: ManageConnectorEntity | null;
  connectorFieldModalErrorMessage?: string;
  connectorEntitiesOnly: EntityOnlyWithStatus;
  connectorFieldsWithStatus: EntityFieldWithStatus;
  quickStartPublishVisible?: boolean;
  quickStartPublishId?: string;
}
