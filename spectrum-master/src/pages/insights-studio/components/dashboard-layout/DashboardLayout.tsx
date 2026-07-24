import { message } from 'antd';
import React, { useEffect, useMemo, useState } from 'react';
import ReactGridLayout, { Responsive, WidthProvider } from 'react-grid-layout';

import { ReactComponent as DataTrendIcon } from 'assets/icons/data-trend.svg';
import Button from 'components/Button';
import Can from 'components/Can';
import InlineMessage from 'components/InlineMessage';
import { PermissionsComparisonOperator } from 'hooks/useUserHasPermission';
import { useDataCardAuthoringContext } from 'pages/insights-studio/context/DataCardAuthoringContext';
import { useInsightsSidebarContext } from 'pages/insights-studio/context/InsightsSidebarContext';
import { layoutsToDataCardList, useAddCardToDashboard } from 'pages/insights-studio/utils/dashboardUtils';
import { useUnifiedDataCardNavigate } from 'pages/insights-studio/utils/useUnifiedDataCardNavigate';
import { useEditDashboardMutation, useGetDashboardQuery } from 'store/insights-studio/api';
import { getRtkQueryErrorMessage } from 'utils/getRtkQueryErrorMessage';
import { tc, tNamespaced } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';

import { DataCard } from '../data-card/DataCard';
import { DragData } from '../draggable-panel-item/DraggablePanelItem';
import { EmptyPanelContent } from '../empty-panel-content/EmptyPanelContent';

import 'react-grid-layout/css/styles.css';
import 'react-resizable/css/styles.css';
import './DashboardLayout.less';

const tn = tNamespaced('InsightsStudio');

interface DashboardLayoutProps {
  dashboardId: string;
}

export const DashboardLayout = ({ dashboardId }: DashboardLayoutProps) => {
  const { data: dashboard, isError } = useGetDashboardQuery(dashboardId);
  const [updateDashboard] = useEditDashboardMutation();
  const { sidebarOpen } = useInsightsSidebarContext();

  const { createDataCardFromDataset } = useDataCardAuthoringContext();
  const addCardToDashboard = useAddCardToDashboard(dashboard?.id);

  const [layouts, setLayouts] = useState<ReactGridLayout.Layout[]>([]);

  const ResponsiveGridLayout = useMemo(() => WidthProvider(Responsive), []);
  const { navigateTo } = useUnifiedDataCardNavigate();

  const isDraft = dashboard?.draftStatus === 'NEW';

  useEffect(() => {
    // Manually trigger a resize event to force ReactGridLayout to resize when sidebar is opened/closed
    const timeoutId = setTimeout(() => {
      window.dispatchEvent(new Event('resize'));
      // wait 300ms for sidebar animation to finish before resizing layout
    }, 300);
    return () => {
      window.clearTimeout(timeoutId);
    };
  }, [sidebarOpen]);

  useEffect(() => {
    const layouts =
      dashboard?.dataCards?.map((card) => {
        return {
          ...card.layout,
          i: card.id,
          isDraggable: isDraft,
          isResizable: isDraft,
          minH: 1,
          minW: 3,
          maxH: 4,
        };
      }) ?? [];

    setLayouts(layouts);
  }, [dashboard?.dataCards, dashboard?.draftStatus, isDraft]);

  const grid = useMemo(() => {
    const handleDrop = (layouts: ReactGridLayout.Layout[], item: ReactGridLayout.Layout, e: Event) => {
      if (!dashboard || !('dataTransfer' in e)) {
        return;
      }

      let dragData: DragData;
      try {
        // @ts-expect-error: Property 'dataTransfer' does not exist on type 'Event'
        // react-grid-layout types this as a generic Event instead of a DragEvent, but it does provide the dataTransfer property
        dragData = JSON.parse(e.dataTransfer.getData('dragData'));
      } catch (error) {
        message.error(`${tn('data_card_not_added')}. ${tc('generic_error')}`);
        return;
      }

      // Detect if data card or dataset was dragged
      // If dataset, trigger create card with dragged dataset pre-selected
      if (dragData.draggedType === 'dataset') {
        createDataCardFromDataset(dragData.id, { dashboardId: dashboard.id, layouts, item });
        navigateTo('DATACARD', 'new', { dashboardId: dashboard.id, datasetId: dragData.id });
      }

      // If data card, add to dashboard
      if (dragData.draggedType === 'datacard') {
        addCardToDashboard(dragData.id, layouts, item);
      }
    };

    const handleMoveOrResize = (layouts: ReactGridLayout.Layout[]) => {
      if (!dashboard || !dashboard.dataCards) {
        return;
      }

      const newDataCards = layoutsToDataCardList(layouts, dashboard.dataCards);
      const updatedDashboard = { ...dashboard, dataCards: newDataCards };

      updateDashboard(updatedDashboard).then((result) => {
        if ('data' in result) {
          message.success(tn('layout_saved'));
        } else {
          message.error(tn('layout_not_saved') + '. ' + getRtkQueryErrorMessage(result.error));
        }
      });
    };

    const handleRemoveCard = (cardId: string) => {
      if (!dashboard || !dashboard.dataCards) {
        return;
      }
      const filteredLayouts = layouts.filter((layout) => layout.i !== cardId);

      const newDataCards = layoutsToDataCardList(filteredLayouts, dashboard.dataCards);
      const updatedDashboard = { ...dashboard, dataCards: newDataCards };

      updateDashboard(updatedDashboard).then((result) => {
        if ('data' in result) {
          message.success(tn('data_card_removed'));
        } else {
          message.error(tn('data_card_not_removed') + '. ' + getRtkQueryErrorMessage(result.error));
        }
      });
    };

    if ((dashboard?.dataCards?.length ?? 0) > layouts.length) {
      // prevents crash from rendering before `layouts` has been created
      return null;
    }
    return (
      <ResponsiveGridLayout
        style={{ minHeight: '100%' }}
        key={dashboardId}
        breakpoints={{ sm: 480 }}
        cols={{ sm: 12 }}
        layouts={{ sm: layouts }}
        rowHeight={dashboardGridLayoutSettings.rowHeight}
        margin={[dashboardGridLayoutSettings.margin, dashboardGridLayoutSettings.margin]}
        isDroppable={isDraft}
        onDrop={handleDrop}
        onDragStop={handleMoveOrResize}
        onResizeStop={handleMoveOrResize}
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
                removeFromDashboard={isDraft ? () => handleRemoveCard(card.id) : undefined}
                isDraft={isDraft}
              />
            </div>
          );
        })}
      </ResponsiveGridLayout>
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [layouts, dashboard]);

  return (
    <div className="dashboard-layout">
      {isError && (
        <div className="dashboard-layout__error">
          <InlineMessage type="error">{tn('dashboard_not_found', { dashboard: dashboardId })}</InlineMessage>
        </div>
      )}
      {grid}
      {!dashboard?.dataCards?.length && isDraft && <EmptyDashboardPlaceholder selectedDashboardId={dashboard.id} />}
    </div>
  );
};

