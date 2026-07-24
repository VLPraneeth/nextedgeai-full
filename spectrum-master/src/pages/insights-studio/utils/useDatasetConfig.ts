import { useCallback, useEffect, useState } from 'react';

import { PicklistValue } from 'components/inputs/types';
import {
  useGetDatasetAndEntityInfoQuery,
  useGetDatasetsQuery,
  useLazyGetDataSourceFieldsQuery,
} from 'store/insights-studio';
import { DatasetFields } from 'store/insights-studio/types';
import { createApiName } from 'utils/StringUtil';

import { useInsightsViewContext } from '../context/InsightsViewContext';
import { useUnifiedDataCardAuthoringContext } from '../context/UnifiedDataCardAuthoringContext';
import {
  appendDatasetIdSuffixForFields,
  createIdWithAlias,
  fillJoinId,
  flatDataSourceFields,
  fromSelectedDataSources,
  fromSelectedFields,
  fromVariableMap,
  splitIdAndAlias,
} from './UnifiedDataCard.util';

export const useDatasetConfig = () => {
  const unifiedDataCardAuthoringContext = useUnifiedDataCardAuthoringContext();
  const {
    setBlendedData,
    selectedDataSources,
    dsFields: dataSourceFields,
    setDsFields: setDataSourceFields,
  } = unifiedDataCardAuthoringContext;

  const [dataSourcePicklistValues, setDataSourcePicklistValues] = useState<PicklistValue[]>([]);
  const { isThoughtSpotView } = useInsightsViewContext();
  const { data: dataSources } = useGetDatasetAndEntityInfoQuery({
    isThoughtspot: isThoughtSpotView,
    withEntityInfo: true,
  });
  const { data: datasets } = useGetDatasetsQuery(isThoughtSpotView);

  // Transform datasource to datasource picker format
  useEffect(() => {
    if (dataSources) {
      setDataSourcePicklistValues(
        dataSources.map(({ displayName, apiName, datasetId, datasetType }) => {
          return {
            value: datasetId,
            label: displayName || '',
            apiName,
            picklistGroup: datasetType === 'ENTITY' ? 'Entities' : 'Data sets',
          };
        })
      );
    }
  }, [dataSources]);

  // Data source fields related operations
  const [fetchFields, { data: dataSourceFieldsResp }] = useLazyGetDataSourceFieldsQuery();

  useEffect(() => {
    if (dataSourceFieldsResp) {
      setDataSourceFields((current) => {
        // All fields have the same dataset id. Using the first field is safe.
        const dataSourceId = dataSourceFieldsResp?.dataSourceFields?.[0]?.datasetId;
        const dataSourceAlias = dataSourceFieldsResp?.dataSourceAlias;
        const key = createIdWithAlias(dataSourceId, dataSourceAlias);
        if (dataSourceId && !current[key]) {
          return {
            ...current,
            [key]: dataSourceFieldsResp.dataSourceFields,
          };
        }
        return {
          ...current,
        };
      });
    }
  }, [dataSourceFieldsResp, setDataSourceFields]);

  // Fetch needed fields for the new seleted data source
  useEffect(() => {
    selectedDataSources.forEach((dataSource) => {
      const dataSourceId = splitIdAndAlias(dataSource.datasetId).id;
      const datasetAlias = dataSource.alias;
      const key = createIdWithAlias(dataSourceId, datasetAlias);

      if (dataSource) {
        // Fetch fields
        if (!dataSourceFields[key]) {
          fetchFields({
            dataSourceId,
            dataSourceType: dataSource.datasetType,
            alias: datasetAlias || '',
          });
        }
      }
    });
  }, [dataSourceFields, dataSources, fetchFields, selectedDataSources]);

  const makeVariableApiName = useCallback((displayName: string) => {
    // TODO: check for duplicates in the variable list
    return createApiName(displayName);
  }, []);

  // Only used when loading a dataset
  const [selectedFields, setSelectedFields] = useState<DatasetFields[] | undefined>();
  // We're loading a dataset, initialize our field list
  useEffect(() => {
    const { datasetId } = unifiedDataCardAuthoringContext;
    if (dataSourceFieldsResp?.dataSourceFields && datasetId && dataSources) {
      const { setSelectedDataSourceFields } = unifiedDataCardAuthoringContext;
      const fields = [...dataSourceFieldsResp.dataSourceFields, ...flatDataSourceFields(dataSourceFields)];

      const pipelineSelectedFields = fromSelectedFields(selectedFields, dataSources, fields);
      const key = createIdWithAlias(
        dataSourceFieldsResp.dataSourceFields?.[0].datasetId,
        dataSourceFieldsResp.dataSourceAlias
      );
      setDataSourceFields((current) => {
        return {
          ...current,
          [key]: dataSourceFieldsResp.dataSourceFields,
        };
      });

      pipelineSelectedFields && setSelectedDataSourceFields(pipelineSelectedFields);
    }
    //  We only need this when were loading a dataset. Ignore other changes
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [dataSourceFieldsResp, dataSources]);

  const loadDataset = useCallback(
    (datasetId: string) => {
      const dataset = datasets?.find((dataset) => dataset.id === datasetId);
      if (dataset) {
        const {
          id: datasetId,
          displayName,
          name: apiName,
          description,
          tags,
          datasetConfig,
          variablesMap,
          sql,
        } = dataset;
        if (!datasetConfig) {
          return;
        }
        const {
          fromDataset,
          selectedFields,
          joins,
          group,
          groupBy,
          filter,
          sort,
          calculatedFields,
          limit,
          configMode,
        } = datasetConfig;
        const {
          setDatasetId,
          setDisplayName,
          setApiName,
          setDescription,
          setTags,
          setSelectedDataSources,
          setGroup,
          setGroupBy,
          setFilter,
          setSort,
          setCalculatedFields,
          setLimit,
          setVariables,
          dsFields,
          setSelectedDataSourceFields,
          setConfigMode,
          setSql,
        } = unifiedDataCardAuthoringContext;
        setDatasetId(datasetId);
        setDisplayName(displayName);
        setApiName(apiName);
        setDescription(description);
        setTags(tags || []);
        setSelectedDataSources(fromSelectedDataSources(fromDataset));
        setBlendedData(fillJoinId(joins || []));
        setGroup(group);
        setGroupBy(groupBy);
        setFilter(filter);
        setSort(sort);
        setCalculatedFields(calculatedFields || []);
        setLimit(limit || '');
        setConfigMode(configMode || 'BASIC');
        setSql(sql || '');
        setVariables(variablesMap ? fromVariableMap(variablesMap) : []);
        if (selectedFields) {
          const _selectedFields = appendDatasetIdSuffixForFields(selectedFields || []);
          setSelectedFields(_selectedFields);
          const fromFields = fromSelectedFields(
            _selectedFields,
            dataSources,
            flatDataSourceFields({ ...dsFields, ...dataSourceFields })
          );
          fromFields && setSelectedDataSourceFields(fromFields);
        }
      }
    },
    [dataSourceFields, dataSources, datasets, setBlendedData, unifiedDataCardAuthoringContext]
  );

  return {
    dataSourceFields,
    dataSourcePicklistValues,
    makeVariableApiName,
    loadDataset,
  };
};
