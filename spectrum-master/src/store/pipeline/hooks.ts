import { filter } from 'lodash';
import { useCallback, useMemo } from 'react';

import { getAsyncNodeConfig } from 'actions/entityPipelineActions';
import { NodeModel } from 'components/GraphItemFilter';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { NodeUIModel } from 'pages/sync-studio/NodePanel';
import { selectConnectorEntities } from 'store/entity-pipeline/selectors';
import { usePicklistAdditionalConfig } from 'store/picklists/hooks';
import AppConstants from 'utils/AppConstants';
import { getDynamicConfigParams, getNodeConfig, hasDynamicConfig } from 'utils/NodeConfigUtil';

import {
  selectAttributeNodes,
  selectConnectors,
  selectDynamicConfigErrorMessage,
  selectDynamicConfigStatus,
  selectDynamicConfigValues,
  selectPipelineActions,
  selectPipelineFunctions,
  selectSelectedGraphNode,
} from './selectors';
import { Node } from './types';

export const useConnectors = () => useEnhancedSelector(selectConnectors);

// TODO: Add connector types when moving reducer
export const useActiveConnectors = () => {
  const connections = useEnhancedSelector(selectConnectors);
  return filter(
    connections,
    (connection) => connection.status === AppConstants.CONNECTOR_STATUS.ACTIVE && connection.name !== 'syncari'
  );
};

export const useStaticNodeConfig = (node: NodeUIModel | NodeModel) => {
  const attributeNodes = useEnhancedSelector((state) => selectAttributeNodes(state));
  const connectorEntities = useEnhancedSelector((state) => selectConnectorEntities(state));
  const actions = useEnhancedSelector((state) => selectPipelineActions(state));
  const functions = useEnhancedSelector((state) => selectPipelineFunctions(state));

  return useMemo(() => {
    return getNodeConfig(node, {
      connectorEntities,
      attributeNodes,
      functions,
      actions,
    });
  }, [actions, attributeNodes, connectorEntities, functions, node]);
};

export const useDynamicNodeConfig = (node: NodeUIModel | NodeModel) => {
  const nodeId = node?.id;

  const dispatch = useEnhancedDispatch();
  const dynamicConfigValues = useEnhancedSelector(selectDynamicConfigValues);
  const dynamicConfigStatus = useEnhancedSelector(selectDynamicConfigStatus);
  const dynamicConfigErrorMessage = useEnhancedSelector(selectDynamicConfigErrorMessage);
  const nodeConfig = useStaticNodeConfig(node);

  const loading = nodeId ? dynamicConfigStatus[nodeId] === AppConstants.FETCH_STATUS.LOADING : false;

  const isValidDynamicConfig = useCallback(
    (nodeConfig: Node) => {
      return nodeId && dynamicConfigValues?.[nodeId] && dynamicConfigValues?.[nodeId]?.name === nodeConfig.name;
    },
    [dynamicConfigValues, nodeId]
  );

  const fetchNodeConfig = useCallback(
    (graphJson: any) => {
      if (nodeId && !loading) {
        dispatch(getAsyncNodeConfig(nodeId, graphJson, getDynamicConfigParams(nodeConfig)));
      }
    },
    [dispatch, loading, nodeConfig, nodeId]
  );

  return useMemo(() => {
    return {
      data: nodeId ? dynamicConfigValues?.[nodeId] : {},
      dynamicConfigValues,
      error: nodeId ? dynamicConfigErrorMessage[nodeId] : null,
      fetch: fetchNodeConfig,
      isValidDynamicConfig,
      loading,
      success: dynamicConfigStatus[nodeId || ''] === AppConstants.FETCH_STATUS.SUCCESS,
    };
  }, [
    dynamicConfigErrorMessage,
    dynamicConfigStatus,
    dynamicConfigValues,
    fetchNodeConfig,
    isValidDynamicConfig,
    loading,
    nodeId,
  ]);
};

/**
 * combination of static and dynamic node configuration
 *
 */
export const useNodeConfig = (node: NodeUIModel | NodeModel) => {
  const staticConfig = useStaticNodeConfig(node);
  const { isValidDynamicConfig, data, error, loading, success, fetch } = useDynamicNodeConfig(node);

  const { insertAdditionalConfig } = usePicklistAdditionalConfig({ functionName: staticConfig?.name });

  const nodeHasDynamicConfig: boolean = useMemo(() => hasDynamicConfig(staticConfig), [staticConfig]);

  const config = useMemo(() => {
    if (nodeHasDynamicConfig) {
      if (node?.id && isValidDynamicConfig(staticConfig)) {
        return {
          ...data,
          configuration: insertAdditionalConfig(data.configuration, node?.metadata?.configuration),
        };
      }
      return;
    }

    return {
      ...staticConfig,
      configuration: insertAdditionalConfig(staticConfig?.configuration, node?.metadata?.configuration),
    };
  }, [
    data,
    insertAdditionalConfig,
    isValidDynamicConfig,
    node?.id,
    node?.metadata?.configuration,
    nodeHasDynamicConfig,
    staticConfig,
  ]);

  return useMemo(
    () => ({
      config,
      error,
      loading: Boolean(nodeHasDynamicConfig && loading),
      success,
      hasDynamicConfig: nodeHasDynamicConfig,
      fetch,
    }),
    [config, error, fetch, loading, nodeHasDynamicConfig, success]
  );
};

/** HOF to build hooks for the currently selected node in state */
const withSelectedNode = <OtherArgs extends unknown[], Return extends unknown>(
  hook: (node: NodeUIModel | NodeModel, ...rest: OtherArgs) => Return
) => {
  return (...rest: OtherArgs) => {
    const selectedNode = useEnhancedSelector(selectSelectedGraphNode);
    hook(selectedNode, ...rest);
  };
};

/** convenience hooks for above, using the currently selected node */
export const useStaticNodeConfigForSelectedNode = withSelectedNode(useStaticNodeConfig);
export const useDynamicNodeConfigForSelectedNode = withSelectedNode(useDynamicNodeConfig);
export const useNodeConfigForSelectedNode = withSelectedNode(useNodeConfig);
