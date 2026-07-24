import { message } from 'antd';
import { cloneDeep } from 'lodash';
import { useSelector } from 'react-redux';

import { dashboardEndpoints, useEditDashboardMutation } from 'store/insights-studio';
import { DashListDataCard } from 'store/insights-studio/types';
import { keyBy } from 'utils/Fp';
import { getRtkQueryErrorMessage } from 'utils/getRtkQueryErrorMessage';
import { tNamespaced } from 'utils/i18nUtil';

const tn = tNamespaced('InsightsStudio');

export const useAddCardToDashboard = (dashboardId?: string) => {
  // ONLY ever use cached data here. We don't want to trigger a request from this hook
  const dashboard = useSelector(
    (state) => dashboardEndpoints.getDashboard.select(dashboardId ?? '')(state as any).data
  );

  const [updateDashboard] = useEditDashboardMutation();

  return (cardId: string, layouts?: ReactGridLayout.Layout[], item?: ReactGridLayout.Layout) => {
    if (!dashboard || !dashboard.dataCards) {
      return Promise.reject();
    }

    if (dashboard.dataCards.some((card) => card.id === cardId)) {
      message.error(tn('data_card_on_dashboard'));
      return Promise.reject();
    }

    const updatedDashboard = cloneDeep(dashboard);

    if (layouts && item) {
      const newDataCard: DashListDataCard = {
        id: cardId,
        layout: { ...item, i: cardId },
      };

      updatedDashboard.dataCards = [].concat(layoutsToDataCardList(layouts, dashboard.dataCards), newDataCard);
    } else {
      // If no updated layout and item provided, add card to end of dashboard

      const { x, y } = calculateEndPosition(updatedDashboard.dataCards?.map((card) => card.layout) ?? []);

      const newDataCard: DashListDataCard = {
        id: cardId,
        layout: {
          i: cardId,
          x,
          y,
          w: 4,
          h: 2,
        },
      };
      updatedDashboard.dataCards?.push(newDataCard);
    }

    return updateDashboard(updatedDashboard).then((result) => {
      if ('data' in result) {
        message.success(tn('data_card_added'));
      } else {
        message.error(tn('data_card_not_added') + '. ' + getRtkQueryErrorMessage(result.error));
      }
    });
  };
};

export const layoutsToDataCardList = (
  layouts: ReactGridLayout.Layout[],
  dataCardList: DashListDataCard[]
): DashListDataCard[] => {
  const keyedLayouts = keyBy('i', layouts);

  const newDataCardList = dataCardList.map((card) => {
    if (keyedLayouts[card.id]) {
      return { ...card, layout: keyedLayouts[card.id] };
    }
    return null;
  });

  return newDataCardList.filter(Boolean) as DashListDataCard[];
};

export const calculateEndPosition = (
  dataCardLayouts: { x: number; y: number; w: number }[]
): { x: number; y: number } => {
  // Find the last card (highest x value with highest y value)
  const lastCard = dataCardLayouts.sort((a, b) => {
    return a.y - b.y || a.x - b.x;
  })[dataCardLayouts.length - 1];

  // Add the last cards width to it's x value to get the new cards x value
  let x = (lastCard?.x ?? 0) + (lastCard?.w ?? 0);
  let y = lastCard?.y ?? 0;

  if (x > 8) {
    // If card can't fit in remaining space of the row, drop it to the start of the next row
    x = 0;
    y += 1;
  }

  return { x, y };
};
