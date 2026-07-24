import { Link } from '@reach/router';
import { ColDef } from 'ag-grid-community';
import { useMemo } from 'react';

import AgTable, { ResizeColumnsCondition } from 'components/AgTable';
import { useI18nContext, withI18n } from 'components/I18nProvider';
import { Stack } from 'components/layout';
import { TranslatedText } from 'components/typography';
import { EMPTY_OBJECT } from 'store/constants';
import { useEntityByApiName } from 'store/entity';
import { CommonTransaction, TransactionChange } from 'store/transactions';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

import { getTransactionId } from '../common';
import { ExternalValuesRenderer, FieldNameRenderer, OldNewValuesRenderer } from './common';

import '../TransactionDetailsPanel.less';

const components = {
  fieldName: FieldNameRenderer,
  externalValues: ExternalValuesRenderer,
  oldNewValues: OldNewValuesRenderer,
};

type TransactionDetailTableProps = {
  transaction: CommonTransaction;
};

const SINGLE_ROW_HEIGHT = 28;
const MULTI_ROW_HEIGHT = 24;

const TransactionDetailTable = ({ transaction }: TransactionDetailTableProps) => {
  const { tn } = useI18nContext();
  const { data: entity, loading } = useEntityByApiName(transaction.entityName);

  const rowData = Object.values(transaction?.changes || EMPTY_OBJECT);

  const colDefs = useMemo<ColDef[]>(() => {
    return [
      {
        headerName: tn('headers.field'),
        cellRenderer: 'fieldName',
        colId: 'fieldId',
        field: 'fieldId',
        valueGetter: ({ data }: { data: TransactionChange }) => {
          try {
            // if we already have the data we need, return
            if (data.dataType && data.displayName) {
              return data;
            }

            const field = entity?.fields.find((f) => f.apiName === data.apiName || f.id === data.fieldId);

            // We can't find the field, so we'll something sensible
            if (!field) {
              return {
                ...data,
                displayName: data.apiName,
                dataType: 'string',
              };
            }

            return {
              ...data,
              displayName: field.displayName,
              dataType: field.dataType,
            };
          } catch (err) {
            return data;
          }
        },
        pinned: true,
      },
      {
        autoHeight: true,
        headerName: tn('headers.sourceValue'),
        cellRenderer: 'externalValues',
        colId: 'incomingExternalValues',
        field: 'incomingExternalValues',
        valueGetter: ({ data }: { data: TransactionChange }) => {
          if (data?.incomingExternalValues) {
            return Object.values(data.incomingExternalValues);
          }
          return;
        },
      },
      {
        headerName: tn('headers.oldValue'),
        colId: 'oldValue',
        field: 'oldValue',
        cellRenderer: 'oldNewValues',
      },
      {
        headerName: tn('headers.newValue'),
        colId: 'newValue',
        field: 'newValue',
        cellRenderer: 'oldNewValues',
      },
      {
        autoHeight: true,
        headerName: tn('headers.authoritativeValue'),
        cellRenderer: 'externalValues',
        colId: 'authoritativeSource',
        field: 'authoritativeSource',
        valueGetter: ({ data }: { data: TransactionChange }) => {
          if (data?.authoritativeSource) {
            return [data.authoritativeSource];
          }
          return;
        },
      },
      {
        autoHeight: true,
        headerName: tn('headers.destinationValue'),
        cellRenderer: 'externalValues',
        flex: 1,
        colId: 'outgoingExternalValues',
        field: 'outgoingExternalValues',
        valueGetter: ({ data }: { data: TransactionChange }) => {
          if (data?.outgoingExternalValues) {
            return Object.values(data.outgoingExternalValues).map((externalValue) => {
              // We're adding the timestamp down to the cell level if the external value is an array.
              // Note that ourgoing external value of a change transaction can be string, array or an object.
              if (typeof externalValue !== 'string' && !Array.isArray(externalValue) && 'apiName' in externalValue) {
                return {
                  ...externalValue,
                  timestamp: data.timestamp,
                };
              }
              return externalValue;
            });
          }
          return;
        },
      },
    ];
  }, [entity?.fields, tn]);

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
        getRowHeight={(params: { data: TransactionChange }) => {
          const rowCount = Math.max(
            (Object.keys(params.data?.incomingExternalValues || {}) || []).length,
            (Object.keys(params.data?.outgoingExternalValues || {}) || []).length
          );
          return rowCount > 1 ? rowCount * MULTI_ROW_HEIGHT : SINGLE_ROW_HEIGHT;
        }}
        sizeColumnsToFit={ResizeColumnsCondition.WHEN_NARROWER}
        enableCellTextSelection
        suppressCellSelection
        suppressColumnVirtualisation
      />
    </Stack>
  );
};

export default withI18n(TransactionDetailTable, 'TransactionDetailsPanel');
