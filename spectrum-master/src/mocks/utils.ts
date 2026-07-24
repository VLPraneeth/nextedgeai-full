import { getTokens } from 'utils/StringUtil';
import { makeUrl } from 'utils/UrlUtil';

/**
 * makeTesturl
 *
 * converts our DataUrlConstants into a standard node route format.
 * Example,
 * makeTestUrl("/test/{{orgId}}/{{userId}}") // => "/test/:orgId/:userId"
 *
 */
export const makeTestUrl = (url: string) => {
  const urlTokens = (getTokens(url) || []).reduce((acc, token) => {
    acc[token] = `:${token}`;
    return acc;
  }, {} as Record<string, string>);

  return makeUrl(url, urlTokens);
};
