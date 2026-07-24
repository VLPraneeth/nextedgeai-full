import { ALL_OPERATIONS_VALUE, TransactionOperation, TransactionsParams } from 'store/transactions';
import { MomentDateRange } from 'utils/DateUtil';

export enum TransactionPanelType {
  CLOSED = 'closed',
  CREATE = 'create',
  UPDATE = 'update',
  MERGE = 'merge',
}

export type TransactionsTableQueryParams = {
  transactionDetail: string;
  entity?: string;
};

export interface SyncErrorsQueryParams {
  startDate: string;
  endDate: string;
  connectorName?: string | undefined;
  operation?: string | undefined;
  entityId?: string | undefined;
  syncariRecordId?: string | undefined;
  syncariEntityName?: string | undefined;
}

export type DraftTransactionsParams = Omit<TransactionsParams, 'startDate' | 'endDate'> &
  MomentDateRange & {
    entityName: string;
    operation?: TransactionOperation | typeof ALL_OPERATIONS_VALUE;
  };
