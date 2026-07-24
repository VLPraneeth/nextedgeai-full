//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { navigate } from '@reach/router';
import { fetchBaseQuery } from '@reduxjs/toolkit/query/react';
import axios, { AxiosRequestConfig } from 'axios';
import { defer } from 'lodash';
import JSONbig from 'json-bigint';

import RouteConstants, { ERROR_MESSAGE, REDIRECT_TO } from './RouteConstants';
import { makeUrl } from './UrlUtil';

type XsrfMetaElement = Element & { content: string };

export const XSRF_TOKEN_KEY = 'x-xsrf-token';
const xsrfMeta = document.querySelector<XsrfMetaElement>(`meta[name="${XSRF_TOKEN_KEY}"]`);
export const xsrfToken = xsrfMeta ? xsrfMeta.content : undefined;

const CONTENT_TYPE = 'Content-Type';
const CONTENT_TYPE_JSON = 'application/json';

// Configure json-bigint to store bigints as strings
const JSONbigString = JSONbig({ storeAsString: true });

const axiosOptions = xsrfToken ? { headers: { [XSRF_TOKEN_KEY]: xsrfToken } } : {};

const axiosOptionsBigInt = {
  ...axiosOptions,
  transformResponse: [
    (data: string) => {
      try {
        const parsed = JSONbigString.parse(data);
        // Convert any BigInt values to strings
        const stringifyAndParse = JSON.parse(
          JSON.stringify(parsed, (_, value) => (typeof value === 'bigint' ? value.toString() : value))
        );
        return stringifyAndParse;
      } catch (err) {
        return data;
      }
    },
  ],
};

const client = axios.create(axiosOptions);
const clientBigInt = axios.create(axiosOptionsBigInt);

export const HTTP = {
  FORBIDDEN: 403,
  UNAUTHORIZED: 401,
  OK: 200,
  NOT_FOUND: 404,
};

export function redirectToLogin(message?: string) {
  // Redirect back the user to login with return url if then session timed out
  // or accessing a deep link.
  // Note: Next tick before redirecting so it does not get cancelled
  defer(() => {
    if (
      !window.location.pathname.startsWith('/insightssharing/') &&
      !new RegExp(`^${RouteConstants.LOGIN}`, 'i').test(window.location.pathname)
    ) {
      navigate(
        makeUrl(
          RouteConstants.LOGIN,
          {},
          {
            [REDIRECT_TO]: `${window.location.pathname}${window.location.search}`,
            [ERROR_MESSAGE]: (message && encodeURIComponent(message)) || '',
          }
        ),
        {
          replace: true,
        }
      );
    }
  });
}

/*
 * Response Intercepters
 */
client.interceptors.response.use(
  // no processing of success
  (response) => response,
  (error) => {
    if (error?.response?.status === HTTP.UNAUTHORIZED) {
      redirectToLogin(error.response?.data?.message);
    }

    // pass rejection to consumer
    return Promise.reject(error);
  }
);

export const get = client.get;
export const post = client.post;
export const patch = client.patch;
export const put = client.put;
export const deleteRequest = client.delete;

// BigInt support for get/post requests
export const getBigInt = clientBigInt.get;
export const postBigInt = clientBigInt.post;

export function request(obj: AxiosRequestConfig) {
  // Default to json content type
  if (!obj?.headers?.[CONTENT_TYPE]) {
    obj.headers = {
      [CONTENT_TYPE]: CONTENT_TYPE_JSON,
    };
  }

  return client(obj);
}

// TODO: Theres no existing fetchBaseQuery for axios so we'll use
// fetch that came with RTKQuery. Option is to check how compatible fetch
// and axios request params and we could just override the fetch parameter of fetchBaseQuery
// or transform it to axios. We can evaluate fetch and switch to it...
export function makeBaseQuery(baseUrl: string) {
  return fetchBaseQuery({
    baseUrl,
    prepareHeaders: (headers: Headers) => {
      if (xsrfToken) {
        headers.set(XSRF_TOKEN_KEY, xsrfToken);
      }
      // Default to json content type
      if (!headers.get(CONTENT_TYPE)) {
        headers.set(CONTENT_TYPE, CONTENT_TYPE_JSON);
      }
      return headers;
    },
  });
}

export interface RequestExceptionType {
  data: {
    message: string;
  };
}

export const RequestException = (function (this: RequestExceptionType, message: string) {
  this.data = {
    message,
  };
} as unknown) as { new (message: string): RequestExceptionType };

export interface RequestResponseExceptionType {
  response: {
    data: {
      message: string;
    };
  };
}

export interface ErrorResponseData {
  error: string;
  message: string;
  timestamp: string;
  status: number;
}

export default client;
