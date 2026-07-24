import { rest } from 'msw';

import DataUrlConstants from 'utils/DataUrlConstants';
import { makeUrl } from 'utils/UrlUtil';

import { dashboards, dash1, dash2, dataCard1, testDataCard, dataCard2 } from '../fixtures/insights';

const handlers = [
  rest.get(DataUrlConstants.INSIGHTS_DASHBOARDS, (req, res, ctx) => {
    return res(ctx.status(200), ctx.json(dashboards));
  }),

  rest.get(makeUrl(DataUrlConstants.INSIGHTS_DASHBOARD, { dashboardId: 'dash1' }), (req, res, ctx) => {
    return res(ctx.status(200), ctx.json(dash1));
  }),

  rest.get(makeUrl(DataUrlConstants.INSIGHTS_DASHBOARD, { dashboardId: 'dash2' }), (req, res, ctx) => {
    return res(ctx.status(200), ctx.json(dash2));
  }),

  rest.post(
    makeUrl(DataUrlConstants.INSIGHTS_GET_DATACARD, { dataCardId: 'testDataCard', dashboardId: 'testDashboard' }),
    (req, res, ctx) => {
      return res(ctx.status(200), ctx.json(testDataCard));
    }
  ),

  rest.post(
    makeUrl(DataUrlConstants.INSIGHTS_GET_DATACARD, { dataCardId: 'dataCard1', dashboardId: 'dash1' }),
    (req, res, ctx) => {
      return res(ctx.status(200), ctx.json(dataCard1));
    }
  ),
  rest.post(
    makeUrl(DataUrlConstants.INSIGHTS_GET_DATACARD, { dataCardId: 'dataCard2', dashboardId: 'dash1' }),
    (req, res, ctx) => {
      return res(ctx.status(200), ctx.json(dataCard2));
    }
  ),

  // Wildcard routes must be at the end of the list or they will overwrite other routes
  rest.get(makeUrl(DataUrlConstants.INSIGHTS_DASHBOARD, { dashboardId: '*' }), (req, res, ctx) => {
    return res(ctx.status(404));
  }),
];

export default handlers;
