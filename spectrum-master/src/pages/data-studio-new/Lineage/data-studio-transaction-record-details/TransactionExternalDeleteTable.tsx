import { Link } from '@reach/router';
import { ColDef, ITooltipParams } from 'ag-grid-community';
import { useMemo, useState } from 'react';

import AgTable, { ResizeColumnsCondition } from 'components/AgTable';
import { useI18nContext, withI18n } from 'components/I18nProvider';
import { InlineTab, InlineTabs } from 'components/InlineTabs';
import { HStack, Stack } from 'components/layout';
import TransactionDate from 'components/renderers/TransactionDate';
import { TranslatedText } from 'components/typography';
import { useEntityByApiName } from 'store/entity';
import { ExternalDeleteTransaction } from 'store/transactions';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

import { getTransactionId } from '../common';
import {
  ExternalValuesRenderer,
  TransactionFieldUpdate,
  TransactionMergeFieldRenderer,
  TransactionMergePredicateRenderer,
} from './common';

import '../DataStudioLineagePanel.less';

const mergeComponents = {
  fieldName: TransactionMergeFieldRenderer,
  externalValues: ExternalValuesRenderer,
  predicate: TransactionMergePredicateRenderer,
};

type TransactionExternalDeleteTableProps = {
  transaction: ExternalDeleteTransaction;
};

interface DisconnectedSource {
  sourceId: string;
  synapseName: string;
  displayName: string;
}

const TransactionExternalDeleteTable = ({ transaction }: TransactionExternalDeleteTableProps) => {
  const { tc, tn } = useI18nContext();
  const { data: entity, loading } = useEntityByApiName(transaction.entityName);

  const [tab, setTab] = useState('deleted_record');

  const syncariId = getTransactionId(transaction);

  const transactionUrl = entity
    ? makeUrl(RouteConstants.DATA_STUDIO_RECORD_FIELDS, {
        entityId: entity.id,
        recordId: syncariId,
      })
    : '';

  const colDefs = useMemo<ColDef[]>(() => {
    if (tab === 'deleted_record') {
      return [
        {
          headerName: tn('headers.field'),
          cellRenderer: 'fieldName',
          colId: 'field',
          field: 'field',
          width: 300,
          minWidth: 300,
          maxWidth: 300,
          suppressSizeToFit: true,
          resizable: false,
          cellClass: 'truncate-first-column',
          valueGetter: ({ data }: { data: TransactionFieldUpdate }) => data.field,
          tooltipValueGetter: (params: ITooltipParams) => {
            const data = params.data as TransactionFieldUpdate;
            return data?.field?.displayName ? `${data.field.displayName} (${data.field.apiName})` : '';
          },
        },
        {
          headerName: tn('headers.value'),
          flex: 1,
          colId: 'value',
          field: 'value',
        },
      ];
    } else {
      return [
        {
          headerName: tn('headers.source_id'),
          colId: 'sourceId',
          field: 'sourceId',
          width: 300,
          minWidth: 300,
          maxWidth: 300,
          suppressSizeToFit: true,
          resizable: false,
          cellClass: 'truncate-first-column',
          tooltipValueGetter: (params: ITooltipParams) => params.value || '',
        },
        {
          headerName: tc('synapse_name'),
          flex: 1,
          colId: 'synapseName',
          field: 'synapseName',
        },
        {
          headerName: tc('name'),
          flex: 1,
          colId: 'displayName',
          field: 'displayName',
        },
      ];
    }
  }, [tab, tc, tn]);

  const rowData: TransactionFieldUpdate[] | DisconnectedSource[] = useMemo(() => {
    const deleteInfo = transaction.additionalInfo.deleteInfo;
    if (tab === 'deleted_record') {
      const deletedId = deleteInfo.deletedId;
      return [
        {
          field: {
            apiName: '',
            displayName: tc('name'),
            dataType: 'string',
          },
          value: `${deletedId.displayName} (${deletedId.apiName})`,
          mergePolicy: undefined,
        },
        {
          field: {
            apiName: '',
            displayName: tn('deleted_record_id'),
            dataType: 'string',
          },
          value: deletedId.id,
          mergePolicy: undefined,
        },
        {
          field: {
            apiName: '',
            displayName: tc('synapse_name'),
            dataType: 'string',
          },
          value: deletedId.connectorName,
          mergePolicy: undefined,
        },
      ];
    } else {
      const disconnectedSources = deleteInfo.disconnectedSources;
      if (!disconnectedSources.length) {
        return [];
      }
      return disconnectedSources.map((source) => {
        return {
          sourceId: source.id,
          synapseName: source.connectorName,
          displayName: `${source.displayName} (${source.displayName})`,
        };
      });
    }
  }, [tab, tc, tn, transaction.additionalInfo.deleteInfo]);

  return (
    <Stack fill>
      <InlineTabs selectedTab={tab} onChange={setTab}>
        <InlineTab id="deleted_record">
          <TranslatedText text="deleted_record" />
        </InlineTab>
        <InlineTab id="disconnected_sources">
          <TranslatedText text="disconnected_sources" />
        </InlineTab>
      </InlineTabs>
      <Stack className="synri-transaction-detail-table-container transaction-merge-table-container" fill scrollOverflow>
        {tab === 'deleted_record' && (
          <HStack className="merge-detail-header" justify="space-between">
            <Stack spacing="xxxs">
              <TranslatedText namespace="DataTypes" text="date_time" color="gray-700" />
              <div className="header-link">
                <TransactionDate text={transaction.createdAt} />
              </div>
            </Stack>
            <Stack spacing="xxxs">
              <HStack spacing="xxsm">
                <TranslatedText namespace="Common" text="syncari_id_label" color="gray-800" weight="bold" />
                {syncariId && entity?.id ? (
                  <Link className="header-link" to={transactionUrl}>
                    {transaction.syncariId}
                  </Link>
                ) : (
                  <TranslatedText text="record_is_deleted" color="gray-700" args={{ id: transaction.syncariId }} />
                )}
              </HStack>
              <HStack spacing="xxsm">
                <TranslatedText text="syncari_deleted" color="gray-800" weight="bold" />
                <TranslatedText
                  namespace="Common"
                  text={transaction.additionalInfo.deleteInfo.syncariDeleted ? 'yes' : 'no'}
                  color="gray-800"
                  weight="bold"
                />
              </HStack>
            </Stack>
          </HStack>
        )}
        <AgTable
          columnDefs={colDefs}
          frameworkComponents={mergeComponents}
          getRowNodeId={(row: TransactionFieldUpdate | DisconnectedSource) =>
            'field' in row ? row.field.displayName : row.sourceId
          }
          loading={loading}
          rowData={rowData}
          sizeColumnsToFit={ResizeColumnsCondition.WHEN_NARROWER}
          enableCellTextSelection
          suppressCellSelection
          suppressColumnVirtualisation
        />
      </Stack>
    </Stack>
  );
};

export default withI18n(TransactionExternalDeleteTable, 'TransactionDetailsPanel');