export const dashboardGridLayoutSettings = {
  rowHeight: 150, // Height of rows for ReactGridLayout
  margin: 28, // Margin set for ReactGridLayout
  verticalPadding: 14, // 7 top + 7 bottom
  headerHeight: 36, // 32 height + 3.5 bottom margin
};

const EmptyDashboardPlaceholder = ({ selectedDashboardId }: { selectedDashboardId: string }) => {
  const { openToTab } = useInsightsSidebarContext();
  const { navigateTo } = useUnifiedDataCardNavigate();

  const highlightList = () => {
    openToTab('datacards');

    document.getElementsByClassName('authoring-sidebar-list').item(0)?.classList.add('highlight');

    setTimeout(() => {
      document.getElementsByClassName('authoring-sidebar-list').item(0)?.classList.remove('highlight');
    }, 1600);
  };
  return (
    <div
      className="dashboard-layout__placeholder"
      style={{
        // Allow dropping data cards over the dashboard placeholder
        pointerEvents: 'none',
      }}>
      <EmptyPanelContent title="This dashboard looks empty." icon={<DataTrendIcon height={60} />}>
        <div
          style={{
            // Enable events just here so inner links are still clickable
            pointerEvents: 'all',
          }}>
          <Can
            permissionOperator={PermissionsComparisonOperator.AND}
            permission={[AllPermissions.CREATE_DATACARD, AllPermissions.UPDATE_DATACARD]}>
            <Button type="link" onClick={() => navigateTo('DATACARD', 'new', { dashboardId: selectedDashboardId })}>
              Add a new card
            </Button>
          </Can>{' '}
          or{' '}
          <Button type="link" onClick={highlightList}>
            drag a data card or set from the panel to the right
          </Button>
        </div>
      </EmptyPanelContent>
    </div>
  );
};
