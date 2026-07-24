import { performance } from 'perf_hooks';
import querystring from 'querystring';

import cookie from 'cookie';
import { createProxyMiddleware } from 'http-proxy-middleware';

import { getConfigVariables } from 'utils/ConfigUtil';
import { compose, match, trim } from 'utils/Fp';
import { logger, logProvider } from 'utils/LogUtil';

const DP = {
  AUTH_TOKEN: 'authorization',
  HOME_URL: '/',
  LOGOUT_URL: '/arcade/logout',
  SAME_SITE_STRICT: 'strict',
  LOCATION_HEADER: 'location',
};

let { secureCookies, trackResponseTime } = getConfigVariables();

const PROXY_RESPONSE_TIME = 'x-arcade-proxy-response-time';
const responseTime = new Map();

/**
 * Set a header value of a request.
 *
 * @param {Request} req request object
 * @param {String} key key of the header
 * @param {String} value value of the header
 */
const setHeader = (req, key, value) => {
  try {
    req.setHeader(key, value);
  } catch (e) {
    logger.error(`Invalid header. Key: ${key} Value: ${value}`);
  }
};

/**
 * Relay all the request headers
 */
function relayRequestHeaders(proxyReq, req) {
  convertAuthCookies(proxyReq, req);

  Object.keys(req.headers).forEach(function (key) {
    setHeader(proxyReq, key, req.headers[key]);
  });

  if (!req.body || !Object.keys(req.body).length) {
    if (trackResponseTime) {
      responseTime.set(req.id, performance.now());
    }
    return;
  }
  proxyReqWrite(proxyReq, req);
  if (trackResponseTime) {
    responseTime.set(req.id, performance.now());
  }
}

function proxyReqWrite(proxyReq, req) {
  const contentType = proxyReq.getHeader('Content-Type');
  const writeBody = (bodyData) => {
    setHeader(proxyReq, 'Content-Length', Buffer.byteLength(bodyData));
    proxyReq.write(bodyData);
  };

  if (contentType === 'application/json') {
    writeBody(JSON.stringify(req.body));
  }

  if (contentType === 'application/x-www-form-urlencoded') {
    writeBody(querystring.stringify(req.body));
  }
}

/**
 * Relay all the response headers
 */
function relayResponseHeaders(proxyRes, req, res) {
  if (trackResponseTime) {
    const startTime = responseTime.get(req.id);
    if (startTime) {
      res.append(PROXY_RESPONSE_TIME, `${(performance.now() - parseFloat(startTime)).toFixed(3)}ms`);
      responseTime.delete(req.id);
    }
  }

  // Debug: log all response headers from backend
  logger.debug(
    `Response headers from backend for ${req.originalUrl}: ${JSON.stringify(Object.keys(proxyRes.headers))}`
  );

  Object.keys(proxyRes.headers).forEach(function (key) {
    if (key === DP.AUTH_TOKEN) {
      addAuthCookies(proxyRes, res);
    } else if (key === DP.LOCATION_HEADER && proxyRes.statusCode === 302) {
      logger.debug('302 with location: ', proxyRes.headers[DP.LOCATION_HEADER]);
      res.statusCode = 200;
      proxyRes.statusCode = 200;
      const locationValue = proxyRes.headers[DP.LOCATION_HEADER];
      if (locationValue) {
        res.append('x-syncari-oauth-redirect', locationValue);
        proxyRes.headers['x-syncari-oauth-redirect'] = locationValue;
        delete proxyRes.headers[DP.LOCATION_HEADER];
        logger.debug('Removing location header from the proxyRes');
      }
      if (res.hasHeader(DP.LOCATION_HEADER)) {
        res.removeHeader(DP.LOCATION_HEADER);
        logger.debug('Removing location header from the response');
      }
    } else {
      res.append(key, proxyRes.headers[key]);
    }

    // TODO: Handle refresh tokens
  });
  clearOnLogout(req, res);
}

/**
 * Convert the auth cookies to authorization header for
 * making api request. Add the header to the proxy request
 *
 * @param {Object} proxyReq Proxy request object
 * @param {Object} req request object
 */
function convertAuthCookies(proxyReq, req) {
  const cookies = cookie.parse(req.headers.cookie || '');
  if (cookies && cookies[DP.AUTH_TOKEN]) {
    // Decode the URI-encoded token back to the original value
    setHeader(proxyReq, DP.AUTH_TOKEN, decodeURIComponent(cookies[DP.AUTH_TOKEN]));
  }
}

/**
 * Add the auth cookie to the response object as for later api request.
 * It is http only, session and same site cookie for XSS and XSRF protection
 * @param {Object} proxyRes Proxy response object
 * @param {Object} res Response object
 */
function addAuthCookies(proxyRes, res) {
  // TODO: Add secure when we add https support...
  const rawToken = proxyRes.headers[DP.AUTH_TOKEN];

  // Handle array case (multiple headers)
  const stringToken = Array.isArray(rawToken) ? rawToken[0] : String(rawToken || '');

  if (!stringToken || stringToken === 'undefined' || stringToken === '') {
    logger.error(`Auth token is empty or undefined: "${stringToken}", skipping cookie`);
    return;
  }

  // JWT tokens are already base64url-encoded, so we don't need to encode again.
  // This also avoids exceeding the ~4KB cookie size limit.
  // Use encodeURIComponent to handle any special characters safely.
  const cookieValue = encodeURIComponent(stringToken);

  logger.debug(`Setting auth cookie, token length: ${stringToken.length}, cookie length: ${cookieValue.length}`);

  // Session cookie - omit expires/maxAge so cookie is deleted when browser closes
  res.cookie(DP.AUTH_TOKEN, cookieValue, {
    httpOnly: true,
    secure: secureCookies,
    sameSite: DP.SAME_SITE_STRICT,
  });
}

