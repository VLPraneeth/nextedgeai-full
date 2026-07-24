import { useCallback, useEffect, useMemo, useState } from 'react';

import DrawerPanel from 'components/DrawerPanel';
import { withI18n, useI18nContext } from 'components/I18nProvider';
import {
  CommonTransaction,
  ExternalDeleteTransaction,
  MergeSkipTransaction,
  MergeTransaction,
  Transaction,
} from 'store/transactions';
import AppConstants from 'utils/AppConstants';

import { PANEL_OPTIONS } from './common';
import MergeSkipTable from './MergeSkipTable';
import { TransactionMergeTable, TransactionDetailTable } from './data-studio-transaction-record-details';
import './DataStudioLineagePanel.less';
import ErrorDetailTable from './data-studio-transaction-record-details/ErrorDetailTable';
import TransactionExternalDeleteTable from './data-studio-transaction-record-details/TransactionExternalDeleteTable';
import { useTransactionContext } from './TransactionContext';

type TransactionDetailsPanelProps = {
  onRequestClose?: () => void;
  transaction?: Transaction;
};

const DataStudioLineagePanel = ({ transaction, onRequestClose }: TransactionDetailsPanelProps) => {
  const { tn } = useI18nContext();
  const [visible, setVisible] = useState(!!transaction);
  const { detailPanelContent } = useTransactionContext();

  const close = useCallback(() => setVisible(false), []);

  useEffect(() => {
    setVisible(!!transaction);
  }, [transaction]);

  const handleAfterVisibilityChange = useCallback(
    (visibility: boolean) => {
      if (!visibility) {
        onRequestClose?.();
      }
    },
    [onRequestClose]
  );

  const content = useMemo(() => {
    if (!transaction) {
      return null;
    }

    if (detailPanelContent === PANEL_OPTIONS.ERRORS) {
      return <ErrorDetailTable transaction={transaction as CommonTransaction} />;
    }

    if (transaction.merge || transaction.operation === AppConstants.MERGE_TRANSACTION_CONST.REPORT_ONLY) {
      return <TransactionMergeTable transaction={transaction as MergeTransaction} />;
    }

    if (transaction.merge || transaction.operation === AppConstants.MERGE_TRANSACTION_CONST.EXTERNAL_DELETE) {
      return <TransactionExternalDeleteTable transaction={transaction as ExternalDeleteTransaction} />;
    }

    if (transaction.operation === 'merge_skip') {
      return <MergeSkipTable transaction={transaction as MergeSkipTransaction} />;
    }

    return <TransactionDetailTable transaction={transaction} />;
  }, [transaction, detailPanelContent]);

  const changesCount = transaction?.changes ? Object.keys(transaction.changes).length : 0;
  const changesTitle = `${changesCount} ${tn('detail_panel_title', { transactionId: transaction?.id })}`;

  const errorCount = transaction?.errors ? Object.keys(transaction.errors).length : 0;
  const errorTitle = `${errorCount} ${tn('errors_panel_title', { transactionId: transaction?.id })}`;

  return (
    <DrawerPanel
      absolutePositioning
      afterVisibleChange={handleAfterVisibilityChange}
      maskClosable
      onClose={() => close()}
      mask
      className="data-studio-lineage-details-panel"
      title={detailPanelContent === PANEL_OPTIONS.ERRORS ? errorTitle : changesTitle}
      width={1000}
      visible={visible}>
      {content}
    </DrawerPanel>
  );
};

export default withI18n(DataStudioLineagePanel, 'TransactionDetailsPanel');
