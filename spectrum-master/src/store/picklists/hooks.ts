import { useCallback, useEffect, useMemo, useState } from 'react';

import { PicklistValue } from 'components/inputs/types';
import { SkullConfig, SkullInput } from 'components/skull';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { NodeKeyValue } from 'pages/sync-studio/node-config/NodeConfigPanel';
import { selectSelectedGraphNode } from 'selectors/pipelineSelectors';
import { PipelineFunction } from 'store/pipeline-functions';

import { useLazyGetAdditionalConfigQuery } from './api';
import { fetchPicklistValues as fetchPicklistValuesThunk, FetchPicklistValuesParams } from './thunks';
import { PicklistsState } from './types';

type FetchPicklistValuesFn = <T extends unknown>(params: T) => void;
type UsePicklistValuesResult = [PicklistsState['picklistValues'], FetchPicklistValuesFn];

/** helper to expose redux state kind of like useState */
export const usePicklistValues = (): UsePicklistValuesResult => {
  const dispatch = useEnhancedDispatch();

  const picklistValues = useEnhancedSelector((state) => state.picklist.picklistValues);
  const fetchPicklistValues = useCallback(
    (params) => {
      if (!picklistValues[params.id]) {
        dispatch(fetchPicklistValuesThunk(params));
      }
    },
    [dispatch, picklistValues]
  );

  return [picklistValues, fetchPicklistValues];
};

interface OptionalFetchPicklistValuesParams extends Omit<FetchPicklistValuesParams, 'id'> {
  id: string | undefined;
}

export const usePicklistValuesForId = ({
  id,
  dependantId,
  dependantType,
}: OptionalFetchPicklistValuesParams): [PicklistValue[] | undefined, () => void] => {
  const dispatch = useEnhancedDispatch();
  const picklistValues = useEnhancedSelector((state) => (id ? state.picklist.picklistValues?.[id] : undefined));

  const fetchPicklistValues = useCallback(() => {
    if (!picklistValues && id) {
      dispatch(
        fetchPicklistValuesThunk({
          id,
          dependantId,
          dependantType,
        })
      );
    }
  }, [picklistValues, id, dispatch, dependantId, dependantType]);

  useEffect(() => {
    fetchPicklistValues();
  }, [fetchPicklistValues]);

  return useMemo(() => [picklistValues, fetchPicklistValues], [fetchPicklistValues, picklistValues]);
};

interface FunctionConfigurationCache {
  nodeId: string;
  functionName: string;
  inputName: string;
  inputValue: string;
  configurations: SkullInput[];
}

// For http synapse we need to add the key "entity" because "entityDefinition" is used in a different context in the backend code.
const getAdditionalConfigValues = (configuration: SkullInput | undefined, values: NodeKeyValue) => {
  return configuration?.isHttpSourceEntity ? Object.assign({}, values, { entity: values?.entityDefinition }) : values;
};

export const usePicklistAdditionalConfig = ({ functionName }: { functionName: string }) => {
  const selectedNode = useEnhancedSelector(selectSelectedGraphNode);
  const currentGraph = useEnhancedSelector((state) => state.pipeline.currentGraph);
  const nodeId = selectedNode?.id;

  // We're doing manual caching since rtk query couldn't reliably cache heavy body requests
  const [configurationCache, setConfigurationCache] = useState<FunctionConfigurationCache[]>([]);

  const [
    getAdditionalConfig,
    { data: additionalConfiguration, isLoading, isFetching },
  ] = useLazyGetAdditionalConfigQuery();

  const getCache = useCallback(
    (inputName: string, inputValue: string) => {
      return configurationCache.find((configCache) => {
        return (
          configCache.nodeId === nodeId &&
          configCache.functionName === functionName &&
          configCache.inputName === inputName &&
          configCache.inputValue === inputValue
        );
      });
    },
    [configurationCache, functionName, nodeId]
  );

  const cacheExists = useCallback((inputName: string, inputValue: string) => Boolean(getCache(inputName, inputValue)), [
    getCache,
  ]);

  const fetchAdditionalConfig = useCallback(
    ({ configurations, values }: { configurations: SkullConfig; values: NodeKeyValue }) => {
      const additionalConfig = configurations?.configuration.find((config) => config.hasAdditionalConfig);
      values = getAdditionalConfigValues(additionalConfig, values);
      if (
        !values ||
        !additionalConfig?.name ||
        isLoading ||
        isFetching ||
        !values?.[additionalConfig.name] ||
        cacheExists(additionalConfig.name, values[additionalConfig.name])
      ) {
        return;
      }
      getAdditionalConfig(
        {
          currentNodeId: nodeId,
          configName: additionalConfig.name,
          graph: currentGraph,
          currentConfiguration: values,
          additionalConfigParams: additionalConfig?.additionalConfigParams,
        },
        true
      );
    },
    [cacheExists, currentGraph, getAdditionalConfig, isFetching, isLoading, nodeId]
  );

  const insertAdditionalConfig = useCallback(
    (configurations: SkullInput[], values: NodeKeyValue) => {
      const newConfigurations: SkullInput[] = [];
      configurations?.forEach((configuration) => {
        values = getAdditionalConfigValues(configuration, values);
        newConfigurations.push(configuration);
        if (
          configuration.hasAdditionalConfig &&
          values[configuration.name] &&
          cacheExists(configuration.name, values[configuration.name])
        ) {
          const cache = getCache(configuration.name, values[configuration.name]);
          cache?.configurations.forEach((configuration) => {
            newConfigurations.push(configuration);
          });
        }
      });
      return newConfigurations;
    },
    [cacheExists, getCache]
  );

  const insertAdditionalConfigInFunctions = useCallback(
    (pipelineFunctions: PipelineFunction[], values: NodeKeyValue) => {
      if (!values || !Object.keys(values).length || !configurationCache.length) {
        return pipelineFunctions;
      }
      const functions = pipelineFunctions.map((pipelineFunc) => {
        if (pipelineFunc.name === functionName) {
          return {
            ...pipelineFunc,
            configuration: insertAdditionalConfig(pipelineFunc.configuration as SkullInput[], values),
          };
        }
        return pipelineFunc;
      });
      return functions;
    },
    [configurationCache, functionName, insertAdditionalConfig]
  );

  // Save the additional config in our local cache if it doesn't exists yet
  useEffect(() => {
    if (
      additionalConfiguration?.configName &&
      additionalConfiguration.currentConfiguration?.[additionalConfiguration.configName] &&
      additionalConfiguration.additionalConfigs &&
      !cacheExists(
        additionalConfiguration.configName,
        additionalConfiguration.currentConfiguration?.[additionalConfiguration.configName]
      )
    ) {
      setConfigurationCache([
        ...configurationCache,
        {
          nodeId,
          functionName,
          inputName: additionalConfiguration.configName,
          inputValue: additionalConfiguration.currentConfiguration?.[additionalConfiguration.configName],
          configurations: additionalConfiguration.additionalConfigs,
        },
      ]);
    }
  }, [additionalConfiguration, cacheExists, configurationCache, functionName, isFetching, isLoading, nodeId]);

  return {
    fetchAdditionalConfig,
    additionalConfig: configurationCache,
    insertAdditionalConfig,
    insertAdditionalConfigInFunctions,
  };
};
