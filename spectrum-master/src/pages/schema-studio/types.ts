import { FieldMetadata, Status } from 'components/renderers/types';
import { FieldDataType } from 'components/types';
import { Connector as BaseConnector } from 'reducers/connectorReducer';
import AppConstants from 'utils/AppConstants';
import { VALID_SCHEMA_VERSIONS } from 'utils/UrlUtil';

const { INPUT_TYPE } = AppConstants;
const { COMPLEX, REFERENCE, POLYMORPHIC_REFERENCE, ID } = INPUT_TYPE;

export interface Connector extends BaseConnector {
  id: string;
  name: string;
  icon: string;
  typeName: string;
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

interface SchemaContainer<T = object> {
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

export type SchemaVersion = typeof VALID_SCHEMA_VERSIONS[number];

export type ConnectorSchema = SchemaContainer<EntityModel>;
export type EntitySchema = SchemaContainer<FieldModel>;

export type ConnectorSchemaResponse = SchemaResponse<ConnectorSchema>;
export type EntitySchemaResponse = SchemaResponse<EntitySchema>;

// Show the datatypes compatible for the parent attribute id
export const FILTERED_FOR_COMPLEX = [COMPLEX];

// Disallowed datatypes for synapse entities
export const DISALLOWED_DATATYPE = [COMPLEX, REFERENCE, POLYMORPHIC_REFERENCE, ID];

// Do not allow complex datatype to be created in syncari
export const DISALLOWED_SYNCARI_DATATYPE = [COMPLEX, ID, POLYMORPHIC_REFERENCE];

/* TS Guards */
export const isComplexDataType = (variableToCheck: any): variableToCheck is typeof FILTERED_FOR_COMPLEX => {
  return FILTERED_FOR_COMPLEX.includes(variableToCheck);
};

export const isDisallowedDataType = (variableToCheck: any): variableToCheck is typeof DISALLOWED_DATATYPE => {
  return DISALLOWED_DATATYPE.includes(variableToCheck);
};

export const isDisallowedSyncariDataType = (
  variableToCheck: any
): variableToCheck is typeof DISALLOWED_SYNCARI_DATATYPE => {
  return DISALLOWED_SYNCARI_DATATYPE.includes(variableToCheck);
};
