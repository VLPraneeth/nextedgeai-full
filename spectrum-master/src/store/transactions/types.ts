//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { FilterValue } from 'components/inputs/types';
import { FieldDataType } from 'components/types';
import { Operations } from 'pages/logs/LogsFilterPanel';
import { Entity } from 'store/entity/types';
import { ArrayToUnion } from 'utils/TypeUtils';

type TransactionEntity = Omit<
  Entity,
  'fields' | 'subLabel' | 'iconPath' | 'pipelineStatus' | 'connectedTo' | 'location' | 'activeFields' | 'type'
> &
  Partial<Pick<Entity, 'fields'>> & {
    active?: boolean;
    activeAttributes?: unknown | unknown[];
    alteredDsNameAttrs?: unknown[];
    apiNameLowerCasedToAttributes: Record<string, unknown>;
    approved: boolean;
    archived: boolean;
    attributes?: unknown[];
    child: boolean;
    connectorId?: string;
    connectorTypeId?: string;
    custom: boolean;
    dataStoreOldName?: string;
    destinationParams?: unknown[];
    draft: boolean;
    dsNameAltered?: boolean;
    fileLinkAttributes?: unknown[];
    idField: string | null;
    idToAttribMap?: Record<string, unknown>;
    idToAttributes?: Record<string, unknown>;
    parentId: string | null;
    partitions: unknown[];
    pluralName: string | null;
    readOnly: boolean;
    ready: boolean;
    references: null | unknown;
    seeded?: boolean;
    sourceParams?: unknown[];
    version: number;
    watermarkField: string | null;
  };

export type TransactionOperation = ArrayToUnion<typeof Operations>;

export interface NetsuiteLineItemValues {
  amount: number;
  item: string;
  itemType: string;
  printItems: boolean;
  quantity: number;
  line: number;
  quantityFulfilled: number;
  excludeFromRateRequest: boolean;
  taxDetailsReference: string;
  isOpen: boolean;
  isClosed: boolean;
  price: string;
  marginal: boolean;
  quantityBilled: number;
  salesorderid: string;
  commitmentFirm: boolean;
  itemSubtype: string;
  id: string;
  linked: boolean;
}

export interface NetsuiteLineItem {
  _id: string;
  name: string;
  lastModified: number;
  createdAt: number;
  syncariTimestamp: number;
  isChild: boolean;
  parentId: string;
  values: NetsuiteLineItemValues;
  isDeleted: boolean;
  isNew: boolean;
  reparented: boolean;
  compositeKeyData: Record<string, any>;
  _class: string;
}

export type TransactionExternalValue = {
  apiName: string;
  connectorId: string;
  connectorName: string;
  dataType: FieldDataType;
  displayName: string;
  fieldId: string;
  value: string | NetsuiteLineItem[];
  timestamp?: string;
};

export interface TransactionChange<T = string> {
  apiName: string;
  authoritativeSource?: TransactionExternalValue;
  dataType: FieldDataType;
  displayName: string;
  fieldId: string;
  incomingExternalValues: Record<string, string[] | string | TransactionExternalValue>;
  newValue: T;
  oldValue: T;
  outgoingExternalValues: Record<string, string[] | string | TransactionExternalValue>;
  srcId: string | null;
  timestamp: number;
}

export interface TransactionSource {
  connectorId: string;
  connectorName: string;
  externalId: string;
  lastModified: number;
}

export interface TransactionFieldMergeValue {
  dataType: FieldDataType;
  displayName: string;
  value: string;
}

export type TransactionMergeRecord = {
  child: boolean;
  compositeKeyData: Record<string, unknown>;
  connectorId: string | null;
  createdAt: number;
  deleted: boolean;
  id: string;
  ignoreFieldChanges: string[];
  lastModified: number;
  lastTransactionLogId: string | null;
  name: string;
  new: boolean;
  originatingConnectorId: string | null;
  parentId: string | null;
  reparented: boolean;
  syncariEntityId: string;
  syncariParentEntityId: string | null;
  syncariScore: null | {
    recordScore: number;
    fieldScores: Record<string, number>;
  };
  syncariTimestamp: number;
  values?: Record<string, TransactionFieldMergeValue>;
};

