import { useEffect, useState, useMemo } from 'react';
import ReactGridLayout, { Responsive, WidthProvider } from 'react-grid-layout';

import { useI18nContext, withI18n } from 'components/I18nProvider';
import { Stack } from 'components/layout';
import { Text } from 'components/typography';
import { useGetDashboardQuery, useGetDashboardsQuery } from 'store/insights-studio/api';

import { DataCard } from '../components/data-card/DataCard';

import './InterestingDataCards.scss';

export const InterestingDataCards = withI18n(() => {
  const { data: dashboards } = useGetDashboardsQuery();
  const { tn } = useI18nContext();

  const dashboardId = useMemo(() => {
    const suggestedDashboard = dashboards?.find((dashboard) => dashboard.tags?.includes('InsightsGPT'));
    if (suggestedDashboard) {
      return suggestedDashboard?.id;
    }
    return '';
  }, [dashboards]);

  const { data: dashboard } = useGetDashboardQuery(dashboardId, { skip: !Boolean(dashboardId) });
  const [layouts, setLayouts] = useState<ReactGridLayout.Layout[]>([]);

  useEffect(() => {
    const layouts =
      dashboard?.dataCards?.map((card) => {
        return {
          ...card.layout,
          i: card.id,
          isDraggable: false,
          isResizable: false,
          minH: 1,
          minW: 3,
          maxH: 4,
        };
      }) ?? [];

    setLayouts(layouts);
  }, [dashboard?.dataCards, dashboard?.draftStatus]);

  const ResponsiveGridLayout = useMemo(() => WidthProvider(Responsive), []);
  return (
    <div className="interesting-data-cards">
      <div
        style={{
          display: 'flex',
          justifyContent: 'start',
          alignItems: 'center',
          marginLeft: 22,
          height: '100%',
        }}>
        <Stack fill className="interesting-data-cards--container">
          <Text color="gray-800" size="xl">
            {tn('interesting_data_cards')}
          </Text>
          {layouts?.length && (
            <ResponsiveGridLayout
              key="random1234"
              breakpoints={{ sm: 480 }}
              cols={{ sm: 12 }}
              layouts={{ sm: layouts }}
              rowHeight={dashboardGridLayoutSettings.rowHeight}
              margin={[dashboardGridLayoutSettings.margin, dashboardGridLayoutSettings.margin]}
              isDroppable={false}
              droppingItem={{ i: 'drop-placeholder', w: 4, h: 2 }}
              draggableCancel=".vizer">
              {dashboard?.dataCards?.map((card, i) => {
                return (
                  <div key={card.id}>
                    <DataCard
                      dashboardId={dashboard.id}
                      description={card.description ?? ''}
                      id={card.id}
                      name={card.displayName ?? ''}
                      layout={layouts[i]}
                      isDraft={false}
                      showAddToDashboard
                    />
                  </div>
                );
              })}
            </ResponsiveGridLayout>
          )}
        </Stack>
      </div>
    </div>
  );
}, 'InsightsStudio.InsightsGPT');

const dashboardGridLayoutSettings = {
  rowHeight: 150, // Height of rows for ReactGridLayout
  margin: 28, // Margin set for ReactGridLayout
  verticalPadding: 14, // 7 top + 7 bottom
  headerHeight: 36, // 32 height + 3.5 bottom margin
};
