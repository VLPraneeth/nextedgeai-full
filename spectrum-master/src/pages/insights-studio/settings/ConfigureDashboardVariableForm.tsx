//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { useEffect, useState } from 'react';

import { useI18nContext, withI18n } from 'components/I18nProvider';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { Stack } from 'components/layout';
import { EMPTY_ARRAY } from 'store/constants';
import { useGetDashboardVariableQuery } from 'store/insights-studio';
import { DataCardSettingsValue, VariableValue, DatasetVariable } from 'store/insights-studio/types';

import DatasetVariableValue from '../dataset/variable/DatasetVariableValue';
import { useDashBoardDataCards } from './DashboardVariable.hooks';
import { isVariableVisible } from './DashboardVariable.utils';
import { useDataCardSettingsContext } from './DataCardSettingsContext';
import { NoDashboardVariables } from './NoDashboardVariables';
import { ResetButton } from './ResetButton';

const ConfigureDashboardVariableForm = () => {
  const {
    setCurrentValue,
    currentValue,
    mergeCurrentValue,
    configureDashboardVariableOptions,
    dashboardSettingsVisible,
  } = useDataCardSettingsContext();
  const { tn } = useI18nContext();

  const { dataCardsWithData } = useDashBoardDataCards();

  const dashboardId = configureDashboardVariableOptions?.dashboardId;
  const { data: variableMappings, refetch } = useGetDashboardVariableQuery(
    { dashboardId: dashboardId || '' },
    { skip: !Boolean(dashboardId) }
  );

  useEffect(() => {
    if (dashboardSettingsVisible) {
      refetch();
    }
    setCurrentValue({});
  }, [dashboardSettingsVisible, refetch, setCurrentValue]);

  // Datacards with user preference and data
  const [dashboardVariables, setDashboardVariables] = useState<DatasetVariable[]>([]);

  // Build a list of variables across all datacards
  useEffect(() => {
    const variables: Record<string, DatasetVariable> = {};

    // Build our list of variable
    dataCardsWithData.forEach((dataCard) => {
      const variableMap = dataCard?.contents?.configuration?.variablesMap;
      const userVariableConfiguration = dataCard?.configuration;
      if (variableMap) {
        Object.keys(variableMap).forEach((apiName) => {
          if (!isVariableVisible(apiName, dataCard?.configurationMeta)) {
            return;
          }

          // Use the user value if available
          const variableValue = userVariableConfiguration?.[apiName]
            ? userVariableConfiguration[apiName]
            : variableMap[apiName].variableDefaultValue;

          if (
            variables[apiName] &&
            variables[apiName].variableDefaultValue.defaultValue !== variableValue.defaultValue
          ) {
            // Empty the default value if they don't have the same value
            variables[apiName] = {
              ...variableMap[apiName],
              variableDefaultValue: { ...variableValue, defaultValue: '' },
            };
          } else {
            variables[apiName] = {
              ...variableMap[apiName],
              variableDefaultValue: variableValue,
            };
          }
        });
      }
    });

    const configurableDashboardVariable: DatasetVariable[] = [];
    let processedVariables: string[] = [];
    // Apply the mapping if any
    if (variableMappings) {
      variableMappings.forEach((variableMap) => {
        if (!variableMap.apiName || processedVariables.includes(variableMap.apiName)) {
          return;
        }
        processedVariables.push(variableMap.apiName);
        if (variableMap.mappedApiNames) {
          processedVariables = [...processedVariables, ...variableMap.mappedApiNames];
        }
        let sameValues = true;
        [...(variableMap.mappedApiNames || EMPTY_ARRAY)].forEach((apiName) => {
          if (
            variableMap.apiName &&
            variables[variableMap.apiName]?.variableDefaultValue.defaultValue !==
              variables[apiName]?.variableDefaultValue.defaultValue
          ) {
            sameValues = false;
          }
        });

        const updatedDashboardVariable = !sameValues
          ? ({
              ...variables[variableMap.apiName],
              variableDefaultValue: {
                ...variables[variableMap.apiName]?.variableDefaultValue,
                defaultValue: '',
              },
            } as DatasetVariable)
          : variables[variableMap.apiName];
        configurableDashboardVariable.push(updatedDashboardVariable);
      });
    }

    // Add remaining fields that doesn't have mapping
    Object.keys(variables).forEach((apiName) => {
      if (processedVariables.includes(apiName)) {
        return;
      }
      configurableDashboardVariable.push(variables[apiName]);
    });
    setDashboardVariables(configurableDashboardVariable.filter(Boolean));
  }, [dataCardsWithData, variableMappings]);

  // Initialize our currentValue for each distinct variables in the dashboard
  useEffect(() => {
    dashboardVariables.forEach((variable) => {
      if (variable.apiName) {
        const newValues: Record<string, VariableValue> = {};
        newValues[variable.apiName] = variable.variableDefaultValue;
        const variableMap = variableMappings?.find((variableMap) => variableMap.apiName === variable.apiName);
        if (variableMap?.mappedApiNames) {
          variableMap.mappedApiNames.forEach((apiName) => {
            newValues[apiName] = variable.variableDefaultValue;
          });
        }
        setCurrentValue((current) => {
          return { ...current, ...newValues };
        });
      }
    });
  }, [dashboardVariables, setCurrentValue, variableMappings]);

  const makeTooltip = (apiName: string) => {
    const dataCardNames: string[] = [];
    dataCardsWithData.forEach((dataCard) => {
      if (dataCard.configurationMeta?.find((variable) => variable.name === apiName)) {
        dataCardNames.push(dataCard.displayName);
      }
    });
    return tn('used_by_data_cards', { dataCards: dataCardNames.join(', ') });
  };

  if (dashboardVariables && !Object.keys(dashboardVariables)?.length) {
    return <NoDashboardVariables />;
  }

  return (
    <Stack>
      {dashboardVariables &&
        dashboardVariables.map((variable) => {
          return (
            <InputWithLabel
              key={variable.apiName}
              label={variable.displayName}
              tooltip={variable.apiName && makeTooltip(variable.apiName)}
              required
              input={
                <DatasetVariableValue
                  defaultValue={
                    variable.apiName ? currentValue[variable.apiName] ?? variable.variableDefaultValue : undefined
                  }
                  onChange={(value) => {
                    if (variable.apiName) {
                      const newValues: Record<string, VariableValue> = {};
                      newValues[variable.apiName] = value;
                      const variableMap = variableMappings?.find(
                        (variableMap) => variableMap.apiName === variable.apiName
                      );
                      if (variableMap?.mappedApiNames) {
                        variableMap.mappedApiNames.forEach((apiName) => {
                          newValues[apiName] = value;
                        });
                      }
                      setCurrentValue({ ...currentValue, ...newValues });
                    }
                  }}
                />
              }
            />
          );
        })}
      <ResetButton
        onClick={() => {
          const defaultValues: DataCardSettingsValue = {};
          dashboardVariables.forEach((variable) => {
            if (variable.apiName) {
              defaultValues[variable.apiName] = variable.variableDefaultValue;
            }
          });
          mergeCurrentValue({ ...defaultValues });
        }}>
        {tn('reset_all')}
      </ResetButton>
    </Stack>
  );
};

export default withI18n(ConfigureDashboardVariableForm, 'InsightsStudio.Settings');
