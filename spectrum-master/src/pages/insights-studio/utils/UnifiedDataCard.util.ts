import ObjectID from 'bson-objectid';
import { cloneDeep, groupBy as lodashGroupBy } from 'lodash';

import { ConditionValue, FilterValue } from 'components/inputs/types';
import { PipelinePickerValue } from 'components/pipeline-picker/PipelinePicker';
import { PipelinePickerEntity, PipelinePickerEntityField } from 'components/pipeline-picker/PipelinePicker.types';
import { FieldDataType } from 'components/types';
import { displayFormatter } from 'components/vizer/utils/VizerDisplayFormatter';
import {
  DataSource,
  DataSourceFields,
  DatasetFields,
  DatasetRecord,
  DatasetSort,
  DatasetVariable,
  Group,
  Joins,
  VariableValue,
} from 'store/insights-studio/types';

import { VizConfigColumnOption } from '../components/data-card-wizard/configuration-step/DataCardConfigStep';
import { CalculatedField } from '../dataset/configuration/sections/CalculatedFields.types';

export const ID_ALIAS_DELIMITER = '--__--';

export const toDataCardColumnOptions = (
  dataSourceFields?: PipelinePickerValue,
  calculatedFields?: CalculatedField[]
) => {
  let columns: VizConfigColumnOption[] = [];

  dataSourceFields?.entities.forEach((entity) => {
    entity.fields?.forEach((field) => {
      columns.push({
        label: field.fieldAlias || `${field.displayName} (${field.apiName})`,
        value: field.fieldAlias || field.displayName,
        dataType: field.dataType,
      });
    });
  });

  calculatedFields?.forEach((field) => {
    columns.push({
      label: field.aliasName,
      value: field.aliasName,
      dataType: field.dataType,
    });
  });
  return columns;
};

export const makeDatasetResult = (datasetRecords: DatasetRecord) => {
  const columns = datasetRecords.columns.map((col) => {
    return {
      headerName: col.displayName,
      field: col.displayName,
      headerTooltip: col.apiName,
      resizable: true,
    };
  });

  const data = datasetRecords.data.map((sample) => {
    return sample.reduce((record: Record<string, string>, { value, columnDisplayName }) => {
      record[columnDisplayName] = value;
      return record;
    }, {});
  });

  return { data, columns };
};

// Remove any join with a field that is no longer in the selected datasources
export const removeInvalidJoin = (blendedData?: Joins[], selectedDataSources?: DataSource[]) => {
  if (!selectedDataSources?.length || selectedDataSources.length < 2) {
    return [];
  }

  const result =
    blendedData?.filter(
      (blendedDatum) =>
        blendedDatum?.field1?.datasetId &&
        selectedDataSources.find(
          (ds) =>
            blendedDatum?.field1?.datasetId === splitIdAndAlias(ds.datasetId).id &&
            blendedDatum?.field1?.datasourceAlias === ds.alias
        ) &&
        blendedDatum?.field2?.datasetId &&
        selectedDataSources.find(
          (ds) =>
            blendedDatum?.field2?.datasetId === splitIdAndAlias(ds.datasetId).id &&
            blendedDatum?.field2?.datasourceAlias === ds.alias
        )
    ) || [];
  return result;
};

// Flatten the data source fields
export const flatDataSourceFields = (dataSourceFields?: Record<string, DataSourceFields[]>) => {
  if (!dataSourceFields) {
    return [];
  }
  return Object.values(dataSourceFields).flatMap((arr) => arr);
};

// Create an ready empty group input
export const makeInitialGroupsValue = () => [{ groupId: ObjectID.generate(), dateGroupByOption: '' }];

// Remove an empty group input
export const removeBlankGroupsValue = (groups?: Group[]) => {
  return (
    groups?.filter((group) => {
      return Boolean(group.datasetField);
    }) || []
  );
};

// Create an ready empty sort input
export const makeInitialSortsValue = () => [{ sortId: ObjectID.generate() }];

// Remove an empty sort input
export const removeBlankSortsValue = (sorts?: DatasetSort[]) => {
  return (
    sorts?.filter((sort) => {
      return Boolean(sort.field);
    }) || []
  );
};

// Transform UI to server variable map
export const toVariableMap = (variables?: DatasetVariable[]) => {
  const variableMap: Record<string, DatasetVariable> = {};
  variables?.forEach((variable) => {
    if (variable.apiName) {
      variableMap[variable.apiName] = variable;
    }
  });
  return variableMap;
};

