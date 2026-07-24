import { TOKEN_BEGIN_SENTINEL } from 'components/inputs/tokens/constants';

import { Token } from './types';

// used for test and when we don't get a getToken fn back from our hook
export const makeFakeToken = (tokenKey: string, shortLabel?: string): Token => {
  const _tokenKey = tokenKey.startsWith(TOKEN_BEGIN_SENTINEL) ? tokenKey.slice(2, -2) : tokenKey;

  return {
    datatype: 'object',
    group: 'Others',
    label: _tokenKey,
    shortLabel: shortLabel || _tokenKey,
    token: tokenKey,
    value: _tokenKey,
  };
};

export const selectTokensMapForNode = (tokens: Record<string, Token[]>) =>
  tokens
    ? Object.values(tokens)
        .flat()
        .reduce((acc, token) => {
          acc[token.token] = token;
          return acc;
        }, {} as Record<string, Token>)
    : {};
