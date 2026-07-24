import { rest } from 'msw';

import { getEntities } from 'pages/sync-studio/fast-mapper/FastMapperModal.fixtures';

const handlers = [
  rest.get('/arcade/api/v1/schema/entity/605cd613b561c5977cae2f81', (req, res, ctx) => {
    return res(ctx.status(200), ctx.json({}));
  }),

  rest.get('/arcade/api/v1/schema/entity/603ca4f25db2a7fe97e4b5b4', (req, res, ctx) => {
    return res(
      ctx.status(200),
      ctx.json({
        entities: getEntities(),
      })
    );
  }),

  rest.get('/arcade/api/v1/schema/605cd5fab561c5977cae2f68', (req, res, ctx) => {
    return res(ctx.status(200), ctx.json({}));
  }),

  rest.get('/arcade/api/v1/schema/603d124e5db2a7fe97e4c350', (req, res, ctx) => {
    return res(
      ctx.status(200),
      ctx.json({
        entities: getEntities(),
      })
    );
  }),
];

export default handlers;
