import { GraphModel } from 'store/pipeline/types';
import DataUrlConstants from 'utils/DataUrlConstants';
import { makeUrl } from 'utils/UrlUtil';

import { injectEndpoints, tags } from '../api';
import { Token } from './types';
import { selectTokensMapForNode } from './utils';

const removeKey = (xs: any[]) => xs.map(({ key, ...x }) => x);

type GetTokensForNodeParams = {
  nodeId: string;
  currentGraph: GraphModel;
};

type GetTokensForNodeResult = {
  tokens: Record<string, Token[]>;
  tokenMap: Record<string, Token>;
};

const tokensApi = injectEndpoints({
  endpoints: (builder) => ({
    getTokensForNode: builder.query<GetTokensForNodeResult, GetTokensForNodeParams>({
      query: ({ nodeId, currentGraph }) => {
        // we need to remove `key` from each node and edge before submitting to arcade
        const cleanGraph = {
          ...currentGraph,
          nodes: removeKey(currentGraph?.nodes || []),
          edges: removeKey(currentGraph?.edges || []),
        };

        return {
          body: cleanGraph,
          headers: { 'content-type': 'application/json' },
          method: 'POST',
          url: makeUrl(DataUrlConstants.TOKENS_FOR_NODE, { nodeId }),
        };
      },
      providesTags: (_, __, { nodeId }) => [tags.NodeTokens(nodeId)],
      transformResponse: (data: Record<string, Token[]>) => {
        return {
          tokens: data,
          tokenMap: selectTokensMapForNode(data),
        };
      },
    }),
  }),
  overrideExisting: false,
});

export const { useGetTokensForNodeQuery } = tokensApi;
