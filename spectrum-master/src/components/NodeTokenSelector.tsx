import { useCallback } from 'react';

import { useTokensError, useTokensForNode, useTokensForSelectedNode } from 'store/tokens/hooks';
import { Token } from 'store/tokens/types';

import TokenSelector, { TokenSelectorProps } from './inputs/tokens/TokenSelector';

// helper to allow an explicit NodeId or fallback to the current selected node
const useTokensForNodeOrSelectedNode = (nodeId?: string) => {
  const selectedNodeResult = useTokensForSelectedNode({ skip: !!nodeId });
  // @ts-expect-error nodeId will be a string OR this query will be skipped
  const nodeResult = useTokensForNode({ nodeId, skip: !nodeId });

  return nodeId ? nodeResult : selectedNodeResult;
};

export type NodeTokenSelectorProps = {
  nodeId?: string;
  onTokenSelect: (token: Token) => void;
} & Pick<TokenSelectorProps, 'label'>;

const NodeTokenSelector = ({ nodeId, onTokenSelect, ...rest }: NodeTokenSelectorProps) => {
  const { isFetching, isLoading, getToken, tokens = {} } = useTokensForNodeOrSelectedNode(nodeId);
  const tokensError = useTokensError(tokens);

  const handleTokenSelect = useCallback(
    (tokenStr: string) => {
      const token = getToken(tokenStr);
      onTokenSelect(token);
    },
    [getToken, onTokenSelect]
  );

  return (
    <TokenSelector
      tokens={tokens}
      tokensError={tokensError}
      tokensLoading={isLoading || isFetching}
      onTokenSelect={handleTokenSelect}
      {...rest}
    />
  );
};

export default NodeTokenSelector;
