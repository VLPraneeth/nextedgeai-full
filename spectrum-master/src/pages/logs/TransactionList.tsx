//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { RouteComponentProps, useMatch } from '@reach/router';
import { message, Tooltip } from 'antd';
import Icon from 'antd/lib/icon';
import { useCallback, useEffect, useMemo, useState } from 'react';

import { ReactComponent as ExportIcon } from 'assets/icons/export.svg';
import Button from 'components/Button';
import FilterButton from 'components/filter-components/FilterButton';
import { useI18nContext, withI18n } from 'components/I18nProvider';
import { HStack, Stack } from 'components/layout';
import Redirect from 'components/Redirect';
import StatBlock from 'components/StatBlock';
import { Text } from 'components/typography';
import useUserLocalMoment from 'hooks/moment';
import { useQueryFilterValues } from 'hooks/useFiltersInQueryParams';
import { useTranslatedWindowTitle } from 'hooks/windowTitle';
import { useLayoutContext } from 'pages/LayoutContext';
import { usePipelineErrorSystemFilter } from 'pages/sync-studio/pipeline-error/PipelineError.hooks';
import { ALL_ENTITIES_VALUE, ALL_OPERATIONS_VALUE, TransactionsParams, useGetKpisQuery } from 'store/transactions';
import DataUrlConstants from 'utils/DataUrlConstants';
import { serializeMomentFields } from 'utils/DateUtil';
import { downloadFile } from 'utils/DownloadUtil';
import { numberFormat } from 'utils/i18nUtil';
import { wrapIcon } from 'utils/IconUtils';
import RouteConstants from 'utils/RouteConstants';
import { safeDecodeURIComponent } from 'utils/StringUtil';
import { isNotNullOrUndefined } from 'utils/TypeUtils';
import { makeUrl } from 'utils/UrlUtil';

import LogsFilterPanel from './LogsFilterPanel/LogsFilterPanel';
import TransactionsTable from './TransactionsTable';
import { DraftTransactionsParams } from './types';

import './Transactions.less';

const FILTER_RESERVED_WIDTH = 220;

export const LOGS_DEFAULT_DAYS_RANGE = 6;

const cleanApiFilters = (filters: DraftTransactionsParams) => {
  const cleanFilters = serializeMomentFields(filters) as TransactionsParams;

  if (cleanFilters.entityName === ALL_ENTITIES_VALUE) {
    cleanFilters.entityName = null;
  }

  if (cleanFilters.operation === ALL_OPERATIONS_VALUE) {
    cleanFilters.operation = null;
  }
  return cleanFilters;
};

