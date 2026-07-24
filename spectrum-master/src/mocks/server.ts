// src/mocks/server.js
import { setupServer } from 'msw/node';

import handlers from './handlers';

// This configures a request mocking server with the given request handlers.
export const server = setupServer(...handlers);

export const startServer = () =>
  server.listen({
    onUnhandledRequest(req) {
      console.error('Found an unhandled %s request to %s', req.method, req.url.href);
    },
  });
