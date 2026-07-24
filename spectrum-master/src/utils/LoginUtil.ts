// @ts-nocheck
//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import Cookies from 'js-cookie';

import AppConstants from 'utils/AppConstants';

export function getCsrfToken() {
  return Cookies.get(AppConstants.CSRF_TOKEN);
}

/**
 * Get the bearer token that was saved to the cookies
 * @returns {String} the saved bearer token, otherwise undefined
 */
export function getBearerToken() {
  return Cookies.get(AppConstants.BEARER_TOKEN);
}

export function setBearerToken(token) {
  return Cookies.set(AppConstants.BEARER_TOKEN, token);
}
