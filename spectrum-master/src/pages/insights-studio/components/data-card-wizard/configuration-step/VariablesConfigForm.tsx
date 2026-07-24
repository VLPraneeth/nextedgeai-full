import { cloneDeep } from 'lodash';
import { useMemo } from 'react';

import { useDataCardAuthoringContext } from 'pages/insights-studio/context/DataCardAuthoringContext';
import { useUnifiedDataCardAuthoring } from 'pages/insights-studio/utils/useUnifiedDataCardAuthoring';
import { useGetVariableQuery } from 'store/insights-studio';
import { DataCardVizConfig, DatasetVariable } from 'store/insights-studio/types';

import { VariableConfigInput } from './VariableConfigInput';

export interface VariablesConfigFormProps {
  setVariables: (variable: DataCardVizConfig['variablesMap']) => void;
  dataCardVariables: DataCardVizConfig['variablesMap'];
}

export const VariablesConfigForm = ({ setVariables, dataCardVariables = {} }: VariablesConfigFormProps) => {
  const { variables: unifiedDatasetVariables, dataCardWithNewDataset } = useUnifiedDataCardAuthoring();
  const { preselectedDatasetId } = useDataCardAuthoringContext();
  const { data: existingDatasetVariables } = useGetVariableQuery({ datasetId: preselectedDatasetId });

  const datasetVariables = useMemo(() => {
    return dataCardWithNewDataset ? unifiedDatasetVariables : existingDatasetVariables;
  }, [dataCardWithNewDataset, existingDatasetVariables, unifiedDatasetVariables]);

  if (!datasetVariables) {
    return null;
  }

  const cardVariableNames = Object.keys(dataCardVariables);

  const updateVariable = (variable: DatasetVariable) => {
    const newVars = cloneDeep(dataCardVariables);
    variable.apiName && (newVars[variable.apiName] = variable);
    setVariables(newVars);
  };

  const removeVariable = (apiName: string) => {
    const newVars = cloneDeep(dataCardVariables);
    delete newVars[apiName];
    setVariables(newVars);
  };

  return (
    <div>
      {datasetVariables.map((variable) => {
        return (
          <VariableConfigInput
            variable={
              typeof variable?.apiName === 'string' ? dataCardVariables?.[variable.apiName] ?? variable : variable
            }
            disabled={Boolean(variable.apiName && !cardVariableNames.includes(variable.apiName))}
            updateVariable={updateVariable}
            removeVariable={removeVariable}
          />
        );
      })}
    </div>
  );
};