// Transform server variable map to UI
export const fromVariableMap = (variableMap?: Record<string, DatasetVariable>) =>
  variableMap ? Object.values(variableMap) : [];

// Transform UI to server selected fields
export const toSelectedFields = (
  selectedDataSourceFields: PipelinePickerValue | undefined,
  dataSources: DataSource[] | undefined
) => {
  const selectedFields: DatasetFields[] = [];
  selectedDataSourceFields?.entities.forEach((entity) => {
    const dId = splitIdAndAlias(entity.id).id;
    const foundDataSource = dataSources?.find((dataSource) => dId === dataSource.datasetId);
    entity.fields.forEach((field) => {
      selectedFields.push({
        apiName: field.apiName,
        dataType: field.dataType,
        datasetId: dId,
        displayName: field.displayName,
        fieldId: field.id,
        alias: field.fieldAlias,
        type: 'variable',
        datasetType: foundDataSource?.datasetType || 'ENTITY',
        datasourceAlias: field.datasourceAlias,
      });
    });
  });
  return selectedFields;
};

// Transform server selected fields to UI
export const fromSelectedFields = (
  selectedFields: DatasetFields[] | undefined,
  dataSources: DataSource[] | undefined,
  dataSourceFields: DataSourceFields[] | undefined
) => {
  const entities: PipelinePickerEntity[] = [];
  const selectedDataSourceFields: PipelinePickerValue = { entities };

  if (!dataSources) {
    return;
  }

  const entityGroups = lodashGroupBy(selectedFields, 'datasetId');

  Object.keys(entityGroups).forEach((datasetId) => {
    // Look for the entity
    const dataSource = dataSources?.find((dataSource) => dataSource.datasetId === splitIdAndAlias(datasetId).id);
    if (dataSource) {
      const datasetFields = entityGroups[datasetId];

      const fields: PipelinePickerEntityField[] = [];
      datasetFields?.forEach(({ apiName, datasetId, alias, datasourceAlias }) => {
        const field = lookupByDatasetIdAndApiNameAndAlias(datasetId, apiName, dataSourceFields, datasourceAlias);
        if (field) {
          const { fieldId: id, apiName, displayName, dataType, alias: fieldAlias, datasourceAlias } = field;
          fields.push({
            id,
            apiName,
            displayName: displayName || '',
            dataType: dataType as FieldDataType,
            fieldAlias: alias || fieldAlias,
            datasourceAlias,
          });
        }
      });

      entities.push({
        id: datasetId,
        apiName: dataSource.apiName,
        displayName: dataSource.displayName || '',
        fields,
      });
    }
  });
  return selectedDataSourceFields;
};

// Transform UI to server selected datasources
export const toSelectedDataSources = (selectedDataSources: DataSource[]) => {
  const fromDataset: DataSource[] = [];

  selectedDataSources.forEach((dataSource) => {
    const dId = splitIdAndAlias(dataSource.datasetId).id;
    if (dataSource) {
      fromDataset.push({
        ...dataSource,
        apiName: dataSource.apiName,
        datasetId: dId,
      });
    }
  });
  return { fromDataset };
};

// Transform server selected fields to UI
export const fromSelectedDataSources = (dataSources?: DataSource[]) => {
  const selectedDataSources: DataSource[] = [];
  dataSources?.forEach((dataSource) => {
    selectedDataSources.push(dataSource);
  });
  return appendDatasetIdSuffixForDataSource(selectedDataSources);
};

export function appendDatasetIdSuffixForDataSource(datasources: DataSource[]) {
  const datasetIdCountsMap: Record<string, number> = {};

  return datasources.map((item) => {
    const datasetId = item.datasetId;

    if (!datasetIdCountsMap[datasetId]) {
      datasetIdCountsMap[datasetId] = 1;
    } else {
      datasetIdCountsMap[datasetId]++;
    }

    const suffix = datasetIdCountsMap[datasetId];
    const newDatasetId = createIdWithAlias(datasetId, `${suffix}`);

    return {
      ...item,
      datasetId: newDatasetId,
    };
  });
}

