//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import cookie from 'cookie';
import Tokens from 'csrf';
import { each, snakeCase, startCase } from 'lodash';

import AppConstants from 'utils/AppConstants';
import { getConfigVariables, SECRET_CONFIGS } from 'utils/ConfigUtil';
import { logger } from 'utils/LogUtil';

/**
 * Print the welcome messages when starting the server or when
 * any settings have changed.
 */
export function printWelcome() {
  const { useMock, mockTarget, spectrumPort, arcadeTarget, pubsubKey, ...configVars } = getConfigVariables();

  logger.info('Config Variables:');
  logger.info(`Port spectrum will listen: ${spectrumPort}`);

  if (useMock) {
    logger.info(`Using mock server: ${useMock}`);
    logger.info(`Redirecting requests to: ${mockTarget}`);
  } else {
    logger.info(`Redirecting arcade requests to: ${arcadeTarget}`);
  }

  each(configVars, (val, key) => {
    if (val && SECRET_CONFIGS.indexOf(snakeCase(key).toUpperCase()) === -1) {
      logger.info(`${startCase(key)}: ${val}`);
    }
  });
}

/**
 * Generate or use the session cookie xsrf token
 */
export function getXsrfCookie(req, res) {
  const { secureCookies } = getConfigVariables();
  let token;
  const cookies = cookie.parse(req.headers.cookie || '');
  if (cookies?.[AppConstants.XSRF_TOKEN_KEY]) {
    token = cookies[AppConstants.XSRF_TOKEN_KEY];
  } else {
    const tokens = new Tokens();
    token = tokens.create(tokens.secretSync());
    res.cookie(AppConstants.XSRF_TOKEN_KEY, token, {
      httpOnly: true,
      expires: 0, // Session cookie
      secure: secureCookies,
      sameSite: 'lax',
    });
  }
  return token;
}
