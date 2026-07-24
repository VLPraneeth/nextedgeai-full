import { InsightsDashboard } from 'store/insights-studio/types';

export const dash2: InsightsDashboard = {
  id: 'dash2',
  displayName: 'Dash 2',
  description: 'Marketing dashboard',
  draftStatus: 'APPROVED',
  seeded: false,
  dataCards: [
    {
      id: 'dataCard3',
      displayName: 'Daily Spend by Channel',
      description: '',

      layout: {
        minH: 1,
        w: 4,
        h: 2,
        x: 0,
        y: 0,
      },
    },
    {
      id: 'dataCard4',
      displayName: 'Campaign Impressions',
      description: '',
      layout: {
        minH: 1,
        w: 8,
        h: 2,
        x: 7,
        y: 0,
      },
    },
  ],
};
