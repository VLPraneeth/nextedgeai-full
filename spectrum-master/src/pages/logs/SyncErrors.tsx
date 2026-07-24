//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { navigate, RouteComponentProps } from '@reach/router';
import { ColDef } from 'ag-grid-community';
import { Icon, Tooltip } from 'antd';
import { keyBy } from 'lodash';
import { useEffect, useMemo, useState } from 'react';

import AgTable, { DefaultPageSizeOptions, PageBasedPagination } from 'components/AgTable';
import Button from 'components/Button';
import FilterButton from 'components/filter-components/FilterButton';
import { useI18nContext, withI18n } from 'components/I18nProvider';
import { HStack, Stack } from 'components/layout';
import {
  agFrameworkComponentsFromRendererMap,
  defaultRendererMap,
  truncatedTextCopyRenderer,
  truncatedTextRenderer,
} from 'components/renderers';
import DateCellRenderer from 'components/renderers/DateCellRenderer';
import { LinkRenderer } from 'components/renderers/LinkRenderer';
import RouteSpin from 'components/RouteSpin';
import { Text } from 'components/typography';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { useQueryFilterValues } from 'hooks/useFiltersInQueryParams';
import useSyncariEntities from 'hooks/useSyncariEntities';
import useToastForFetchStatusChange from 'hooks/useToastForFetchStatusChange';
import { useWindowTitle } from 'hooks/windowTitle';
import { useLayoutContext } from 'pages/LayoutContext';
import { usePipelineErrorSystemFilter } from 'pages/sync-studio/pipeline-error/PipelineError.hooks';
import { getSyncErrors, getSyncErrorsByMessage, SyncErrorsParams } from 'store/logs/thunks';
import { SyncErrorRecord } from 'store/logs/types';
import AppConstants from 'utils/AppConstants';
import { t } from 'utils/i18nUtil';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

import { ExportSyncErrorsButton } from './export-sync-errors-button';
import SyncErrorsFilterPanel, { defaultSyncErrorFilters } from './SyncErrorsFilterPanel/SyncErrorsFilterPanel';

import './index.less';

const AgFrameworkComponents = agFrameworkComponentsFromRendererMap({
  ...defaultRendererMap,
  linkRenderer: (item: [SyncErrorRecord['syncariRecordId'], string | undefined]) => {
    const [id, url] = item;

    return <LinkRenderer text={id} url={url} />;
  },
  occuredTime: DateCellRenderer,
  truncatedText: truncatedTextRenderer((text: string) => text),
  truncatedTextCopy: truncatedTextCopyRenderer((text: string) => text),
});

interface SyncErrorsListProps {}

