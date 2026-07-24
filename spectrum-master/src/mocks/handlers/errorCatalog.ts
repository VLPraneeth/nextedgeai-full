import { rest } from 'msw';

import { ErrorCatalogMetadata } from 'store/error-notifications/types';
import DataUrlConstants from 'utils/DataUrlConstants';

const notificationConfig: ErrorCatalogMetadata = {
  notificationItems: [
    {
      id: '62829952b1e81b907e8fc68a',
      category: 'PIPELINE',
      title: 'Pipeline Errors',
      helpText: 'Errors that prevent pipelines from running successfully',
      priority: 'P1',
    },
    {
      id: '62829952b1e81b907e8fc68b',
      category: 'SYNAPSE',
      title: 'Synapse Errors',
      helpText: 'Errors that prevent synapses from running successfully',
      priority: 'P1',
    },
    {
      id: '62829952b1e81b907e8fc68c',
      category: 'SYNC',
      title: 'Sync Errors',
      helpText: 'Errors that prevent data sync from running successfully',
      priority: 'P1',
    },
  ],
  channels: [
    {
      id: null,
      type: 'email',
      label: 'Email',
      configurationType: 'input',
    },
  ],
  frequencies: [
    { frequency: 'HOURLY', label: 'Hourly' },
    { frequency: 'DAILY', label: 'Daily' },
    { frequency: 'WEEKLY', label: 'Weekly' },
    { frequency: 'MONTHLY', label: 'Monthly' },
  ],
};

const handlers = [
  rest.get(DataUrlConstants.ERROR_CATALAG, (req, res, ctx) => {
    return res(ctx.status(200), ctx.json(notificationConfig));
  }),
];

export default handlers;