/**
 * Clear the auth cookies on logout
 * @param {Object} req Request object
 * @param {Object} res Response object
 */
function clearOnLogout(req, res) {
  if (req.originalUrl === DP.LOGOUT_URL) {
    res.clearCookie(DP.AUTH_TOKEN, {
      httpOnly: true,
      secure: secureCookies,
      sameSite: DP.SAME_SITE_STRICT,
    });
  }
}

// SSO
// In http-proxy-middleware v3.x, pathRewrite receives path relative to mount point
// So /sso/ORGID/assertion becomes /ORGID/assertion
const ssoRegex = /^\/([\w\d]+)\/assertion$/;
const transformSsoUrl = compose(
  (match) => (match && match[1] ? `/api/v1/sso/saml/${match[1]}` : null),
  match(ssoRegex),
  trim
);

export function initialize(app) {
  // Skip initialization of arcade proxy if mocking is enabled
  const { useMock, arcadeLogLevel: logLevel, arcadeTarget: target } = getConfigVariables();
  if (useMock) {
    return;
  }

  const commonProxyOptions = {
    changeOrigin: true,
    logLevel,
    logProvider,
    target,
    ws: false,
  };

  // Setup our proxy
  app.use(
    '/arcade',
    (req, res, next) => {
      // For /ui paths, intercept writeHead to manage X-Frame-Options
      if (req.url.startsWith('/ui')) {
        const originalWriteHead = res.writeHead;
        res.writeHead = function (statusCode, statusMessage, headers) {
          // Remove backend's X-Frame-Options header
          this.removeHeader('X-Frame-Options');
          this.removeHeader('x-frame-options');

          // In production, set SAMEORIGIN for security
          // In development, omit header to allow cross-origin (localhost:3000 -> localhost:8088)
          if (process.env.NODE_ENV === 'production') {
            this.setHeader('X-Frame-Options', 'SAMEORIGIN');
          }

          return originalWriteHead.apply(this, arguments);
        };
      }

      next();
    },
    createProxyMiddleware({
      ...commonProxyOptions,
      pathRewrite: {
        '^/arcade': '', // remove arcade prefix
      },
      on: {
        proxyReq: relayRequestHeaders,
        proxyRes: relayResponseHeaders,
      },
    })
  );

  // support links redirection
  app.use(
    '/support',
    createProxyMiddleware({
      ...commonProxyOptions,
      hostRewrite: true,
      autoRewrite: true,
      protocolRewrite: true,
      pathRewrite: function (path, req) {
        return `/api/v1/support${path}`;
      },
      on: {
        proxyReq: relayRequestHeaders,
        proxyRes: (proxyRes, req, res) => {
          // We expect a 302 from Arcade so we can bounce to the correct support doc
          // if we don't see a 302, or there's a missing location header,
          // then we'll let our usual response flow handle this,
          // which should show the relevant error page
          if (proxyRes.statusCode !== 302 || !proxyRes.headers?.[DP.LOCATION_HEADER]) {
            relayResponseHeaders(proxyRes, req, res);
            return;
          }

          const locationValue = proxyRes.headers[DP.LOCATION_HEADER];
          res.statusCode = proxyRes.statusCode;

          if (locationValue) {
            res.append('x-syncari-oauth-redirect', locationValue);
            proxyRes.headers['x-syncari-oauth-redirect'] = locationValue;

            res.header(DP.LOCATION_HEADER, proxyRes.headers[DP.LOCATION_HEADER]);

            delete proxyRes.headers[DP.LOCATION_HEADER];
            logger.debug('Removing location header from the proxyRes');
          }
        },
      },
    })
  );

  // sso saml passthrough
  app.use(
    '/sso',
    createProxyMiddleware({
      ...commonProxyOptions,
      pathRewrite: transformSsoUrl,
      selfHandleResponse: true,
      on: {
        proxyReq: proxyReqWrite,
        proxyRes: (proxyRes, req, res) => {
          if (proxyRes.statusCode === 200) {
            if (DP.AUTH_TOKEN in proxyRes.headers) {
              addAuthCookies(proxyRes, res);
              // redirect home
              res.writeHead(302, { Location: DP.HOME_URL });
              res.end();
            }
          } else {
            let body = '';
            proxyRes.on('data', (chunk) => (body += chunk));
            proxyRes.on('end', () => {
              try {
                const parsed = JSON.parse(body);
                const message = encodeURIComponent(parsed.message || 'Unknown error');
                let errorPage = '/errors/error-500';

                if (proxyRes.statusCode === 400) {
                  errorPage = `/errors/error-400?errorType=auth&message=${message}`;
                } else if (proxyRes.statusCode === 404) {
                  errorPage = `/errors/error-404?errorType=notFound&message=${message}`;
                }
                res.writeHead(302, { Location: errorPage });
                res.end();
              } catch (e) {
                res.writeHead(302, { Location: '/errors/error-500' });
                res.end();
              }
            });
          }
        },
      },
    })
  );
}
