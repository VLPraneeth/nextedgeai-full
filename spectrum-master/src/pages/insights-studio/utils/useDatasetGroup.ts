import ObjectID from 'bson-objectid';
import { useCallback, useMemo } from 'react';

import { PipelinePickerValue } from 'components/pipeline-picker/PipelinePicker';
import { useGetDatasetFunctionsQuery } from 'store/insights-studio';
import { DatasetFields, DatasetFunction, Group } from 'store/insights-studio/types';

import { useUnifiedDataCardAuthoringContext } from '../context/UnifiedDataCardAuthoringContext';
import { CalculatedField } from '../dataset/configuration/sections/CalculatedFields.types';
import { flatDataSourceFields } from './UnifiedDataCard.util';
import { useDatasetConfig } from './useDatasetConfig';

export const useDatasetGroup = () => {
  const { calculatedFields, setGroupBy, setGroup, selectedDataSourceFields } = useUnifiedDataCardAuthoringContext();
  const { data: functions } = useGetDatasetFunctionsQuery();
  const { dataSourceFields } = useDatasetConfig();

  const syncGroups = useCallback(
    (newCalculatedFields?: CalculatedField[], newSelectedDataSourceFields?: PipelinePickerValue) => {
      if (!functions) {
        return;
      }
      const groups: Group[] = [];
      const localCalculatedFields = newCalculatedFields ? newCalculatedFields : calculatedFields;
      const localSelectedDataSourceFields = newSelectedDataSourceFields
        ? newSelectedDataSourceFields
        : selectedDataSourceFields;

      // Automatically add the fields in the calculated field to the groups
      localCalculatedFields?.forEach((calcField) => {
        // Do not add aggregate functions
        if (isAggregateFunction(calcField.aggFunctions, functions)) {
          return;
        }
        // Note: Remove alwaysAddField when the backend start accepting agg function in the group
        // Always adding field for now
        const alwaysAddField = false;
        if (!alwaysAddField && !isAggregateFunction(calcField.aggFunctions, functions)) {
          const datasetField: DatasetFields = {
            fieldId: calcField.aliasName || '',
            apiName: calcField.aliasName,
            displayName: calcField.aliasName,
            datasetId: '',
            datasetType: 'DATASET',
            type: 'variable',
          };
          if (!groupFieldAlreadyExists(datasetField, groups)) {
            groups.push({
              groupId: ObjectID.generate(),
              datasetField,
            });
          }
        } else {
          calcField.datasetFields.forEach((field) => {
            if (field.datasetType === 'LITERAL') {
              return;
            }
            // @ts-ignore
            if (field && !groupFieldAlreadyExists(field, groups)) {
              groups.push({
                groupId: ObjectID.generate(),
                datasetField: {
                  fieldId: field.fieldId || '',
                  apiName: field.apiName,
                  displayName: field.displayName,
                  datasetId: field.datasetId || '',
                  dataType: field.dataType,
                  datasetType: field.datasetType || 'ENTITY',
                  type: 'variable',
                },
              });
            }
          });
        }
      });

      // Automatically add the selected fields in the groups
      if (localSelectedDataSourceFields?.entities) {
        const fields = flatDataSourceFields(dataSourceFields);
        localSelectedDataSourceFields.entities.forEach((entity) => {
          entity.fields?.forEach((field) => {
            const datasetField = fields.find(
              (datasetField) =>
                datasetField.fieldId === field.id && datasetField.datasourceAlias === field.datasourceAlias
            );
            if (datasetField) {
              if (!groupFieldAlreadyExists(datasetField, groups)) {
                groups.push({
                  groupId: ObjectID.generate(),
                  datasetField,
                });
              }
            }
          });
        });
      }

      setGroupBy(groups);

      // Always enable group when we the user added both aggregated and non aggregated functions
      if (functions?.length && hasAggAndNonAggFunctions(localCalculatedFields, functions)) {
        setGroup(true);
      }
    },
    [calculatedFields, dataSourceFields, functions, selectedDataSourceFields, setGroup, setGroupBy]
  );

  const groupRequired = useMemo(() => {
    if (functions?.length) {
      return hasAggAndNonAggFunctions(calculatedFields, functions);
    }
    return false;
  }, [calculatedFields, functions]);

  return {
    syncGroups,
    groupRequired,
  };
};

const hasAggAndNonAggFunctions = (calculatedFields: CalculatedField[], functions: DatasetFunction[]) => {
  let hasAgg = false;
  let hasNonAgg = false;
  calculatedFields.forEach((calcField) => {
    if (isAggregateFunction(calcField.aggFunctions, functions)) {
      hasAgg = true;
    } else {
      hasNonAgg = true;
    }
  });
  return hasAgg && hasNonAgg;
};

// Check if a given function is an aggregate or not
const isAggregateFunction = (name: string, functions: DatasetFunction[]) => {
  return functions.some((func) => func.name === name && func.aggregate);
};

const groupFieldAlreadyExists = (datasetField: DatasetFields, groups: Group[]) => {
  return Boolean(findFieldGroup(datasetField, groups));
};

const findFieldGroup = (datasetField: DatasetFields, groups: Group[]) => {
  return groups.find(
    (group) =>
      group.datasetField?.apiName === datasetField.apiName &&
      group.datasetField?.datasetId === datasetField.datasetId &&
      group.datasetField?.datasourceAlias === datasetField.datasourceAlias
  );
};
