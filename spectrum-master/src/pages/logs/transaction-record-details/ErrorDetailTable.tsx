import { Link } from '@reach/router';
import { ColDef } from 'ag-grid-community';
import Tooltip from 'antd/lib/tooltip';
import { useMemo } from 'react';

import AgTable, { ResizeColumnsCondition } from 'components/AgTable';
import { EnhancedAgCellRendererParams } from 'components/renderers';
import { useI18nNamespace } from 'components/I18nProvider';
import { Stack } from 'components/layout';
import { Text } from 'components/typography';
import { TranslatedText } from 'components/typography';
import { EMPTY_OBJECT } from 'store/constants';
import { useEntityByApiName } from 'store/entity';
import { CommonTransaction, TransactionChange } from 'store/transactions';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

import { getTransactionId } from '../common';
import '../TransactionDetailsPanel.less';

export type ErrorDetailTableProps = {
  transaction: CommonTransaction;
};

const ErrorMessageRenderer = ({ value }: EnhancedAgCellRendererParams<string>) => {
  if (!value) {
    return null;
  }

  return (
    <Tooltip title={value} placement="topLeft" mouseEnterDelay={0.5}>
      <Text className="error-message-cell">{value}</Text>
    </Tooltip>
  );
};

const SimpleTextRenderer = ({ value }: EnhancedAgCellRendererParams<string>) => {
  return value ? <Text>{value}</Text> : null;
};

const ErrorDetailTable = ({ transaction }: ErrorDetailTableProps) => {
  const tn = useI18nNamespace('TransactionDetailsPanel');
  const { data: entity, loading } = useEntityByApiName(transaction.entityName);

  const rowData = Object.values(transaction?.errors || EMPTY_OBJECT);

  const components = useMemo(
    () => ({
      error: ErrorMessageRenderer,
      graphId: SimpleTextRenderer,
      graphName: SimpleTextRenderer,
      scope: SimpleTextRenderer,
      nodeId: SimpleTextRenderer,
      nodeName: SimpleTextRenderer,
    }),
    []
  );

  const colDefs = useMemo<ColDef[]>(() => {
    return [
      {
        autoHeight: true,
        headerName: tn('errorTableHeaders.graphId'),
        flex: 1,
        cellRenderer: 'graphId',
        colId: 'graphId',
        field: 'graphId',
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
      <Stack spacing="xxxs">
        <TranslatedText namespace="Common" text="syncari_id" />
        <Link to={transactionUrl}>{syncariId}</Link>
      </Stack>
      <AgTable
        columnDefs={colDefs}
        frameworkComponents={components}
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
