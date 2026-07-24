import { rest } from 'msw';

import DataUrlConstants from 'utils/DataUrlConstants';
import { makeUrl } from 'utils/UrlUtil';

const handlers = [
  rest.get(makeUrl(DataUrlConstants.INSTANCE_FEATURE, { featureName: 'Insights' }), (req, res, ctx) => {
    return res(
      ctx.status(200),
      ctx.json({
        name: 'Insights',
        stage: 'GA',
        status: 'inactive',
      })
    );
  }),
  rest.post(makeUrl(DataUrlConstants.INSTANCE_FEATURE_ENABLE, { featureName: 'Insights' }), (req, res, ctx) => {
    return res(
      ctx.status(200),
      ctx.json({
        name: 'Insights',
        stage: 'GA',
        status: 'active',
      })
    );
  }),
  rest.post(makeUrl(DataUrlConstants.INSTANCE_FEATURE_DISABLE, { featureName: 'Insights' }), (req, res, ctx) => {
    return res(
      ctx.status(200),
      ctx.json({
        name: 'Insights',
        stage: 'GA',
        status: 'insactive',
      })
    );
  }),
  rest.post(
    makeUrl(DataUrlConstants.INSTANCE_FEATURE_ENABLE, { featureName: 'InsightsAdvanceDataset' }),
    (req, res, ctx) => {
      return res(
        ctx.status(200),
        ctx.json({
          name: 'InsightsAdvanceDataset',
          stage: 'GA',
          status: 'active',
        })
      );
    }
  ),
  rest.post(
    makeUrl(DataUrlConstants.INSTANCE_FEATURE_DISABLE, { featureName: 'InsightsAdvanceDataset' }),
    (req, res, ctx) => {
      return res(
        ctx.status(200),
        ctx.json({
          name: 'InsightsAdvanceDataset',
          stage: 'GA',
          status: 'insactive',
        })
      );
    }
  ),
];

export default handlers;
