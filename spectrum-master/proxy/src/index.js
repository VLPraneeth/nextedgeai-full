//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
// Environment variables:
//   ARCADE_TARGET - proxy the /arcade to this path
//                   - defaults to 'http://localhost:8080'
//   ARCADE_LOG_LEVEL - arcade proxy log level
//                   - default to 'info'. 'debug' to log all redirected urls
//   SPECTRUM_PORT - spectrum nodejs will listen to this port
//                   - defaults to 8088
//   SECURE_COOKIES - create secure cookies. default is 'true'
//   DISABLE_XSRF - Disable XSRF environment variable. Default is false and should only be turned on development mode.

import http from 'http';
import path from 'path';
import crypto from 'crypto';

import axios from 'axios';
import bodyParser from 'body-parser';
import { initialize as initializeControllers } from 'controllers';
import { initMessageStream } from 'controllers/messageStream';
import express from 'express';
import morgan from 'morgan';
import responseTime from 'response-time';

import AppConstants from 'utils/AppConstants';
import { printWelcome } from 'utils/AppUtil';
import { getConfigVariables } from 'utils/ConfigUtil';
import { logger } from 'utils/LogUtil';

import overrideArcade from './middlewares/overrideArcade';
import xsrfChecker from './middlewares/xsrf';
import serveIndex from './serveIndex';

const helmet = require('helmet');

const app = express();

const { spectrumPort, trackResponseTime } = getConfigVariables();

if (trackResponseTime) {
  app.use(responseTime());
  app.use((req, res, next) => {
    req.id = req.get('x-request-id') || crypto.randomUUID();
    res.set('x-request-id', req.id);
    next();
  });
}

printWelcome();

app.use(
  // Apache access log format (combined) + response time ms
  morgan(
    ':remote-addr - :remote-user [:date[clf]] ":method :url HTTP/:http-version" :status :res[content-length] ":referrer" ":user-agent" ":response-time ms"'
  )
);

// Capture the set password and force it to the ui
app.get('/arcade/api/v1/user/setpassword/', (req, res) => {
  res.sendFile(path.resolve(__dirname, 'public', 'index.html'));
});

// Proxy the pypi syncari sdk info to show in the UI
app.get('/arcade/api/v1/syncariSdkInfo', async (req, res) => {
  try {
    const response = await axios.get('https://pypi.org/pypi/syncari-sdk/json');
    res.json({
      requiresPython: response.data.info.requires_python,
      version: response.data.info.version,
      packageUrl: response.data.info.package_url,
    });
  } catch (error) {
    res.status(500).json({ error: 'Internal Server Error' });
  }
});

// Parse url encoded body
app.use(bodyParser.urlencoded({ limit: AppConstants.BODY_LIMIT, extended: false }));

// Include our xsrf middleware
if (process.env.DISABLE_XSRF !== 'true') {
  app.use(xsrfChecker);
}

// Middleware to check if we need to serve the
// index for specific arcade urls.
app.use(overrideArcade);

// Initialize our controllers BEFORE helmet so arcade proxy can set headers
initializeControllers(app);

// Set security related http response headers
// Skip helmet for /arcade paths since arcade proxy handles its own headers
app.use((req, res, next) => {
  if (req.path.startsWith('/arcade')) {
    return next();
  }
  // Google Identity popups need to communicate with their opener when FedCM
  // is unavailable, while the referrer policy keeps cross-origin requests
  // limited to the site origin.
  res.setHeader('Cross-Origin-Opener-Policy', 'same-origin-allow-popups');
  res.setHeader('Referrer-Policy', 'strict-origin-when-cross-origin');
  helmet({
    hsts: {
      maxAge: 31536000, // 1 year
      includeSubDomains: true,
      preload: true,
    },
    frameguard: {
      action: 'sameorigin',
    },
  })(req, res, next);
});

app.disable('x-powered-by');

const setHeaders = (res, path) => {
  if (path && path.endsWith('service-worker.js')) {
    // we want to make sure service-worker is never cached
    res.setHeader('Cache-Control', 'public, max-age=0');
  }
};

// default to Max-Age of MAX_AGE, unless set by setHeaders explicitly
// BUT exclude /arcade/ui paths which should be proxied to the backend
app.use((req, res, next) => {
  if (req.path.startsWith('/arcade/ui')) {
    return next();
  }
  express.static(path.join(__dirname, 'public'), { maxage: AppConstants.MAX_AGE, setHeaders })(req, res, next);
});

// Always return the main index.html, so that UI-router will take care of
// rendering the proper UI
// BUT exclude /arcade/ui paths which should be proxied to the backend
app.get('*', (req, res, next) => {
  // Don't serve index for /arcade/ui paths - let them be proxied
  if (req.path.startsWith('/arcade/ui')) {
    return next();
  }
  serveIndex(req, res);
});

const httpServer = http.createServer(app);
httpServer.listen(spectrumPort, '0.0.0.0', () => {
  logger.info(`Spectrum listening on port ${spectrumPort}!`);

  // Initialize our message stream
  initMessageStream(httpServer);
});