export function appendDatasetIdSuffixForFields(fields: DatasetFields[]) {
  const datasetFields: DatasetFields[] = [];

  const datasetIdCountsMap: Record<string, number> = {};

  const datasetIdFieldsMap: Record<string, DatasetFields[]> = lodashGroupBy(fields, (d) =>
    createIdWithAlias(d.datasetId, d.datasourceAlias)
  );

  Object.keys(datasetIdFieldsMap).forEach((key) => {
    const datasetId = splitIdAndAlias(key).id;

    if (!datasetIdCountsMap[datasetId]) {
      datasetIdCountsMap[datasetId] = 1;
    } else {
      datasetIdCountsMap[datasetId]++;
    }

    const suffix = datasetIdCountsMap[datasetId];
    const newDatasetId = createIdWithAlias(datasetId, `${suffix}`);

    datasetFields.push(
      ...datasetIdFieldsMap[key].map((field) => ({
        ...field,
        datasetId: newDatasetId,
      }))
    );
  });

  return datasetFields;
}

export function createIdWithAlias(id: string = '', datasourceAlias?: string) {
  return datasourceAlias ? id + ID_ALIAS_DELIMITER + datasourceAlias : id;
}

export function splitIdAndAlias(id: string = '') {
  return {
    id: id.split(ID_ALIAS_DELIMITER)[0] || '',
    datasourceAlias: id.split(ID_ALIAS_DELIMITER)[1] || '',
  };
}

export const lookupByDatasetIdAndApiNameAndAlias = (
  datasetId: string,
  apiName: string,
  dataSourceFields: DataSourceFields[] | undefined,
  datasourceAlias: string | undefined
) => {
  const foundDataSourceField = dataSourceFields?.find(
    (dataSourceField) =>
      dataSourceField.apiName === apiName &&
      dataSourceField.datasetId === splitIdAndAlias(datasetId).id &&
      (datasourceAlias ? dataSourceField.datasourceAlias === datasourceAlias : true)
  );
  let field: DatasetFields | undefined = foundDataSourceField
    ? {
        datasetId: foundDataSourceField.datasetId,
        fieldId: foundDataSourceField.fieldId,
        apiName: foundDataSourceField.apiName,
        displayName: foundDataSourceField.displayName,
        dataType: foundDataSourceField.dataType,
        datasetType: foundDataSourceField.datasetType,
        type: foundDataSourceField.type,
        datasourceAlias: foundDataSourceField.datasourceAlias,
      }
    : undefined;
  return field;
};

// Fill in the join id for complete joins
export const fillJoinId = (joins: Joins[]) => {
  return joins.map((join) => {
    // Only add the jionId if its a complete join
    if (join.field1?.apiName && join.field2?.apiName && !join.joinId) {
      return { ...join, joinId: makeJoinId(join) };
    }
    return join;
  });
};

// Generate a join id for complete join
const makeJoinId = (join: Joins) => {
  return `${join.field1?.datasetId}-${join.field1?.apiName}-${join.field2?.datasetId}-${join.field2?.apiName}`;
};

// Check if there are no selected fields
export const isSelectedDataSourceFieldsEmpty = (selDsFields?: PipelinePickerValue) => {
  if (!selDsFields || selDsFields.entities.length <= 0) {
    return true;
  }

  return !selDsFields.entities?.some((entity) => {
    return Boolean(entity.fields.length);
  });
};

export function removeDataSourceFields(
  selectedDataSourceFields: PipelinePickerValue,
  newSelectedDataSources: DataSource[]
) {
  const updatedFields = selectedDataSourceFields.entities.filter((entity) => {
    return newSelectedDataSources.find((ds) => ds.datasetId === entity.id);
  });

  return { entities: updatedFields };
}

export function removeFiltersWithRemovedEntityFields(
  filter: FilterValue | undefined,
  newSelectedDataSources: DataSource[]
) {
  if (!filter) {
    return filter;
  }

  const newFilters = removePredsFromFilter(filter, newSelectedDataSources);

  if (newFilters.predicates.length === 0) {
    return;
  }

  return newFilters;
}

function removePredsFromFilter(filter: FilterValue, dataSources: DataSource[]): FilterValue {
  return {
    ...filter,
    predicates: filter.predicates.reduce((acc, pred) => {
      if ('left' in pred) {
        // This is a ConditionValue
        if (
          dataSources.find((ds) => {
            return (
              // @ts-expect-error DatasetFields is not compatible with LeftValue type
              // TODO: Fix these types. May require a refactor of Condition components to account for type changes
              splitIdAndAlias(ds.datasetId).id === pred.left?.datasetId &&
              (ds.alias ? ds.alias === pred.left.datasourceAlias : true)
            );
          })
        ) {
          acc.push(pred);
        }
      } else if ('predicates' in pred) {
        // This is a FilterValue
        let newPred = removePredsFromFilter(pred, dataSources);

        if (newPred.predicates.length === 0) {
          // don't include any filter values that have no predicates left
          return acc;
        }

        if (newPred.predicates.length === 1 && 'left' in newPred.predicates[0]) {
          // If only one ConditionValue remains in the group, just return the condition and not the group
          acc.push(newPred.predicates[0]);
        } else {
          acc.push(newPred);
        }
      }

      return acc;
    }, [] as (FilterValue | ConditionValue)[]),
  };
}

