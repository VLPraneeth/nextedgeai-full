import { isEqual } from 'lodash';
import { useCallback, useMemo } from 'react';

import InfoBox from 'components/InfoBox';
import {
  PipelinePickerProps,
  PipelinePickerValue,
  TranslatedPipelinePicker,
} from 'components/pipeline-picker/PipelinePicker';
import { PipelinePickerEntity, PipelinePickerEntityField } from 'components/pipeline-picker/PipelinePicker.types';
import { FieldDataType } from 'components/types';
import { createIdWithAlias, splitIdAndAlias } from 'pages/insights-studio/utils/UnifiedDataCard.util';
import { useDatasetConfig } from 'pages/insights-studio/utils/useDatasetConfig';
import { useDatasetGroup } from 'pages/insights-studio/utils/useDatasetGroup';
import { useUnifiedDataCardAuthoring } from 'pages/insights-studio/utils/useUnifiedDataCardAuthoring';

export type DataSourceFieldTreePickerValue = PipelinePickerValue;

export const DataSourceFieldTreePicker = (props: Omit<PipelinePickerProps, 'entities'>) => {
  const { selectedDataSources, setSelectedDataSourceFields, selectedDataSourceFields } = useUnifiedDataCardAuthoring();
  const { dataSourceFields } = useDatasetConfig();
  const { syncGroups } = useDatasetGroup();

  const dataSourceToPipeline: PipelinePickerEntity[] = useMemo(() => {
    const pipelineEntities: PipelinePickerEntity[] = [];

    selectedDataSources.forEach((dataSource) => {
      const datasourceId = splitIdAndAlias(dataSource.datasetId).id;
      const foundSelectedDataSource = selectedDataSourceFields?.entities.find(
        (entity) => entity.id === dataSource.datasetId
      );

      if (dataSource) {
        const key = createIdWithAlias(datasourceId, dataSource.alias);
        const fields: PipelinePickerEntityField[] = dataSourceFields[key]?.map((field) => {
          let fieldAlias = field.alias || '';

          if (foundSelectedDataSource) {
            const foundSelectedField = foundSelectedDataSource.fields?.find(
              (selectedField) => selectedField.id === field.fieldId
            );
            if (foundSelectedField) {
              fieldAlias = foundSelectedField.fieldAlias || fieldAlias;
            }
          }
          return {
            id: field.fieldId,
            apiName: field.apiName,
            displayName: field.displayName || '',
            // TODO: Backend is always returning undefined. Remove the default string when the BE is fixed
            dataType: (field.dataType || 'string') as FieldDataType,
            fieldAlias,
            datasourceAlias: field.datasourceAlias,
          };
        });

        pipelineEntities.push({
          id: dataSource.datasetId,
          apiName: dataSource.apiName,
          displayName: dataSource.alias || dataSource.displayName || '',
          loading: !Boolean(fields),
          fields: fields || [],
        });
      }
    });
    return pipelineEntities;
  }, [dataSourceFields, selectedDataSources, selectedDataSourceFields]);

  const onChange = useCallback(
    (value: DataSourceFieldTreePickerValue) => {
      if (!isEqual(value, selectedDataSourceFields)) {
        setSelectedDataSourceFields(value);
        syncGroups(undefined, value);
      }
    },
    [selectedDataSourceFields, setSelectedDataSourceFields, syncGroups]
  );

  if (selectedDataSources?.length < 1) {
    return <InfoBox message="You need to select a data sources to select fields" type="info" showIcon />;
  }

  return (
    <TranslatedPipelinePicker
      onChange={onChange}
      withFieldAlias
      value={selectedDataSourceFields}
      entities={dataSourceToPipeline}
      hideOpenInNewTab
      hideSelectionSummary
      hideDataTypeFilter
    />
  );
};
