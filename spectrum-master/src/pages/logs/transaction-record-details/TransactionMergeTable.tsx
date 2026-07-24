import { Link } from '@reach/router';
import { ColDef } from 'ag-grid-community';
import { sortBy } from 'lodash';
import { useMemo, useState } from 'react';

import AgTable, { ResizeColumnsCondition } from 'components/AgTable';
import { useI18nContext, withI18n } from 'components/I18nProvider';
import { InlineTab, InlineTabs } from 'components/InlineTabs';
import { HStack, Stack } from 'components/layout';
import SelectInput from 'components/SelectInput';
import { TranslatedText } from 'components/typography';
import { useEntityByApiName } from 'store/entity';
import { MergeTransaction } from 'store/transactions';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

import { getTransactionId } from '../common';
import {
  ExternalValuesRenderer,
  TransactionFieldUpdate,
  TransactionMergeFieldRenderer,
  TransactionMergePredicateRenderer,
} from './common';

import '../TransactionDetailsPanel.less';

const mergeComponents = {
  fieldName: TransactionMergeFieldRenderer,
  externalValues: ExternalValuesRenderer,
  predicate: TransactionMergePredicateRenderer,
};

type TransactionMergeTableProps = {
  transaction: MergeTransaction;
};

const TransactionMergeTable = ({ transaction }: TransactionMergeTableProps) => {
  const { tn } = useI18nContext();
  const { data: entity, loading } = useEntityByApiName(transaction.entityName);

  const [tab, setTab] = useState('winning');
  const [losingRecordId, setLosingRecordId] = useState(
    () => transaction.additionalInfo.mergeDetails.losingRecords[0].id
  );

  const syncariId = getTransactionId(transaction);

  const transactionUrl = entity
    ? makeUrl(RouteConstants.DATA_STUDIO_RECORD_FIELDS, {
        entityId: entity.id,
        recordId: syncariId,
      })
    : '';

  const colDefs = useMemo<ColDef[]>(() => {
    return [
      {
        headerName: tn('headers.field'),
        cellRenderer: 'fieldName',
        colId: 'field',
        field: 'field',
        flex: 1,
        valueGetter: ({ data }: { data: TransactionFieldUpdate }) => data.field,
      },
      {
        headerName: tn('headers.value'),
        flex: 1,
        colId: 'value',
        field: 'value',
      },
      // Dedupe Merge WIP
      // {
      //   autoHeight: true,
      //   headerName: tn('headers.fieldLevelMergePolicy'),
      //   cellRenderer: 'predicate',
      //   flex: 1,
      //   colId: 'mergePolicy',
      //   field: 'mergePolicy',
      //   valueGetter: ({ data }: { data: TransactionFieldUpdate }) => {
      //     return data.mergePolicy;
      //   },
      // },
    ];
  }, [tn]);

  const rowData: TransactionFieldUpdate[] = useMemo(() => {
    const rows =
      tab === 'winning'
        ? Object.entries(transaction.additionalInfo.mergeDetails.winningRecord.values || {})
        : Object.entries(
            transaction.additionalInfo.mergeDetails.losingRecords.find((r) => r.id === losingRecordId)?.values || {}
          );

    const updatedRows: TransactionFieldUpdate[] = rows
      .filter(([key]) => key !== '_id')
      .map(([key, { displayName, dataType, value }]) => {
        const mergePolicy = transaction.additionalInfo.mergeDetails?.mergeInfo?.fieldMergePolicies?.[key];
        return {
          field: {
            apiName: key,
            displayName,
            dataType,
          },
          value,
          mergePolicy,
        };
      });

    return sortBy(updatedRows, ['field.displayName']);
  }, [losingRecordId, tab, transaction]);

  const losingRecordOptions = transaction.additionalInfo.mergeDetails.losingRecords.map((r, idx) => ({
    label: r.id,
    value: r.id,
  }));

  return (
    <Stack fill>
      <InlineTabs selectedTab={tab} onChange={setTab}>
        <InlineTab id={'winning'}>
          <TranslatedText text="winning_record" />
        </InlineTab>
        <InlineTab id={'losing'}>
          <TranslatedText text="losing_record" />
        </InlineTab>
      </InlineTabs>
      <Stack className="synri-transaction-detail-table-container transaction-merge-table-container" fill scrollOverflow>
        {tab === 'losing' ? (
          losingRecordOptions.length > 1 && (
            <Stack spacing="xxxs">
              <TranslatedText namespace="Common" text="syncari_id" color="gray-800" weight="bold" />
              <SelectInput options={losingRecordOptions} onChange={setLosingRecordId} value={losingRecordId} />
            </Stack>
          )
        ) : (
          <HStack className="merge-detail-header" justify="space-between">
            <Stack spacing="xxxs">
              <TranslatedText namespace="Common" text="syncari_id" color="gray-800" weight="bold" />
              {syncariId && entity?.id ? (
                <Link to={transactionUrl}>{transaction.additionalInfo.mergeDetails.winningRecord.id}</Link>
              ) : (
                <TranslatedText
                  text="record_is_deleted"
                  color="gray-700"
                  args={{ id: transaction.additionalInfo.mergeDetails.winningRecord.id }}
                />
              )}
            </Stack>
            {/* Dedupe Merge WIP */}
            {/* <Stack spacing="xxxs">
              <TranslatedText text="deduplicationRule" color="gray-800" weight="bold" />
              <TransactionMergePredicate
                name="deduplicationRule"
                value={transaction.additionalInfo.mergeDetails.mergeInfo?.duplicateSelector}
              />
            </Stack>
            <Stack spacing="xxxs">
              <TranslatedText text="selectWinnerPredicate" color="gray-800" weight="bold" />
              <TransactionMergePredicate
                name="selectWinnerPredicate"
                value={transaction.additionalInfo.mergeDetails.mergeInfo?.winnerSelectorPredicate}
              />
            </Stack>
            {transaction.additionalInfo.mergeDetails.mergeInfo && (
              <Stack spacing="xxxs">
                <TranslatedText text="defaultMergePolicy" color="gray-800" weight="bold" />
                <Text color="gray-1000">
                  {transaction.additionalInfo.mergeDetails.mergeInfo.winnerOverridePolicy.label}
                </Text>
              </Stack>
            )}
            */}
          </HStack>
        )}
        <AgTable
          columnDefs={colDefs}
          frameworkComponents={mergeComponents}
          getRowNodeId={(row: TransactionFieldUpdate) => row.field.apiName}
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

export default withI18n(TransactionMergeTable, 'TransactionDetailsPanel');
