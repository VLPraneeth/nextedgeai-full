import { Link } from '@reach/router';
import { ColDef, ITooltipParams } from 'ag-grid-community';
import { useMemo } from 'react';

import AgTable, { ResizeColumnsCondition } from 'components/AgTable';
import { useI18nContext, withI18n } from 'components/I18nProvider';
import { HStack, Stack } from 'components/layout';
import TransactionDate from 'components/renderers/TransactionDate';
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
        width: 300,
        minWidth: 300,
        maxWidth: 300,
        suppressSizeToFit: true,
        resizable: false,
        cellClass: 'truncate-first-column',
        tooltipValueGetter: (params: ITooltipParams) => tn('headers.skip_when'),
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
      <HStack spacing="lg">
        <Stack spacing="xxxs">
          <TranslatedText namespace="Common" text="created_at" />
          <div className="header-link">
            <TransactionDate text={transaction.createdAt} />
          </div>
        </Stack>
        <Stack spacing="xxxs">
          <TranslatedText namespace="Common" text="syncari_id" />
          <Link className="header-link" to={transactionUrl}>
            {syncariId}
          </Link>
        </Stack>
      </HStack>
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
