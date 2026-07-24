import { Transaction } from 'store/transactions';
import AppConstants from 'utils/AppConstants';

export function getTransactionId(transaction: Transaction) {
  // if our syncariId is set from the backend, show it
  if (transaction.syncariId) {
    return transaction.syncariId;
  }

  // go fishing for the syncari source and show the id from here
  const syncariSource = transaction.sources.find(
    (source) => source.connectorName === AppConstants.SYNCARI_CONNECTOR_NAME
  );

  return syncariSource?.externalId;
}

export const PANEL_OPTIONS = {
  CHANGES: 'CHANGES',
  ERRORS: 'ERRORS',
};

export enum PANEL_ACTIONS {
  ENABLE_CHANGES_MODAL = 'ENABLE_CHANGES_MODAL',
  ENABLE_ERRORS_MODAL = 'ENABLE_ERRORS_MODAL',
}
