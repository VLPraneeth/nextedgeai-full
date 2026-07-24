//
// Copyright (c) 2019-Present Syncari - All rights reserved.
// This middleware will check if we need to serve the index page for path with arcade prefix
//
import AppConstants from 'utils/AppConstants';

import serveIndex from '../serveIndex';

// Known url with arcade prefix that should not be redirected to arcade
const OVERRIDE_ARCADE_PATH = [
  /\/arcade\/api\/v1\/oauth\/authorize.*/,
  /\/arcade\/oauth\/authorize.*/,
  /\/arcade\/api\/v1\/oauth2\/authorize.*/,
  /\/arcade\/api\/v1\/oauth2\/consent.*/,
];

export default function overrideArcade(req, res, next) {
  if (shouldServeIndex(req?.url)) {
    // Only allow requests without xsrf token to make sure
    // it is the root request and not an ajax request.
    // Check if the token is in the header
    let token = req.header(AppConstants.XSRF_TOKEN_KEY);

    if (!token) {
      token = req.body?.[AppConstants.XSRF_TOKEN_KEY];
    }

    // Extra check to make sure its not an ajax request
    if (!token && req.accepts(AppConstants.TEXT_HTML)) {
      return serveIndex(req, res);
    } else {
      // Proxy the request since its coming from our application
      next();
    }
  } else {
    next();
  }
}

function shouldServeIndex(url) {
  return OVERRIDE_ARCADE_PATH.some((urlRegex) => url?.match(urlRegex));
}
