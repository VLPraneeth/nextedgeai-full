import { useCallback, useMemo } from 'react';

import { safeDecodeURIComponent } from 'utils/StringUtil';

/**
 * Converts a querystring into a record
 */
const parseQueryString = <T extends {}>(querystring: string): T =>
  Object.fromEntries(
    querystring
      .substring(1) // removes "?"
      .split('&')
      .filter(Boolean)
      .map((params) => params.split('=').map((value) => safeDecodeURIComponent(value).replace(/\+/g, ' ')))
  );

// supports similar usage to useState updater,
// can pass a new object, or use a callback which provides the current value as an arg
type QueryStringUpdate = string | Record<string, string> | undefined;
type QueryStringUpdater = (update: QueryStringUpdate, options?: QueryStringUpdaterOptions) => void;
type QueryStringUpdaterOptions = {
  replaceState?: boolean;
  // EncodeToPlus will encode space to + otherwise to %20.
  // Note: It will always encode it but the difference is space
  encodeToPlus?: boolean;
};

const useQueryParams = <Params extends {}, Result extends Partial<Params> = Partial<Params>>(): [
  Result,
  QueryStringUpdater
] => {
  const { search } = window.location;

  const params = useMemo(() => parseQueryString<Result>(search), [search]);

  const setParams = useCallback(
    (
      updatedParams: QueryStringUpdate,
      { replaceState = false, encodeToPlus = true }: QueryStringUpdaterOptions = {}
    ) => {
      const updatedSearch =
        !encodeToPlus && updatedParams && typeof updatedParams !== 'string'
          ? encodeObjectToSearchParams(updatedParams)
          : new URLSearchParams(updatedParams ?? '').toString();

      const { protocol, host, pathname } = window.location;
      const baseUrl = `${protocol}//${host}${pathname}`;
      const newUrl = updatedSearch ? `${baseUrl}?${updatedSearch}` : baseUrl;

      const historyFn = replaceState ? window.history.replaceState : window.history.pushState;
      historyFn.call(window.history, { path: newUrl }, '', newUrl);
    },
    []
  );

  return [params, setParams];
};

export const encodeObjectToSearchParams = (params: Record<string, string>) => {
  return Object.keys(params)
    .reduce((acc: string[], key) => {
      return [...acc, `${key}=${encodeURIComponent(params[key])}`];
    }, [])
    .join('&');
};

export default useQueryParams;
