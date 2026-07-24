import { FieldMetadata, Status } from 'components/renderers/types';
import { FieldDataType } from 'components/types';
import { FetchStatus } from 'store/types';
import AppConstants from 'utils/AppConstants';
export interface Connector {
  id: string;
  name: string;
  icon: string;

  [index: string]: any;
}

export interface EntityModel {
  id: string;
  apiName: string;
  description: string;
  displayName: string;
  type: string;
  status: Status;
  deletedRecords: number;
  totalFields: number;
  totalRecords: number;
  references: number;
  lastUpdated: string;
  updatedBy: string;
  usedIn: ReferenceTarget[];
  sources: ReferenceTarget[];
  destinations: ReferenceTarget[];
  tags: string[];
  hasDraft: boolean;
  dataStoreName: string;
  runDFI?: boolean;
  runMerge?: boolean;
}

export interface FieldModel {
  id: string;
  apiName: string;
  description: string;
  displayName: string;
  dataType: FieldDataType;
  status: Status;
  totalFields: number;
  length: number;
  totalRecords: number;
  references: number;
  referenceTo: string;
  referenceTargetField: string;
  picklistValues: string[];
  lastUpdated: string;
  updatedBy: string;
  usedIn: ReferenceTarget[];
  sources: ReferenceTarget[];
  destinations: ReferenceTarget[];
  tags: string[];
  isCalculated: boolean;
  isIdField: boolean;
  isReadonly: boolean;
  isRequired: boolean;
  isSystem: boolean;
  isUnique: boolean;
  isWatermarkField: boolean;
  isMultiValueField: boolean;
  parentAttributeId: string;
  isSyncariDefined: boolean;
  dataStoreName: string;
  compositeKey: string;

  // Metadata control fields to determine field capbilities
  schemaUpdatable: boolean;
  schemaDeletable: boolean;
}

export interface FieldWithStatusModel extends FieldModel {
  ready?: boolean;
  isMapped?: boolean;
  hasChanges?: boolean;
  hasPublishedPipeline?: boolean;
  link?: string;
  draftLink?: string;
}

export type GraphStatus = keyof typeof AppConstants.GRAPH_STATUS;

export interface SchemaContainer<T = object> {
  fields: T;
}

export type SchemaMetadata<T extends SchemaContainer> = {
  [K in keyof T['fields']]: FieldMetadata;
};
export interface VersionedSchemaData<T> {
  apiName: string;
  draft: T;
  published: T;
}

export interface SchemaResponse<T extends SchemaContainer> {
  meta: SchemaMetadata<T>;
  data: VersionedSchemaData<T>[];
}

export type ReferenceTarget = Record<string, string>;

export type SchemaVersion = 'draft' | 'published';

export type ConnectorSchema = SchemaContainer<EntityModel>;
export type EntitySchema = SchemaContainer<FieldModel>;

export type ConnectorSchemaResponse = SchemaResponse<ConnectorSchema>;
export type EntitySchemaResponse = SchemaResponse<EntitySchema>;

export const GET_ENTITY_SCHEMA_FOR_VERSION_PENDING = 'studio/schema/entity/version/pending';
export const GET_ENTITY_SCHEMA_FOR_VERSION_FULFILLED = 'studio/schema/entity/version/fulfilled';
export const GET_ENTITY_SCHEMA_FOR_VERSION_FAILED = 'studio/schema/entity/version/failed';

export const GET_CONNECTOR_SCHEMA_PENDING = 'studio/schema/connector/pending';
export const GET_CONNECTOR_SCHEMA_FULFILLED = 'studio/schema/connector/fulfilled';
export const GET_CONNECTOR_SCHEMA_FAILED = 'studio/schema/connector/failed';

export const GET_CONNECTOR_ENTITY_SCHEMA_PENDING = 'studio/schema/entity/pending';
export const GET_CONNECTOR_ENTITY_SCHEMA_FULFILLED = 'studio/schema/entity/fulfilled';
export const GET_CONNECTOR_ENTITY_SCHEMA_FAILED = 'studio/schema/entity/failed';

export const CREATE_ENTITY_DRAFT_PENDING = 'studio/schema/entity/createDraft/pending';
export const CREATE_ENTITY_DRAFT_FULFILLED = 'studio/schema/entity/createDraft/fulfilled';
export const CREATE_ENTITY_DRAFT_FAILED = 'studio/schema/entity/createDraft/failed';

export const PUBLISH_ENTITY_SCHEMA_PENDING = 'studio/schema/entity/publish/pending';
export const PUBLISH_ENTITY_SCHEMA_FULFILLED = 'studio/schema/entity/publish/fulfilled';
export const PUBLISH_ENTITY_SCHEMA_FAILED = 'studio/schema/entity/publish/failed';

export const DISCARD_ENTITY_SCHEMA_PENDING = 'studio/schema/entity/discard/pending';
export const DISCARD_ENTITY_SCHEMA_FULFILLED = 'studio/schema/entity/discard/fulfilled';
export const DISCARD_ENTITY_SCHEMA_FAILED = 'studio/schema/entity/discard/failed';

