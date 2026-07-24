import { Link } from '@reach/router';
import { ColDef } from 'ag-grid-community';
import { useMemo } from 'react';

import AgTable, { ResizeColumnsCondition } from 'components/AgTable';
import { useI18nContext, withI18n } from 'components/I18nProvider';
import { Stack } from 'components/layout';
import { TranslatedText } from 'components/typography';
import { useEntityByApiName } from 'store/entity';
import { MergeSkipTransaction } from 'store/transactions';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

import { getTransactionId } from './common';

type MergeSkipTableProps = {
  transaction: MergeSkipTransaction;
};

const MergeSkipTable = ({ transaction }: MergeSkipTableProps) => {
  const { tn } = useI18nContext();

  const { data: entity, loading } = useEntityByApiName(transaction.entityName);

  const rowData = [transaction?.additionalInfo?.mergeSkipDetails?.filterCondition];

  const colDefs = useMemo<ColDef[]>(() => {
    return [
      {
        headerName: tn('headers.field'),
        cellRendererFramework: () => {
          return <span className="ag-cell-value">{tn('headers.skip_when')}</span>;
        },
        pinned: true,
      },
      {
        headerName: tn('headers.skip_value'),
        cellRendererFramework: ({ data }: { data?: string }) => {
          return <span className="ag-cell-value">{data ? data : '-'}</span>;
        },
      },
    ];
  }, [tn]);

  const syncariId = getTransactionId(transaction);

  const transactionUrl = entity
    ? makeUrl(RouteConstants.DATA_STUDIO_RECORD_FIELDS, {
        entityId: entity.id,
        recordId: syncariId,
      })
    : '';

  return (
    <Stack className="synri-transaction-detail-table-container" fill scrollOverflow>
      <Stack spacing="xxxs">
        <TranslatedText namespace="Common" text="syncari_id" />
        <Link to={transactionUrl}>{syncariId}</Link>
      </Stack>
      <AgTable
        columnDefs={colDefs}
        loading={loading}
        rowData={rowData}
        sizeColumnsToFit={ResizeColumnsCondition.WHEN_NARROWER}
        enableCellTextSelection
        suppressCellSelection
        suppressColumnVirtualisation
      />
    </Stack>
  );
};

export default withI18n(MergeSkipTable, 'TransactionDetailsPanel');
