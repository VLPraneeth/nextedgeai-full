import { FieldDataType } from 'components/types';
import { FetchStatus, ResponseError } from 'store/types';

export interface TokenError {
  message?: string;
  description?: string;
}

export interface Token {
  datatype: FieldDataType;
  group: string;
  label: string;
  shortLabel: string;
  token: string;
  value: string;
  error?: TokenError[];
}

export const GET_TOKENS_FOR_NODE_PENDING = 'GET_TOKENS_FOR_NODE_PENDING';

interface GetTokensForNodePending {
  type: typeof GET_TOKENS_FOR_NODE_PENDING;
  payload: {
    nodeId: string;
  };
}

export const GET_TOKENS_FOR_NODE_FULFILLED = 'GET_TOKENS_FOR_NODE_FULFILLED';

interface GetTokensForNodeFulfilled {
  type: typeof GET_TOKENS_FOR_NODE_FULFILLED;
  payload: {
    nodeId: string;
    data: any;
  };
}

export const GET_TOKENS_FOR_NODE_FAILED = 'GET_TOKENS_FOR_NODE_FAILED';

interface GetTokensForNodeFailed {
  type: typeof GET_TOKENS_FOR_NODE_FAILED;
  payload: {
    nodeId: string;
    error: ResponseError;
  };
}

export type TokensAction = GetTokensForNodePending | GetTokensForNodeFulfilled | GetTokensForNodeFailed;

export interface TokensState {
  tokensForNodeStatus: Record<string, FetchStatus>;
  // Shape, { nodeId: { tokenGroup: Token[] }}
  tokensForNode: Record<string, Record<string, Token[]>>;
}
