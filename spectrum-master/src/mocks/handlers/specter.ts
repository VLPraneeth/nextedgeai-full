import { rest } from 'msw';

import DataUrlConstants from 'utils/DataUrlConstants';

const handlers = [
  rest.post(DataUrlConstants.REQUEST_GHOST_ACCESS, (req, res, ctx) => {
    return res(ctx.status(200), ctx.json({ result: 'success' }));
  }),
];

export default handlers;
