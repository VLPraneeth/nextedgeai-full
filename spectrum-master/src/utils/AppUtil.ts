//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { navigate } from '@reach/router';
import { cloneDeep, isEmpty, isFunction, isObject, isString } from 'lodash';

import { APPLICATION_ERROR } from 'store/app/app.types';
import { t, tc } from 'utils/i18nUtil';

import { RootState } from '../reducers';
import AppConstants from './AppConstants';
import { ValuesOf } from './TypeUtils';

// Number of maximum number of action we will
// be capturing for the phone home
const MAX_LAST_ACTION_LOG = 15;

// Global syncari namespace
export const SYNCARI_NS = 'SYNCARI_NS';

/**
 * Resolves an arcade UI path to the correct URL based on environment
 * In development, returns full URL to proxy server (different port)
 * In production, returns relative path (same origin)
 *
 * @param path - The arcade UI path (e.g., '/arcade/ui/hello' or just 'hello')
 * @returns The resolved URL
 */
export function getArcadeUiUrl(path: string): string {
  // Ensure path starts with /arcade/ui
  const arcadePath = path.startsWith('/arcade/ui') ? path : `/arcade/ui${path.startsWith('/') ? '' : '/'}${path}`;

  // In development, React dev server runs on port 3000, proxy on different port
  // Iframe requests need full URL to reach the proxy server
  // Use REACT_APP_PROXY_URL if set, otherwise fall back to package.json proxy value
  if (process.env.NODE_ENV === 'development') {
    const proxyUrl = process.env.REACT_APP_PROXY_URL || 'http://localhost:8088';
    return `${proxyUrl}${arcadePath}`;
  }

  // In production, everything runs on same origin/port
  return arcadePath;
}

export const RESPONSE_CODE = {
  UNAUTHORIZED: 401,
  APPLICATION_ERROR: 500,
  NOT_FOUND: 404,
  GATEWAY_TIMEOUT_ERROR: 504,
} as const;

export function isUnauthorizedError(resp: unknown) {
  return isErrorResponse(resp, RESPONSE_CODE.UNAUTHORIZED);
}

export function isApplicationError(resp: unknown) {
  return isErrorResponse(resp, RESPONSE_CODE.APPLICATION_ERROR);
}

export function isGatewayTimeoutError(resp: unknown) {
  return isErrorResponse(resp, RESPONSE_CODE.GATEWAY_TIMEOUT_ERROR);
}

function isErrorResponse(resp: any, code: ValuesOf<typeof RESPONSE_CODE>) {
  return resp.status === code;
}

export function isResourceNotFound(resp: unknown) {
  const resourceNotFound = isErrorResponse(resp, RESPONSE_CODE.NOT_FOUND);
  // We are matching not found text here since the backend does not return a 404
  // on resource not found We will just rely on the text. We should have a
  // proper 404 error and resource message in the future

  // TODO: Type the error object so we can do a type guard this
  if (!resourceNotFound && (resp as any)?.response?.data?.message?.match(/not found$/)) {
    return true;
  }
  return resourceNotFound;
}

export function getApplicationError(error: any) {
  if (!error || !error.response) {
    return {};
  }

  let data: any = {};

  if (isString(error.response.data)) {
    data.message = error.response.data;
  } else if (isObject(error.response.data)) {
    data = error.response.data;
  }
  return {
    applicationError: {
      ...data,
      status: error.response.status,
      statusText: error.response.statusText,
    },
  };
}

export function getConnectorError(error: any) {
  const data: any = {};

  if (!isEmpty(error?.data?.errors)) {
    data.errorMessage = error.data.errors.join(' ');
    data.errors = error.data.errors;
  } else if (error?.data?.message) {
    data.errorMessage = error.data.message;
  }
  return data;
}

export type ResponseError = {
  data?: any;
  message?: string;
  errorMessage: string;
  status: number;
  statusText: string;
};

