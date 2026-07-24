import { InsightsDashboard } from 'store/insights-studio/types';

export const dash1: InsightsDashboard = {
  id: 'dash1',
  displayName: 'Dash 1',
  description: 'Sales dashboard',
  draftStatus: 'NEW',
  seeded: false,
  dataCards: [
    {
      id: 'dataCard1',
      layout: {
        minH: 1,
        w: 4,
        h: 1,
        x: 0,
        y: 0,
      },
      displayName: 'Revenue by quarter',
      description: '',
    },
    {
      id: 'dataCard2',
      layout: {
        minH: 1,
        w: 8,
        h: 1,
        x: 5,
        y: 0,
      },

      displayName: 'Annual Recurring Revenue',
      description: '',
    },
    {
      id: '333',
      layout: {
        minH: 1,
        w: 4,
        h: 1,
        x: 0,
        y: 1,
      },

      displayName: 'Revenue Type by Quarter',
      description: '',
    },
    {
      id: '444',
      layout: {
        minH: 1,
        w: 4,
        h: 1,
        x: 4,
        y: 1,
      },
      displayName: 'Net Dollar Retention',
      description: '',
    },
    {
      id: '555',
      layout: {
        minH: 1,
        w: 4,
        h: 1,
        x: 8,
        y: 1,
      },

      displayName: 'Expansion Revenue',
      description: '',
    },
    {
      id: '666',
      layout: {
        minH: 1,
        w: 6,
        h: 2,
        x: 1,
        y: 2,
      },

      displayName: 'Revenue Churn',
      description: '',
    },
    {
      id: '777',
      layout: {
        minH: 1,
        w: 6,
        h: 2,
        x: 0,
        y: 2,
      },

      displayName: 'Average Revenue Per Account',
      description: '',
    },
  ],
};
