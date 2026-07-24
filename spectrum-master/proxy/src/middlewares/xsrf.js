//
// Copyright (c) 2019-Present Syncari - All rights reserved.
// This middleware will check for all the requests that goes to /arcade and check for xsrf tokens
//
import cookie from 'cookie';

import AppConstants from 'utils/AppConstants';
import { logger } from 'utils/LogUtil';

// Known url that does not send the xsrf header
const WHITELIST = [
  '/arcade/api/v1/organization/photo',
  '/arcade/api/v1/user/photo',
  '/arcade/api/v1/brand/logoSquare',
  '/arcade/api/v1/brand/logo',
  '/arcade/api/v1/saml/sso/workramp',
  '/arcade/api/v1/oauth2/authorize',
  '/arcade/api/v1/oauth2/consent',
  '/arcade/api/v1/oauth2/token',
  '/arcade/api/v1/oauth2/register',
  '/arcade/health',
  '/arcade/api/v1/user',
  '/arcade/api/v1/user/preference',
  '/arcade/api/v1/studio/data/entity/[0-9a-z]{24}/records/downloadfile/[0-9a-z]{24}',
  '/arcade/api/v1/connectormeta/[0-9a-z]{24}/.*',
  '/arcade/api/v1/organization/icon.*',
  '/version',
  /^\/sso\/.*\/assertion$/,
  /^\/arcade\/api\/v1\/quickstart\/icon\/.*/,

  // Temporary whitelist our oauth authorize.
  /^\/arcade\/api\/v1\/oauth\/authorize.*/,
  /^\/arcade\/oauth\/authorize.*/,
  /^\/mcp\/oauth\/authorize.*/,

  /^\/arcade\/api\/v1\/webhooks\/.*/,

  // Whitelist arcade UI paths for iframe content
  /^\/arcade\/ui\/.*/,
];

// Ideally we check for all the url and only whitelist some urls but
// the proxy also serves assets that has dynamic file names so it will
// be impossible. We only have matches here for now.
const XSRF_URL = [/^\/arcade/gi, /^\/sso/gi, /^\/version/gi];

export default function xsrfChecker(req, _res, next) {
  if (shouldEnforceXsrf(req?.url)) {
    let token = req.header(AppConstants.XSRF_TOKEN_KEY);
    if (!token) {
      token = req.body?.[AppConstants.XSRF_TOKEN_KEY];
    }
    if (token) {
      let cookies = cookie.parse(req.headers?.cookie || '');
      if (cookies && cookies[AppConstants.XSRF_TOKEN_KEY]) {
        if (cookies[AppConstants.XSRF_TOKEN_KEY] === token) {
          return next();
        }
      }
    }

    const errMsg = `Invalid XSRF Token for url ${req?.url}`;
    let err = new Error(errMsg);
    logger.info(errMsg);
    err.statusCode = 403;
    next(err);
  } else {
    next();
  }
}

export function shouldEnforceXsrf(url) {
  return (
    XSRF_URL.some((urlRegex) => url?.match(urlRegex)) &&
    !WHITELIST.some((pattern) => new RegExp(pattern).test(url?.replace(/\/$/, '')?.toLowerCase()))
  );
}
