import { rest } from 'msw';

import { getConnector } from 'pages/sync-studio/fast-mapper/FastMapperModal.fixtures';

const handlers = [
  rest.get('/arcade/api/v1/connector/', (req, res, ctx) => {
    return res(ctx.status(200), ctx.json(getConnector().connectors));
  }),
];

export default handlers;