const SyncErrorsList = (props: RouteComponentProps<SyncErrorsListProps>) => {
  const { tc, tn } = useI18nContext();
  useWindowTitle(t('Reports.sync_errors'));

  const [filterPanelVisible, setFilterPanelVisible] = useState(false);

  const { filterValues: activeFilters, filterIsActive } = useQueryFilterValues<SyncErrorsParams>(
    defaultSyncErrorFilters
  );

  const [, setReloadKey] = useState(1);

  const [count, setCount] = useState(DefaultPageSizeOptions[1]);
  const [pageNumber, setPageNumber] = useState(0);

  const { data: syncariEntities, loading: entitiesLoading } = useSyncariEntities();

  const dispatch = useEnhancedDispatch();
  const { fetchStatus, listData: errorData } = useEnhancedSelector((state) => state.logs.syncErrors);

  const entitiesMap = useMemo(() => keyBy(syncariEntities, 'apiName'), [syncariEntities]);

  useToastForFetchStatusChange(fetchStatus, {
    error: tn('error_fetching_errors_list'),
  });

  const colDefs = useMemo(() => {
    return [
      'connectorName',
      'syncariEntityName',
      'syncariRecordId',
      'externalEntityName',
      'externalRecordId',
      'operation',
      'errorCode',
      'errorDetails',
      'occuredTime',
    ].map((colKey) => {
      const def: ColDef = {
        headerName: tn(`headers.${colKey}`),
        colId: colKey,
        field: colKey,
      };

      switch (colKey) {
        case 'occuredTime':
          def.cellRenderer = 'occuredTime';
          break;
        case 'syncariRecordId':
          def.cellRenderer = 'linkRenderer';
          def.valueGetter = ({ data: syncError }: { data: SyncErrorRecord }) => {
            const errorId = syncError.syncariRecordId;
            const entityId = entitiesMap?.[syncError.syncariEntityName ?? '']?.id;

            const url =
              errorId && entityId
                ? makeUrl(RouteConstants.DATA_STUDIO_RECORD_FIELDS, { entityId, recordId: errorId })
                : undefined;

            return [errorId, url];
          };
          break;
        case 'errorCode':
          def.cellRenderer = 'truncatedText';
          break;
        case 'errorDetails':
          def.cellRenderer = 'truncatedTextCopy';
          break;
      }

      return def;
    });
  }, [entitiesMap, tn]);

  const { setShowSystemFilter, showSystemFilter, queryParams, errorMessage } = usePipelineErrorSystemFilter();

  useEffect(() => {
    if (errorMessage) {
      dispatch(
        getSyncErrorsByMessage({
          ...queryParams,
          pageNumber,
          count,
        })
      );
    } else {
      dispatch(
        getSyncErrors({
          ...activeFilters,
          pageNumber,
          count,
        })
      );
    }
  }, [count, activeFilters, dispatch, errorMessage, pageNumber, queryParams]);

  const handlePageChange = (pageNumber: number, count?: number) => {
    if (count) {
      setCount(count);
    }

    setPageNumber(pageNumber);
  };

  const isLoading = fetchStatus === AppConstants.FETCH_STATUS.LOADING;

  const layout = useLayoutContext();

  if (entitiesLoading) {
    return <RouteSpin />;
  }

  return (
    <Stack fill spacing="lg" className="sync-errors">
      {showSystemFilter && (
        <div className="sync-errors--filters-container filters-container">
          <Stack>
            <Text
              color="gray-900"
              className="sync-errors--filters-container--filter-text"
              style={{ maxWidth: layout.dimensions.content.width - 220 }}>
              <Text weight="bold">{t('PipelineErrorState.filter_colon')}</Text>
              <Tooltip placement="bottomLeft" autoAdjustOverflow title={errorMessage}>
                {errorMessage}
              </Tooltip>
            </Text>
            <Button
              className="sync-errors--filters-container--clear-filter"
              type="primary"
              onClick={() => {
                navigate(RouteConstants.LOGS_SYNC_ERRORS);
                setShowSystemFilter(false);
              }}>
              <Icon type="close-circle" />
              {tc('clear_filter')}
            </Button>
          </Stack>
        </div>
      )}
      <div className="sync-errors__table-actions">
        <HStack spacing="md">
          <ExportSyncErrorsButton dataParams={activeFilters} />
          <FilterButton onClick={() => setFilterPanelVisible(!filterPanelVisible)} isFilterActive={filterIsActive} />
        </HStack>
      </div>
      <AgTable
        frameworkComponents={AgFrameworkComponents}
        columnDefs={colDefs}
        rowData={errorData?.records}
        loading={isLoading}
        enableCellTextSelection
        suppressCellSelection
        suppressRowClickSelection
        // There is not a uniqueId we can derive from SyncErrorRecord. We could
        // have multiple records with exactly the same information so we have to
        // turn off immutableData to prevent rows from being stuck in the table.
        immutableData={false}
        noRowsOverlayComponentProps={filterIsActive ? { description: tn('no_data_matches_filter') } : undefined}
        pagerComponent={
          <PageBasedPagination
            allowPageSizeChange
            onPageSizeChange={setCount}
            onRequestNextPage={handlePageChange}
            onRequestPreviousPage={handlePageChange}
            pageInfo={errorData?.pageInfo}
            pageSize={count}
          />
        }
      />
      <SyncErrorsFilterPanel
        visible={filterPanelVisible}
        onClose={() => setFilterPanelVisible(false)}
        onFilterUpdate={() => setReloadKey(Math.random())}
      />
    </Stack>
  );
};

export default withI18n(SyncErrorsList, 'Reports.SyncErrors');
