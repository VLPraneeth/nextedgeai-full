import { RouteComponentProps, useParams } from '@reach/router';
import { useMemo } from 'react';
import { uid } from 'react-uid';

import { ReactComponent as TransactionsIcon } from 'assets/icons/record-transactions.svg';
import I18nProvider from 'components/I18nProvider';
import { Stack } from 'components/layout';
import TransactionsTable, { DEFAULT_COLUMNS } from 'pages/logs/TransactionsTable';
import { replaceItem } from 'utils/ArrayUtil';

const columns = replaceItem(DEFAULT_COLUMNS, DEFAULT_COLUMNS.indexOf('syncariId'), 'transactionId');

const Lineage = (props: RouteComponentProps & { entityId: string }) => {
  const { recordId } = useParams();
  const params = useMemo(
    () => ({
      entityName: '',
      entityId: props.entityId,
      syncariId: recordId,
    }),
    [props.entityId, recordId]
  );

  const filtersId = uid(params);

  return (
    <I18nProvider namespace="Transaction">
      <Stack fill>
        <TransactionsTable key={filtersId} params={params} selectedColumns={columns} />
      </Stack>
    </I18nProvider>
  );
};

export const lineagePageOption = {
  id: 'lineage',
  name: 'Lineage',
  icon: TransactionsIcon,
};

export default Lineage;