export const SHOW_PUBLISH_CONFIRMATION_MODAL = 'studio/schema/entity/publish/modal/visibility';

export const SAVE_ENTITY_PENDING = 'studio/schema/entity/save/pending';
export const SAVE_ENTITY_FULFILLED = 'studio/schema/entity/save/fulfilled';
export const SAVE_ENTITY_FAILED = 'studio/schema/entity/save/failed';
export const RESET_ENTITY_MODAL = 'studio/schema/reset/entity/modal';

export const GET_ENTITY_DETAIL_PENDING = 'studio/schema/get/entitydetail/pending';
export const GET_ENTITY_DETAIL_FULFILLED = 'studio/schema/get/entitydetail/fulfilled';
export const GET_ENTITY_DETAIL_FAILED = 'studio/schema/get/entitydetail/failed';

export const SAVE_FIELD_PENDING = 'studio/schema/field/save/pending';
export const SAVE_FIELD_FULFILLED = 'studio/schema/field/save/fulfilled';
export const SAVE_FIELD_FAILED = 'studio/schema/field/save/failed';
export const RESET_FIELD_MODAL = 'studio/schema/reset/field/modal';
export const MARK_FIELD_PIPELINE_READY_FULFILLED = 'MARK_FIELD_PIPELINE_READY_FULFILLED';
export const RESET_PUBLISH_ENTITY_SCHEMA = 'studio/schema/entity/publish/reset';

// TODO: type this!
export type GraphVersion = string;

interface GetEntitySchemaForVersionPending {
  type: typeof GET_ENTITY_SCHEMA_FOR_VERSION_PENDING;
  payload: {
    entityId: string;
    graphVersion: GraphVersion;
  };
}

interface GetEntitySchemaForVersionFulfilled {
  type: typeof GET_ENTITY_SCHEMA_FOR_VERSION_FULFILLED;
  payload: {
    entityId: string;
    graphVersion: GraphVersion;
    data: EntitySchemaResponse;
  };
}

interface GetEntitySchemaForVersionFailed {
  type: typeof GET_ENTITY_SCHEMA_FOR_VERSION_FAILED;
  payload: {
    entityId: string;
    graphVersion: GraphVersion;
    error: string;
  };
}

interface GetConnectorSchemaPending {
  type: typeof GET_CONNECTOR_SCHEMA_PENDING;
  payload: {
    connectorId: string;
  };
}
interface GetConnectorSchemaFulfilled {
  type: typeof GET_CONNECTOR_SCHEMA_FULFILLED;
  payload: {
    connectorId: string;
    data: ConnectorSchemaResponse;
  };
}
interface GetConnectorSchemaFailed {
  type: typeof GET_CONNECTOR_SCHEMA_FAILED;
  payload: {
    connectorId: string;
  };
}

interface GetConnectorEntitySchemaPending {
  type: typeof GET_CONNECTOR_ENTITY_SCHEMA_PENDING;
  payload: { entityId: string };
}

interface GetConnectorEntitySchemaFulfilled {
  type: typeof GET_CONNECTOR_ENTITY_SCHEMA_FULFILLED;
  payload: {
    entityId: string;
    data: EntitySchemaResponse;
  };
}

interface GetConnectorEntitySchemaFailed {
  type: typeof GET_CONNECTOR_ENTITY_SCHEMA_FAILED;
  payload: {
    entityId: string;
    error: ResponseError;
  };
}

interface CreateEntityDraftPending {
  type: typeof CREATE_ENTITY_DRAFT_PENDING;
  payload: {
    entityId: string;
  };
}
interface CreateEntityDraftFulfilled {
  type: typeof CREATE_ENTITY_DRAFT_FULFILLED;
  payload: {
    entityId: string;
  };
}
interface CreateEntityDraftFailed {
  type: typeof CREATE_ENTITY_DRAFT_FAILED;
  payload: {
    entityId: string;
    error: ResponseError;
  };
}

interface PublishEntitySchemaPending {
  type: typeof PUBLISH_ENTITY_SCHEMA_PENDING;
  payload: {
    entityId: string;
  };
}

// TODO: fix type
interface PublishEntitySchemaFulfilled {
  type: typeof PUBLISH_ENTITY_SCHEMA_FULFILLED;
  payload: {
    entityId: string;
    data: any;
  };
}

interface PublishEntitySchemaFailed {
  type: typeof PUBLISH_ENTITY_SCHEMA_FAILED;
  payload: {
    entityId: string;
    error: ResponseError;
  };
}

interface ResetPublishEntitySchema {
  type: typeof RESET_PUBLISH_ENTITY_SCHEMA;
}

interface DiscardEntitySchemaPending {
  type: typeof DISCARD_ENTITY_SCHEMA_PENDING;
  payload: {
    entityId: string;
  };
}
interface DiscardEntitySchemaFulfilled {
  type: typeof DISCARD_ENTITY_SCHEMA_FULFILLED;
  payload: {
    entityId: string;
    data: any;
  };
}
interface DiscardEntitySchemaFailed {
  type: typeof DISCARD_ENTITY_SCHEMA_FAILED;
  payload: {
    entityId: string;
    error: ResponseError;
  };
}

