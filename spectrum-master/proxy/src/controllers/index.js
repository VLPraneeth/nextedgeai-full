//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import fs from 'fs';
import path from 'path';
import process from 'process';

import { initialize as arcadeInitialize } from 'controllers/arcade';
import dev from 'controllers/dev';
import { initialize as mockInitialize } from 'controllers/mock';
import phoneHome from 'controllers/phoneHome';

import AppConstants from 'utils/AppConstants';
import { getConfigVariables } from 'utils/ConfigUtil';
import { logger } from 'utils/LogUtil';

import { mockedRoutesInitialize } from '../mocked-routes';

const { arcadeTarget } = getConfigVariables();

const filePath = path.join(__dirname, AppConstants.VERSION_PATH);

// Hander for the version request
const version = (req, res) => {
  let versionJson = {
    errorMsg: 'File not found, build spectrum first!',
  };

  if (fs.existsSync(filePath)) {
    try {
      versionJson = JSON.parse(fs.readFileSync(filePath, 'utf8'));

      if (process.env.NODE_ENV !== 'production') {
        // if we're in dev, add arcadeTarget to the versionJson response
        versionJson.arcadeTarget = arcadeTarget;
      }
    } catch (e) {
      logger.error(e.message);
    }
  }

  res.send(JSON.stringify(versionJson));
};

export function initialize(app) {
  // Version handler
  app.get('/version', version);

  // phoneHome
  app.use('/phoneHome', phoneHome);

  // Proxies
  // Handle whitelisted mocks
  mockedRoutesInitialize(app);
  // Handler for /arcade
  arcadeInitialize(app);
  // Handler for /mock
  mockInitialize(app);

  // only respond on dev routes if we're not in production
  if (process.env.NODE_ENV !== 'production') {
    // Handler for /dev/*
    app.use('/dev', dev);
  }
}
