//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { Tooltip } from 'antd';
import { useEffect, useMemo, useState } from 'react';

import AgTable, { ResizeColumnsCondition } from 'components/AgTable';
import { CursorBasedPagination } from 'components/AgTable';
import { CursorPageInfo } from 'components/AgTable/Pagination';
import { Stack } from 'components/layout';
import { agFrameworkComponentsFromRendererMap, defaultRendererMap } from 'components/renderers';
import { useCursorPagination } from 'hooks/pagination';
import { useUnifiedDataCardNavigate } from 'pages/insights-studio/utils/useUnifiedDataCardNavigate';
import { useGetDashboardsQuery, useGetDashDataCardWithConfigPageMutation } from 'store/insights-studio';
import { DataRow } from 'store/insights-studio/types';

import { VizerDisplayFormat, VizerProps } from '../types';
import { useTableVizer } from '../utils/useTableVizer';
import { displayFormatter } from '../utils/VizerDisplayFormatter';

import './VizerTable.less';

interface TableCellProps {
  value: string;
}

const TableCell = ({ value }: TableCellProps) => (
  <Tooltip mouseEnterDelay={1} title={value}>
    <span className="vizer-table-cell-value">{value}</span>
  </Tooltip>
);

const VizerTable = ({ configuration, data, height, dataCardId }: VizerProps) => {
  const { columns, rowData } = useTableVizer({ configuration, data, height });
  const [fetchPageData, { isLoading }] = useGetDashDataCardWithConfigPageMutation();
  const [pagedData, setPagedData] = useState<DataRow[]>([]);
  const { getCurrentDashboard, dashboardVersionMatch } = useUnifiedDataCardNavigate();
  const { dashboardId } = getCurrentDashboard();
  const { data: dashboardsData } = useGetDashboardsQuery(undefined, {
    skip: dashboardVersionMatch?.version !== 'draft',
  });

  const [currentDashboardId] = useState(dashboardId);
  const [currentDashboardVersionMatch] = useState(dashboardVersionMatch);

  const memoData = useMemo(() => (pagedData?.length ? pagedData : rowData), [pagedData, rowData]);

  const agFrameworkComponents = useMemo(() => {
    const vizerRendererMap = Object.keys(displayFormatter).reduce((acc, key) => {
      if (key === 'currency') {
        return { ...acc, currency: tableCurrencyRenderer };
      }
      return {
        ...acc,
        [key]: (value: string) => <TableCell value={displayFormatter[key as VizerDisplayFormat](value)} />,
      };
    }, {});

    return agFrameworkComponentsFromRendererMap({
      ...defaultRendererMap,
      ...vizerRendererMap,
    });
  }, []);

  const { start, end, hasMore, hasPrevious, totalCount } = data?.pageInfo || {
    start: null,
    end: null,
    hasMore: false,
    hasPrevious: false,
    totalCount: 0,
  };

  const [datasetPageInfo, setDatasetPageInfo] = useState<CursorPageInfo>({
    start,
    end,
    hasMore,
    hasPrevious,
    totalCount,
  });

  const { cursor, pageSize, direction, onRequestNextPage, onRequestPrevPage, onGotoPage } = useCursorPagination();

  useEffect(() => {
    if (
      cursor &&
      dataCardId &&
      currentDashboardId === dashboardId &&
      currentDashboardVersionMatch?.version === dashboardVersionMatch?.version
    ) {
      const datacardDashboardId =
        (dashboardVersionMatch?.version === 'draft'
          ? dashboardsData?.find((dashboard) => dashboard.id === currentDashboardId)?.draft?.id
          : currentDashboardId) || currentDashboardId;

      fetchPageData({
        dashboardId: datacardDashboardId || '',
        dataCardId,
        configuration: {},
        pageCursor: {
          cursor,
          pageSize,
          direction,
        },
        previousTotalCount: totalCount,
      })
        .unwrap()
        .then((data) => {
          data.pageInfo && setDatasetPageInfo(data.pageInfo);
          data.rows && setPagedData(data.rows);
        });
    }
  }, [
    currentDashboardId,
    currentDashboardVersionMatch?.version,
    cursor,
    dashboardId,
    dashboardVersionMatch?.version,
    dashboardsData,
    dataCardId,
    direction,
    fetchPageData,
    pageSize,
    totalCount,
  ]);

  const showPagination = totalCount && totalCount > pageSize;

  return (
    <div className="vizer-table" style={{ height }}>
      <Stack fill>
        <AgTable
          immutableData={false}
          columnDefs={showPagination ? columns?.map((column) => ({ ...column, sortable: false })) : columns}
          loading={isLoading}
          frameworkComponents={agFrameworkComponents}
          rowData={memoData}
          sizeColumnsToFit={ResizeColumnsCondition.WHEN_NARROWER}
          suppressCellSelection
          enableCellTextSelection
          colResizeDefault="shift"
          pagerComponent={
            showPagination && (
              <CursorBasedPagination
                simple
                pageInfo={datasetPageInfo}
                onGotoPageChange={onGotoPage}
                onRequestNextPage={onRequestNextPage}
                onRequestPreviousPage={onRequestPrevPage}
                pageSize={pageSize}
                allowGotoPage
              />
            )
          }
        />
      </Stack>
    </div>
  );
};

export { VizerTable };

/**
 * Cell renderer function to format currency values.
 * Defaults to right-aligned. Will support options for left, center, accounting in future
 */
const tableCurrencyRenderer = (value: string) => {
  const formattedValue = displayFormatter.currency(value);
  return (
    <Tooltip mouseEnterDelay={1} title={formattedValue}>
      <div className="vizer-table__currency-cell">
        <div className="vizer-table__currency-symbol">{formattedValue.substring(0, 1)}</div>
        <div className="vizer-table__currency-value">{formattedValue.substring(1)}</div>
      </div>
    </Tooltip>
  );
};