const TransactionsList = ({ navigate }: RouteComponentProps) => {
  const { tc, tn } = useI18nContext();
  const moment = useUserLocalMoment();

  useTranslatedWindowTitle('title');

  const [filterPanelVisible, setFilterPanelVisible] = useState(false);

  const { filterValues: activeFilters, filterIsActive } = useQueryFilterValues<DraftTransactionsParams>({
    entityName: ALL_ENTITIES_VALUE,
    operation: ALL_OPERATIONS_VALUE,
    startDate: moment().subtract(LOGS_DEFAULT_DAYS_RANGE, 'days').startOf('day'),
    syncariId: '',
  });

  const { data: transactionKpis, error, isFetching: isFetchingKpis } = useGetKpisQuery(cleanApiFilters(activeFilters));

  useEffect(() => {
    if (error) {
      console.error('message' in error ? error.message : 'data' in error ? error.data : 'Error fetching KPIs');
      message.error(tn('unable_to_fetch_kpis'));
    }
  }, [error, tn]);

  const transactionsSelected = useMatch(RouteConstants.LOGS_TRANSACTIONS);
  const syncErrorsSelected = useMatch(RouteConstants.LOGS_SYNC_ERRORS);

  const subMenuSelected = transactionsSelected || syncErrorsSelected;

  useTranslatedWindowTitle('title');

  const exportData = useCallback(() => {
    downloadFile(
      makeUrl(
        DataUrlConstants.DOWNLOAD_TXN_RECORDS_LIST,
        {},
        {
          startDate: activeFilters.startDate.toISOString(),
          endDate: activeFilters.endDate.toISOString(),
          entityName: activeFilters.entityName,
          operation: activeFilters.operation,
        }
      )
    );
  }, [activeFilters.endDate, activeFilters.entityName, activeFilters.operation, activeFilters.startDate]);

  const stats = useMemo(() => {
    return [
      {
        label: tn('transactions'),
        value: transactionKpis?.transactions ?? 0,
      },
      {
        label: tn('newRecords'),
        value: transactionKpis?.newRecords,
      },
      {
        label: tn('updatedRecords'),
        value: transactionKpis?.updateRecords,
      },
    ]
      .filter((stat) => isNotNullOrUndefined(stat.value))
      .map((stat) => ({
        ...stat,
        value: numberFormat(stat.value),
      }));
  }, [tn, transactionKpis]);

  const layout = useLayoutContext();

  // when the activeFilters change, we want to ensure the Table is invalidated
  const [reloadKey, setReloadKey] = useState(1);

  const filtersId = useMemo(() => {
    return `${reloadKey}_${JSON.stringify(activeFilters)}`;
  }, [activeFilters, reloadKey]);

  const { setShowSystemFilter, showSystemFilter, errorMessage } = usePipelineErrorSystemFilter();

  if (!subMenuSelected) {
    // if we don't have an entity selection,
    // redirect to the first Entity in our entities list
    return <Redirect redirectTo={makeUrl(RouteConstants.LOGS_TRANSACTIONS)} replace />;
  }

  const exportDisabled =
    activeFilters.entityName === ALL_ENTITIES_VALUE ||
    !activeFilters?.operation ||
    (activeFilters?.operation && !['merge', 'merge_report_only', 'merge_skip'].includes(activeFilters.operation));

  return (
    <>
      <Stack className="synri-transactions-container" fill spacing="lg">
        <HStack justify="space-between">
          <HStack>
            {stats.map((stat) => (
              <StatBlock value={isFetchingKpis ? '-' : stat.value} label={stat.label} />
            ))}
          </HStack>
          <HStack spacing="md">
            <Tooltip placement="bottom" autoAdjustOverflow title={exportDisabled ? tn('export_disabled') : ''}>
              <span>
                <Button disabled={exportDisabled} aria-label={tn('export')} onClick={exportData}>
                  <Icon component={wrapIcon(ExportIcon)} className="export-button-icon" />
                  {tn('export')}
                </Button>
              </span>
            </Tooltip>
            <FilterButton onClick={() => setFilterPanelVisible(!filterPanelVisible)} isFilterActive={filterIsActive} />
          </HStack>
        </HStack>
        {showSystemFilter && (
          <Stack>
            <Text
              color="gray-900"
              className="transactions-filters-container--filter-text"
              style={{ maxWidth: layout.dimensions.content.width - FILTER_RESERVED_WIDTH }}>
              <Text weight="bold">{tc('filter_colon')}</Text>
              <Tooltip placement="bottomLeft" autoAdjustOverflow title={safeDecodeURIComponent(errorMessage || '')}>
                {safeDecodeURIComponent(errorMessage || '')}
              </Tooltip>
            </Text>

            <Button
              type="primary"
              onClick={() => {
                setShowSystemFilter(false);
                navigate?.('/logs/transactions/');
              }}>
              <Icon type="close-circle" style={{ color: 'white' }} />
              {tc('clear_filter')}
            </Button>
          </Stack>
        )}

        <TransactionsTable
          key={filtersId}
          params={cleanApiFilters(activeFilters)}
          agTableProps={{
            noRowsOverlayComponentProps: filterIsActive ? { description: tn('no_data_matches_filter') } : undefined,
          }}
        />
      </Stack>
      <LogsFilterPanel
        visible={filterPanelVisible}
        onClose={() => setFilterPanelVisible(false)}
        onFilterUpdate={() => setReloadKey(Math.random())}
      />
    </>
  );
};

export default withI18n(TransactionsList, 'Transaction');