export const compileVizerLabel = (label: string, variable?: Record<string, VariableValue>) => {
  if (!variable) {
    return label;
  }
  return Object.entries(variable).reduce((newStr, [key, value]) => {
    const formattedValue =
      value.datatype === 'double' || value.datatype === 'integer'
        ? displayFormatter.number(value.defaultValue)
        : value?.defaultValue;
    return newStr.replace(`{{${key}}}`, formattedValue?.toString() || '');
  }, label);
};

export const makeStringToken = (str?: string) => (str ? `{{${str}}}` : '');

export function updateCalculatedFields(
  calculatedFields: CalculatedField[],
  datasetId: string | undefined,
  oldAlias: string | undefined
) {
  return calculatedFields
    .map((field) => {
      const updatedFields = field.datasetFields.filter((datasetField) => {
        return datasetField.datasetId === splitIdAndAlias(datasetId).id && datasetField.datasourceAlias !== oldAlias;
      });

      return {
        ...field,
        datasetFields: updatedFields,
      };
    })
    .filter((field) => field.datasetFields.length);
}

export function updateGroup(
  groupBy: Group[] | undefined,
  datasetId: string | undefined,
  oldAlias: string | undefined,
  newAlias: string | undefined
) {
  const updatedGroup = cloneDeep(groupBy || []);

  for (let i = 0; i < updatedGroup.length; i++) {
    const groupItem = updatedGroup[i];

    if (
      groupItem.datasetField &&
      groupItem.datasetField.datasetId === datasetId &&
      groupItem.datasetField.datasourceAlias === oldAlias
    ) {
      groupItem.datasetField.datasourceAlias = newAlias;
    }
  }

  return updatedGroup;
}

export function updateSort(
  sort: DatasetSort[] | undefined,
  datasetId: string | undefined,
  oldAlias: string | undefined,
  newAlias: string | undefined
) {
  const updatedSort = cloneDeep(sort || []);

  for (let i = 0; i < updatedSort.length; i++) {
    const sortItem = updatedSort[i];

    if (sortItem.field && sortItem.field.datasetId === datasetId && sortItem.field.datasourceAlias === oldAlias) {
      sortItem.field.datasourceAlias = newAlias;
    }
  }

  return updatedSort;
}

export function updateBlendData(
  blendedData: Joins[] | undefined,
  datasetId: string | undefined,
  oldAlias: string | undefined,
  newAlias: string | undefined
) {
  const updatedBlendData = cloneDeep(blendedData || []);

  for (let i = 0; i < updatedBlendData.length; i++) {
    const blendObject = updatedBlendData[i];

    if (
      blendObject.field1 &&
      blendObject.field1.datasetId === datasetId &&
      blendObject.field1.datasourceAlias === oldAlias
    ) {
      blendObject.field1.datasourceAlias = newAlias;
    }

    if (
      blendObject.field2 &&
      blendObject.field2.datasetId === datasetId &&
      blendObject.field2.datasourceAlias === oldAlias
    ) {
      blendObject.field2.datasourceAlias = newAlias;
    }
  }
  return updatedBlendData;
}

export function updateFilter(
  datasetId: string | undefined,
  datasourceAliasOld: string | undefined,
  datasourceAliasNew: string | undefined,
  filter?: FilterValue | undefined
) {
  if (!filter) {
    return filter;
  }
  const updatedFilter = cloneDeep(filter);

  function updateDatasourceAlias(obj: any) {
    if (Array.isArray(obj)) {
      for (let i = 0; i < obj.length; i++) {
        updateDatasourceAlias(obj[i]);
      }
    } else if (typeof obj === 'object' && obj !== null) {
      if (obj.datasourceAlias && obj.datasourceAlias === datasourceAliasOld && obj.datasetId === datasetId) {
        obj.datasourceAlias = datasourceAliasNew;
      }
      for (const key in obj) {
        if (typeof obj[key] === 'object' && obj[key] !== null) {
          updateDatasourceAlias(obj[key]);
        }
      }
    }
  }

  updateDatasourceAlias(updatedFilter);

  return updatedFilter;
}
