import { FilterValue } from 'components/inputs/types';

export enum BatchStatus {
  NEW = 'NEW',
  ACTIVE = 'ACTIVE',
  DELETED = 'DELETED',
  PENDING = 'PENDING',
  PROCESSING = 'PROCESSING',
  COMPLETED = 'COMPLETED',
  ERROR = 'ERROR',
  CANCELLED = 'CANCELLED',
}

export enum BatchOperation {
  DELETE = 'delete',
  UPDATE = 'update',
}

export type Batch = {
  id: string;
  errors?: string[];
  filter?: FilterValue;
  initiatedAt: string;
  initiatedByUser: string;
  lastUpdatedAt: string;
  operation: BatchOperation;
  recordsFailed: number;
  recordsProcessed: number;
  recordsTotal: number;
  status: BatchStatus;
};

export type BatchListResponse = Batch[];

export type BatchDeleteParams = {
  entityId: string;
  deleteInEndSystem?: boolean;
  predicate?: string; // Base64 encoded Predicate
};

export type BatchUpdateParams = {
  entityId: string;
  fields: Record<string, unknown>;
  predicate?: string;
};
