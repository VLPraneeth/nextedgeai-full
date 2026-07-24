import { Link } from '@reach/router';
import { ColDef, ITooltipParams } from 'ag-grid-community';
import { useMemo } from 'react';

import AgTable, { ResizeColumnsCondition } from 'components/AgTable';
import { useI18nNamespace } from 'components/I18nProvider';
import { HStack, Stack } from 'components/layout';
import TransactionDate from 'components/renderers/TransactionDate';
import { TranslatedText } from 'components/typography';
import { EMPTY_OBJECT } from 'store/constants';
import { useEntityByApiName } from 'store/entity';
import { CommonTransaction, TransactionChange } from 'store/transactions';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

import { getTransactionId } from '../common';
import '../DataStudioLineagePanel.less';

export type ErrorDetailTableProps = {
  transaction: CommonTransaction;
};

const ErrorDetailTable = ({ transaction }: ErrorDetailTableProps) => {
  const tn = useI18nNamespace('TransactionDetailsPanel');
  const { data: entity, loading } = useEntityByApiName(transaction.entityName);

  const rowData = Object.values(transaction?.errors || EMPTY_OBJECT);

  const colDefs = useMemo<ColDef[]>(() => {
    return [
      {
        autoHeight: true,
        headerName: tn('errorTableHeaders.graphId'),
        width: 300,
        minWidth: 300,
        maxWidth: 300,
        suppressSizeToFit: true,
        resizable: false,
        cellRenderer: 'graphId',
        colId: 'graphId',
        field: 'graphId',
        cellClass: 'truncate-first-column',
        tooltipValueGetter: (params: ITooltipParams) => params.value || '',
      },
      {
        autoHeight: true,
        headerName: tn('errorTableHeaders.graphName'),
        flex: 1,
        cellRenderer: 'graphName',
        colId: 'graphName',
        field: 'graphName',
      },
      {
        autoHeight: true,
        headerName: tn('errorTableHeaders.scope'),
        flex: 1,
        cellRenderer: 'scope',
        colId: 'scope',
        field: 'scope',
      },
      {
        autoHeight: true,
        headerName: tn('errorTableHeaders.nodeId'),
        flex: 1,
        cellRenderer: 'nodeId',
        colId: 'nodeId',
        field: 'nodeId',
      },
      {
        headerName: tn('errorTableHeaders.nodeName'),
        flex: 1,
        colId: 'nodeName',
        field: 'nodeName',
        cellRenderer: 'nodeName',
      },
      {
        autoHeight: true,
        headerName: tn('errorTableHeaders.error'),
        cellRenderer: 'error',
        flex: 1,
        colId: 'error',
        field: 'error',
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
          <TranslatedText namespace="DataTypes" text="date_time" />
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
        getRowNodeId={(row: TransactionChange) => String(row.fieldId || row.timestamp)}
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

export default ErrorDetailTable;
