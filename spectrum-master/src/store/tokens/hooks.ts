import { useMemo } from 'react';

import { TokensError } from 'components/inputs/tokens/TokenSelector';
import { useEnhancedSelector } from 'hooks/redux';

import { useGetTokensForNodeQuery } from './api';
import { Token, TokenError } from './types';
import { makeFakeToken, selectTokensMapForNode } from './utils';

export type UseTokensForNodeParams = {
  nodeId: string;
  skip?: boolean;
  suppliedTokens?: Record<string, Token[]>;
};

export const useTokensForNode = ({ nodeId, skip: providedSkip = false, suppliedTokens }: UseTokensForNodeParams) => {
  const currentGraph = useEnhancedSelector((state) => state.pipeline.currentGraph);

  // This code will never get to the providedSkip. Need more testing before removing the provideddSkip and currentGraph
  const skip = Boolean(!nodeId) ?? providedSkip ?? Boolean(!currentGraph);

  const { isFetching, isError, isLoading, isSuccess, data, error } = useGetTokensForNodeQuery(
    // @ts-expect-error currentGraph must be a GraphModel, but we skip if it's missing
    { nodeId, currentGraph },
    {
      skip,
    }
  );

  return useMemo(() => {
    const { tokens = {}, tokenMap = suppliedTokens ? selectTokensMapForNode(suppliedTokens) : {} } = data || {};

    return {
      error: error as TokensError,
      isError,
      isFetching,
      isLoading,
      isSuccess,
      tokens,
      tokenMap,
      getToken: (tokenKey: string) => {
        return tokenMap[tokenKey] ?? makeFakeToken(tokenKey);
      },
    };
  }, [suppliedTokens, data, error, isError, isFetching, isLoading, isSuccess]);
};

// convenience hook to get the tokens for the selected node
export const useTokensForSelectedNode = ({
  skip = false,
  suppliedTokens,
}: Pick<UseTokensForNodeParams, 'skip' | 'suppliedTokens'> = {}) => {
  const selectedGraphNode = useEnhancedSelector((state) => state.entityPipeline.selectedGraphNode);

  const nodeId: string | undefined = selectedGraphNode?.id;

  // @ts-expect-error nodeId will be a string OR this query will be skipped
  return useTokensForNode({ nodeId, skip: skip ?? !nodeId, suppliedTokens });
};

export const useTokensError = (tokens: Record<string, Token[]>) => {
  const tokensError = useMemo(() => {
    let tokensError: TokenError | undefined = undefined;

    const tokensKeys = Object.keys(tokens);
    if (tokensKeys.length > 0 && tokensKeys.includes('error')) {
      const error = tokens['error'].find((value) => value.error)?.error;
      let message = '';
      let description = '';

      error?.forEach((object) => {
        if (!message && object.message) {
          message = object.message;
        }

        if (!description && object.description) {
          description = object.description;
        }
      });

      if (message || description) {
        tokensError = {
          message,
          description,
        };
      }
    }

    return tokensError;
  }, [tokens]);

  return tokensError;
};
