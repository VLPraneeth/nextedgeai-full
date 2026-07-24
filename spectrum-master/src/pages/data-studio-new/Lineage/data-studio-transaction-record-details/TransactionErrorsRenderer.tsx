//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Link } from '@reach/router';

import { Spacer } from 'components/layout';
import useQueryParams, { encodeObjectToSearchParams } from 'hooks/useQueryParams';
import { TransactionsTableQueryParams } from 'pages/logs/types';
import { CommonTransaction } from 'store/transactions/types';
import { tNamespaced } from 'utils/i18nUtil';

import { useTransactionContext } from '../TransactionContext';

import '../DataStudioLineage.less';

const tn = tNamespaced('Transaction');

export type TransactionErrorsRendererProps = {
  transaction: CommonTransaction;
};

export function TransactionErrorsRenderer({ transaction }: TransactionErrorsRendererProps) {
  const { errors = {} } = transaction;
  const [queryParams] = useQueryParams<TransactionsTableQueryParams>();

  const { enableErrorsPanel } = useTransactionContext();

  const count = Object.keys(errors).length;

  const pluralErrorsString = tn('errors', { count });
  const singularErrorsString = tn('errors', { count });

  const showErrors = () => {
    enableErrorsPanel();
  };

  const searchParams = encodeObjectToSearchParams({
    ...queryParams,
    transactionDetail: transaction.id,
  });

  if (!errors) {
    return null;
  }

  return (
    <div className="data-studio-synri-changes-column">
      <Link to={`?${searchParams}`} className="aria-label" onClick={showErrors}>
        {count > 1 ? singularErrorsString : pluralErrorsString}
      </Link>
      <Spacer x="md" />
    </div>
  );
}

export const rendererWrapper = (_value: CommonTransaction['errors'], data: CommonTransaction) => (
  <TransactionErrorsRenderer transaction={data} />
);

export default TransactionErrorsRenderer;