type TransactionMaterializedValue = {
  label: string;
  value: string;
};

export type TransactionFieldMergePolicy = {
  expressionMap: FilterValue;
  overridePolicy: TransactionMaterializedValue;
};

export type TransactionMergeInfo = {
  duplicateSelector: any;
  fieldMergePolicies: Record<string, TransactionFieldMergePolicy>;
  winnerOverridePolicy: TransactionMaterializedValue;
  winnerSelectorPredicate: any;
  winnerValueSelectionPolicy: TransactionMaterializedValue;
};

export type TransactionMergeDetails = {
  apiNameDisplayMap: Record<string, string>;
  batchId: string;
  entity: TransactionEntity;
  loserIds: string[];
  loserReferencedEntities: unknown[];
  losingRecords: TransactionMergeRecord[];
  mergeAction: 'MERGE'; // TODO: More types here?
  mergeInfo?: TransactionMergeInfo;
  reportOnly: boolean;
  winningRecord: TransactionMergeRecord;
};

export interface DisconnectedSource {
  connectorName: string;
  connectorId: string;
  id: string;
  apiName: string;
  displayName?: string;
  entityId?: string;
}

export interface DeletedRecord {
  connectorName: string;
  connectorId: string;
  id: string;
  apiName: string;
  displayName?: string;
  entityId?: string;
}

export type TransactionExternalDeleteDetails = {
  disconnectedSources: DisconnectedSource[];
  deletedId: DeletedRecord;
  syncariDeleted: boolean;
};

export type TransactionAdditionalInfo = {
  mergeDetails: TransactionMergeDetails;
  deleteInfo: TransactionExternalDeleteDetails;
};

type TransactionErrors = {
  pipeline: String;
  node: String;
  error: String;
};

type BaseTransaction = {
  batchId: string;
  changes: Record<string, TransactionChange>;
  errors: TransactionErrors[];
  createdAt: string;
  createdBy: string;
  destinations: TransactionSource[];
  entityName: string;
  id: string;
  mergeOperation: unknown;
  new: boolean;
  occurredAt: number;
  operation: TransactionOperation;
  sources: TransactionSource[];
  syncariId: string;
  updatedAt: string;
  updatedBy: string;
};

export type CommonTransaction = BaseTransaction & {
  merge?: false;
};

export type MergeTransaction = BaseTransaction & {
  additionalInfo: TransactionAdditionalInfo;
  merge: true;
};

export type MergeSkipTransaction = BaseTransaction & {
  additionalInfo: {
    mergeSkipDetails: {
      filterCondition: string;
    };
  };
  merge: false;
};

export type ExternalDeleteTransaction = BaseTransaction & {
  additionalInfo: TransactionAdditionalInfo;
  merge: false;
};

export type Transaction = CommonTransaction | MergeTransaction | ExternalDeleteTransaction | MergeSkipTransaction;

export interface PageInfo {
  start: string | null;
  end: string | null;
  hasMore: boolean;
  hasPrevious: boolean;
  pageNumber?: number;
  sorting: {
    columnName: string;
    ascending: boolean;
  }[];
}

export interface TransactionsResponse {
  records: Transaction[];
  pageInfo: PageInfo;
}

export interface TransactionKpis {
  transactions?: number;
  mostActiveEntity?: string;
  mostActiveSynapse?: string;
  newRecords?: number;
  updateRecords?: number;
  deleteRecords?: number;
}

export const ALL_ENTITIES_VALUE = 'all_entities';
export const ALL_OPERATIONS_VALUE = 'all_operations';

export interface TransactionsParams {
  entityName: string | null;
  startDate?: string;
  endDate?: string;
  operation?: TransactionOperation | typeof ALL_OPERATIONS_VALUE | null;
  syncariId: string;
}