interface ShowPublishConfirmationModal {
  type: typeof SHOW_PUBLISH_CONFIRMATION_MODAL;
  payload: {
    entityId: string | null;
    connectorId?: string | null;
  };
}

interface SaveEntityPending {
  type: typeof SAVE_ENTITY_PENDING;
  payload: {
    entityId: string;
  };
}
interface SaveEntityFulfilled {
  type: typeof SAVE_ENTITY_FULFILLED;
  payload: {
    entityId: string;
    data: EntityDetailsModel;
  };
}
interface SaveEntityFailed {
  type: typeof SAVE_ENTITY_FAILED;
  payload: {
    entityId: string;
    error: ResponseError;
  };
}
interface ResetEntityModal {
  type: typeof RESET_ENTITY_MODAL;
}

interface GetEntityDetailPending {
  type: typeof GET_ENTITY_DETAIL_PENDING;
  payload: { entityId: string };
}
interface GetEntityDetailFulfilled {
  type: typeof GET_ENTITY_DETAIL_FULFILLED;
  payload: {
    entityId: string;
    data: EntityDetailsModel;
  };
}
interface GetEntityDetailFailed {
  type: typeof GET_ENTITY_DETAIL_FAILED;
  payload: {
    entityId: string;
    error: ResponseError;
  };
}

interface SaveFieldPending {
  type: typeof SAVE_FIELD_PENDING;
}
interface SaveFieldFulfilled {
  type: typeof SAVE_FIELD_FULFILLED;
  payload: {
    data: any;
  };
}
interface SaveFieldFailed {
  type: typeof SAVE_FIELD_FAILED;
  payload: {
    error: ResponseError;
  };
}
interface ResetFieldModal {
  type: typeof RESET_FIELD_MODAL;
}

interface GetEntityDetailPending {
  type: typeof GET_ENTITY_DETAIL_PENDING;
  payload: {
    entityId: string;
  };
}

interface GetEntityDetailFulfilled {
  type: typeof GET_ENTITY_DETAIL_FULFILLED;
  payload: {
    entityId: string;
    data: EntityDetailsModel;
  };
}

interface GetEntityDetailFailed {
  type: typeof GET_ENTITY_DETAIL_FAILED;
  payload: {
    entityId: string;
    error: ResponseError;
  };
}

export type ResponseError =
  | { data?: any; message?: string; errorMessage: string; status: number; statusText: string }
  | Error
  | { message?: string };

export type SchemaAction =
  | GetEntitySchemaForVersionPending
  | GetEntitySchemaForVersionFulfilled
  | GetEntitySchemaForVersionFailed
  | GetConnectorSchemaPending
  | GetConnectorSchemaFulfilled
  | GetConnectorSchemaFailed
  | GetConnectorEntitySchemaPending
  | GetConnectorEntitySchemaFulfilled
  | GetConnectorEntitySchemaFailed
  | CreateEntityDraftPending
  | CreateEntityDraftFulfilled
  | CreateEntityDraftFailed
  | PublishEntitySchemaPending
  | PublishEntitySchemaFulfilled
  | PublishEntitySchemaFailed
  | ResetPublishEntitySchema
  | DiscardEntitySchemaPending
  | DiscardEntitySchemaFulfilled
  | DiscardEntitySchemaFailed
  | ShowPublishConfirmationModal
  | SaveEntityPending
  | SaveEntityFulfilled
  | SaveEntityFailed
  | ResetEntityModal
  | GetEntityDetailPending
  | GetEntityDetailFulfilled
  | GetEntityDetailFailed
  | SaveFieldPending
  | SaveFieldFulfilled
  | SaveFieldFailed
  | ResetFieldModal;

// enhancement for EntityPanel
export interface EntityDetailsModel extends Omit<EntityModel, 'status'> {
  /**
   * Status of the entity model
   */
  draftStatus: string;
}

export interface LegacySchemaState {
  entities: Record<string, SchemaContainer<FieldWithStatusModel[]>>;
  entitySchemaStatus: Record<string, FetchStatus>;
  connectorSchemas: Record<string, ConnectorSchemaResponse>;
  connectorSchemaStatus: Record<string, FetchStatus>;
  connectorEntitySchemas: Record<string, EntitySchemaResponse>;
  connectorEntityStatus: Record<string, FetchStatus>;
  getEntityDetailStatus: FetchStatus;
  entityDetails: EntityDetailsModel | null;
  getEntityDetailErrorMessage: ResponseError | string;
  saveEntityStatus: FetchStatus;
  saveEntityErrorMessage: ResponseError | string;
  saveFieldStatus: FetchStatus;
  saveFieldErrorMessage: ResponseError | string;
  entitySchemaDraftPublishStatus: FetchStatus;
  entitySchemaDraftDiscardStatus: FetchStatus;
  entitySchemaDraftCreateStatus: FetchStatus;
  entitySchemaDraftPublishErrorMessage?: string;
  showingPublishConfirmationModalForEntityId: null | string;
  showingPublishConfirmationModalMetadata: null | {
    entityId: string | null;
    connectorId?: string | null;
  };
}