// TODO: Refactor/rename/retype this function. It returns a custom
// Response object, not a message, but it's usage throughout the App
// seems to expect a string and TypeScript isn't flagging the mismatch.
// May be causing error messages to not display in various places.
// Rename to processErrorResponse() ?
// Add fallback messages so there isn't an empty object?
export function getErrorMessage(error: Error | any): ResponseError {
  let data: ResponseError = {
    status: error?.response?.status,
    statusText: error?.response?.statusText,
    errorMessage: '',
  };

  // A 502 is usually sent from the load balancer when arcade doesn't respond
  // within 2 minutes. We need to show a user friendly message instead of the
  // html returned from the load balancer.
  if (error?.response?.status === 502) {
    const timeoutMessage = t('ApiUtil.server_error_message');
    return {
      status: error?.response?.status,
      statusText: timeoutMessage,
      errorMessage: timeoutMessage,
    };
  }

  if (!error?.response) {
    return data;
  }

  if (isString(error.response.data)) {
    data.message = error.response.data;
    data.errorMessage = error.response.data;
  } else if (isObject(error.response.data)) {
    data = error.response.data;
    data.errorMessage = error.response.data.message;
  }

  return data;
}

export function handleAsApplicationError(dispatch: any) {
  return (error: any) => {
    dispatch({
      type: APPLICATION_ERROR,
      ...getApplicationError(error),
    });
  };
}

export function triggerResize() {
  // IE friendly resize
  var resizeEvent = window.document.createEvent('UIEvents');
  // @ts-ignore
  if (resizeEvent.initUIEvent) {
    // @ts-ignore
    resizeEvent.initUIEvent('resize', true, false, window, 0);
    window.dispatchEvent(resizeEvent);
  } else {
    // Modern browser should support this resize event
    window.dispatchEvent(new Event('resize'));
  }
}

export interface NavigateToParams {
  changed: boolean;
  showConfirmModal: (show: boolean) => void;
  setNavigatingTo: (url: string) => void;
  state?: Record<string, any>;
}
export function navigateTo(url: string, params?: NavigateToParams) {
  if (!params || !params.changed) {
    navigate(url, { state: params?.state });
  } else if (isFunction(params.showConfirmModal)) {
    params.setNavigatingTo(url);
    params.showConfirmModal(true);
  }
}

export function getNavigateParams(obj: any) {
  let {
    changedId,
    changedScope,
    changed,
    url,
    showUnsavedConfirmModal,
    showConfirmModal,
    graphChanged,
    setNavigatingTo,
  } = obj;

  if (!showConfirmModal) {
    showConfirmModal = showUnsavedConfirmModal;
  }

  return {
    changedId,
    changedScope,
    changed,
    url,
    showConfirmModal,
    graphChanged,
    setNavigatingTo,
  };
}

export function mapNavigateStateToProps(state: RootState) {
  return {
    changed: state.pipeline.changed,
    changedId: state.pipeline.changedId,
    changedScope: state.pipeline.changedScope,
  };
}

export function can(userCapabilities: string[], requiredCapabilities: string | string[]) {
  if (!Array.isArray(requiredCapabilities)) {
    requiredCapabilities = [requiredCapabilities];
  }
  return userCapabilities.some((userCapability) => requiredCapabilities.includes(userCapability));
}

export function setWindowTitle(title: string) {
  const newTitle = (title ? `${title} - ` : '') + tc('syncari');
  if (newTitle !== window.document.title) {
    window.document.title = newTitle;
  }
}

export function appendAction(actions: any[], action: any, maxActions = MAX_LAST_ACTION_LOG) {
  let localActions = cloneDeep(actions);
  if (localActions) {
    const length = localActions.length - (maxActions - 1);
    localActions = localActions.slice(length <= 0 ? 0 : length);
    localActions.push(cloneDeep(action));
  }
  return localActions;
}

export const noop = () => {};

// Convert rtk statuses to redux fetch status
export const makeFetchStatus = (
  isSuccess: boolean,
  isError: boolean,
  isLoading: boolean,
  isFetching: boolean = false
) => {
  if (isSuccess) {
    return AppConstants.FETCH_STATUS.SUCCESS;
  }
  if (isError) {
    return AppConstants.FETCH_STATUS.ERROR;
  }
  if (isLoading || isFetching) {
    return AppConstants.FETCH_STATUS.LOADING;
  }
  return AppConstants.FETCH_STATUS.IDLE;
};
