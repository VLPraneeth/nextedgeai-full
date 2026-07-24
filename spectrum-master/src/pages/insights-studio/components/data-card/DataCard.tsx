import { Spin } from 'antd';
import { useMemo, useEffect } from 'react';

import CenterLayout from 'components/layout/CenterLayout';
import { Vizer } from 'components/vizer/Vizer';
import { useEnhancedSelector } from 'hooks/redux';
import { useLazyGetDashDataCardWithConfigurationQuery } from 'store/insights-studio/api';
import { DashListDataCard } from 'store/insights-studio/types';
import { makeUserDataCardConfigKey } from 'store/insights-studio/util';
import { getRtkQueryErrorMessage } from 'utils/getRtkQueryErrorMessage';

import { dashboardGridLayoutSettings } from '../dashboard-layout/DashboardLayout';
import { DataCardError } from '../data-card-error/DataCardError';
import TitleBar from './TitleBar';

import './DataCard.less';

export interface DataCardProps {
  dashboardId: string;
  description: string;
  id: string;
  name: string;
  layout: DashListDataCard['layout'];
  removeFromDashboard?: () => void;
  isDraft?: boolean;
  showAddToDashboard?: boolean;
}

export const DataCard = ({
  dashboardId,
  id,
  name,
  description,
  layout,
  removeFromDashboard,
  isDraft = false,
  showAddToDashboard,
}: DataCardProps) => {
  const [
    getDataCard,
    { data: dataCard, isLoading, isFetching, isError, error },
  ] = useLazyGetDashDataCardWithConfigurationQuery();
  const graphHeight = useMemo(() => calculateGraphHeight(layout.h), [layout.h]);
  const userDataCardConfig = useEnhancedSelector((state) => state.insightsStudio.userDataCardConfig);

  useEffect(() => {
    const userConfig = userDataCardConfig[makeUserDataCardConfigKey(dashboardId, id)] || {};
    getDataCard({ dashboardId, dataCardId: id, configuration: userConfig.configuration || {} });
  }, [dashboardId, getDataCard, id, userDataCardConfig]);

  const hasError = isError || dataCard?.contents?.data?.error;

  return (
    <div className="data-card">
      <TitleBar
        name={dataCard?.displayName || name}
        description={dataCard?.description || description || ''}
        dashboardId={dashboardId}
        dataCard={dataCard}
        showConfigButton={Boolean(dataCard?.configurationMeta?.length)}
        removeFromDashboard={removeFromDashboard}
        showEditControls={isDraft}
        showAddToDashboard={showAddToDashboard}
      />

      {isLoading || isFetching ? (
        // Loading State
        <CenterLayout>
          <Spin />
        </CenterLayout>
      ) : hasError ? (
        // Error State
        <DataCardError error={dataCard?.contents?.data?.error} tooltip={getRtkQueryErrorMessage(error)} />
      ) : (
        // Success State
        <div data-testid="data-card-contents" className="data-card__contents">
          {dataCard?.id && (
            <Vizer
              dataCardId={dataCard.id}
              dataCardContent={dataCard.contents}
              dataConfiguration={dataCard.configuration}
              graphHeight={graphHeight}
              // key triggers rerender of graph when data card is resized or vizType changes
              key={dataCard.id + '-' + layout.w + '-' + dataCard.contents.configuration.vizType}
            />
          )}
        </div>
      )}
    </div>
  );
};

/**
 * Calculate the height of the graph within a DataCard
 *
 * @param {number} rows Number of rows the DataCard should span in ReactGridLayout
 * @returns {number} height of the graph in pixels
 */
const calculateGraphHeight = (rows: number = 1) => {
  const { rowHeight, margin, verticalPadding, headerHeight } = dashboardGridLayoutSettings;

  const cardHeightInPixels = rows * rowHeight + (rows - 1) * margin;

  return cardHeightInPixels - verticalPadding * 2 - headerHeight;
};
