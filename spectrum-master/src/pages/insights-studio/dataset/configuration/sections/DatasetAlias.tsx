import { message } from 'antd';

import { HStack } from 'components/layout';
import {
  splitIdAndAlias,
  updateBlendData,
  updateCalculatedFields,
  updateFilter,
  updateGroup,
  updateSort,
} from 'pages/insights-studio/utils/UnifiedDataCard.util';
import { useUnifiedDataCardAuthoring } from 'pages/insights-studio/utils/useUnifiedDataCardAuthoring';
import { tNamespaced } from 'utils/i18nUtil';

import { DatasetAliasInput } from '../DatasetAliasInput';

const tn = tNamespaced('InsightsStudio');

export function DatasetAlias() {
  const {
    selectedDataSources,
    setBlendedData,
    setFilter,
    setSelectedDataSources,
    setSort,
    setGroupBy,
    setCalculatedFields,
    blendedData,
    sort,
    filter,
    groupBy,
    calculatedFields,
  } = useUnifiedDataCardAuthoring();

  function updateSelectedDatasetAlias(datasetId?: string, oldAlias?: string, newAlias?: string) {
    if (oldAlias !== newAlias && selectedDataSources.find((ds) => ds.alias === newAlias)) {
      return message.error(tn('duplicate_alias'));
    }
    setSelectedDataSources(
      selectedDataSources?.map((source) => {
        if (source.datasetId === datasetId) {
          return {
            ...source,
            alias: newAlias,
          };
        }
        return source;
      })
    );

    const _datasetId = splitIdAndAlias(datasetId).id;
    const updatedBlendData = updateBlendData(blendedData, _datasetId, oldAlias, newAlias);
    const updatedSort = updateSort(sort, _datasetId, oldAlias, newAlias);
    const updatedGroup = updateGroup(groupBy, _datasetId, oldAlias, newAlias);
    const updatedCalculatedFields = updateCalculatedFields(calculatedFields, _datasetId, oldAlias);
    const updatedFilter = updateFilter(_datasetId, oldAlias, newAlias, filter);

    setCalculatedFields(updatedCalculatedFields);
    setFilter(updatedFilter);
    setSort(updatedSort);
    setGroupBy(updatedGroup);
    setBlendedData(updatedBlendData);
  }

  return (
    <div>
      {selectedDataSources?.map((dataSource) => {
        return (
          <HStack className="data-source-picker__alias">
            <div>
              {dataSource.displayName}{' '}
              <span className="data-source-picker__alias-api_name">({dataSource.apiName})</span>
            </div>

            <DatasetAliasInput
              datasetId={dataSource.datasetId}
              alias={dataSource.alias}
              updateSelectedItem={updateSelectedDatasetAlias}
            />
          </HStack>
        );
      })}
    </div>
  );
}
