//
// Copyright (c) 2019-Present Syncari - All rights reserved.
// Mocking arcade
//
import { createProxyMiddleware } from 'http-proxy-middleware';

import { getConfigVariables } from 'utils/ConfigUtil';
import { logProvider } from 'utils/LogUtil';

const { mockTarget, useMock, arcadeLogLevel: logLevel } = getConfigVariables();

export function initialize(app) {
  // Skip initialization of arcade mocking controller when use mock is disabled
  if (!useMock) {
    return;
  }

  const target = mockTarget;
  const options = {
    target,
    changeOrigin: true,
    ws: false,
    pathRewrite: {
      '^/arcade': '/mock/arcade', // add the mock prefix
    },
    logLevel,
    logProvider,
  };

  // Setup our proxy
  app.use('/arcade', createProxyMiddleware(options));
}
