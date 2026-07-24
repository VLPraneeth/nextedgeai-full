import { navigate, RouteComponentProps, useParams } from '@reach/router';
import { useMemo } from 'react';
import { uid } from 'react-uid';

import { ReactComponent as TransactionsIcon } from 'assets/icons/record-transactions.svg';
import I18nProvider from 'components/I18nProvider';
import { HStack, Stack } from 'components/layout';
import TransactionsTable, { DEFAULT_COLUMNS } from './TransactionsTable';
import { replaceItem } from 'utils/ArrayUtil';
import { useEntityRecord } from 'store/data-studio/hooks';
import Arrow from 'components/Arrow';
import { Icon } from 'antd';
import Button from 'components/Button';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';
import './DataStudioLineage.less';

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

  const { entity } = useEntityRecord({ entityId: props.entityId, recordId: recordId });
  const filtersId = uid(params);

  return (
    <div className="data-studio-lineage content-section">
      <I18nProvider namespace="Transaction">
        <Stack fill>
          <div className="data-studio-lineage-header">
            <Button
              type="default"
              onClick={() => navigate(makeUrl(RouteConstants.DATA_STUDIO_ENTITY, { entityId: props.entityId }))}>
              <Icon type="arrow-left" style={{ fontSize: '14px' }} />
              <span className="link-text">{entity?.displayName}</span>
            </Button>
            <span>
              Showing Lineage of <span className="lineage-id">{props.entityId}</span>
            </span>
          </div>
          <TransactionsTable key={filtersId} params={params} selectedColumns={columns} />
        </Stack>
      </I18nProvider>
    </div>
  );
};

export const lineagePageOption = {
  id: 'lineage',
  name: 'Lineage',
  icon: TransactionsIcon,
};

export default Lineage;
