//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Link } from '@reach/router';

import { Spacer } from 'components/layout';
import useQueryParams, { encodeObjectToSearchParams } from 'hooks/useQueryParams';
import { TransactionsTableQueryParams } from 'pages/logs/types';
import { MergeTransaction } from 'store/transactions/types';
import AppConstants from 'utils/AppConstants';
import { tc, tNamespaced } from 'utils/i18nUtil';

import { useTransactionContext } from './TransactionContext';

import './TransactionRenderer.less';

const tn = tNamespaced('Transaction');

type TransactionChangesRendererProps = {
  record: MergeTransaction;
};

export function TransactionChangesRenderer({ record }: TransactionChangesRendererProps) {
  const { changes = {} } = record;
  const [queryParams] = useQueryParams<TransactionsTableQueryParams>();
  const { enableChangesPanel } = useTransactionContext();

  if (!changes) {
    return null;
  }

  let changeString = tn('changes', { count: Object.keys(changes).length });

  if (
    record.operation === AppConstants.MERGE_TRANSACTION_CONST.OPERATION ||
    record.operation === AppConstants.MERGE_TRANSACTION_CONST.REPORT_ONLY
  ) {
    const losingRecordCount = record.additionalInfo?.mergeDetails?.losingRecords?.length || 0;
    changeString = tn('losers', { count: losingRecordCount });
  } else if (record.operation === AppConstants.MERGE_TRANSACTION_CONST.EXTERNAL_DELETE) {
    // Delete always have 1 record
    changeString = tc('deleted_count', { count: 1 });
  } else if (record.operation === 'merge_skip') {
    changeString = tn('merge_skip', { count: 1 });
  }

  const searchParams = encodeObjectToSearchParams({
    ...queryParams,
    transactionDetail: record.id,
  });

  return (
    <div className="synri-changes-column">
      <Link to={`?${searchParams}`} className="aria-label" onClick={() => enableChangesPanel()}>
        {changeString}
      </Link>
      <Spacer x="md" />
    </div>
  );
}

export const rendererWrapper = (_value: MergeTransaction['changes'], data: MergeTransaction) => (
  <TransactionChangesRenderer record={data} />
);

export default TransactionChangesRenderer;
