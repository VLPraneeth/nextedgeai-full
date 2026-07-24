//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { fillJoinId } from 'pages/insights-studio/utils/UnifiedDataCard.util';
import { EMPTY_ARRAY } from 'store/constants';
import DataUrlConstants from 'utils/DataUrlConstants';
import { makeUrl } from 'utils/UrlUtil';

import { injectEndpoints, tags } from '../api';
import {
  ConfigMode,
  DataSource,
  DataSourceFields,
  Dataset,
  DatasetExportJob,
  DatasetFunction,
  DatasetReadData,
  DatasetRecord,
  DatasetVariable,
  Joins,
  UsedByItem,
  VariableValue,
} from './types';

const datasetApi = injectEndpoints({
  endpoints: (builder) => ({
    getDatasets: builder.query<Dataset[], boolean | void>({
      query: (isThoughtspot = false) => ({
        url: isThoughtspot ? DataUrlConstants.INSIGHTS_TS_DATASET : DataUrlConstants.INSIGHTS_DATASET,
      }),
      providesTags: () => [tags.InsightsDatasetList],
    }),
    createDataset: builder.mutation<Dataset, Dataset>({
      query: (dataset) => ({ url: DataUrlConstants.INSIGHTS_DATASET, method: 'POST', body: dataset }),
      invalidatesTags: [tags.InsightsDatasetList],
    }),
    getDataset: builder.query<Dataset, Record<string, string>>({
      query: ({ datasetId }) => ({
        url: makeUrl(DataUrlConstants.INSIGHTS_DATASET_ENTRY, { datasetId }),
      }),
      providesTags: (result) => (result?.id ? [tags.InsightsDataset(result.id)] : EMPTY_ARRAY),
    }),
    updateDataset: builder.mutation<Dataset, Dataset>({
      query: (dataset) => ({
        url: makeUrl(DataUrlConstants.INSIGHTS_DATASET_ENTRY, { datasetId: dataset.id }),
        method: 'PUT',
        body: dataset,
      }),
      invalidatesTags: [tags.InsightsDatasetList],
    }),
    deleteDataset: builder.mutation<Dataset, string>({
      query: (datasetId) => ({
        url: makeUrl(DataUrlConstants.INSIGHTS_DATASET_DELETE, { datasetId }),
        method: 'DELETE',
      }),
      invalidatesTags: [tags.InsightsDatasetList],
    }),
    deleteDatasets: builder.mutation<Dataset, string[]>({
      query: (datasetIds) => ({
        url: DataUrlConstants.INSIGHTS_DATASETS_DELETE,
        method: 'DELETE',
        body: datasetIds,
      }),
      invalidatesTags: [tags.InsightsDatasetList],
    }),
    createDatasetDraft: builder.mutation<Dataset, string>({
      query: (datasetId) => ({
        url: makeUrl(DataUrlConstants.INSIGHTS_DATASET_CREATE_DRAFT, { datasetId }),
        method: 'POST',
      }),
      invalidatesTags: [tags.InsightsDatasetList],
    }),
    publishDataset: builder.mutation<void, string>({
      query: (datasetId) => ({
        url: makeUrl(DataUrlConstants.INSIGHTS_DATASET_PUBLISH, { datasetId }),
        method: 'POST',
      }),
      invalidatesTags: [tags.InsightsDatasetList],
    }),
    discardDatasetDraft: builder.mutation<Dataset, string>({
      query: (datasetId) => ({
        url: makeUrl(DataUrlConstants.INSIGHTS_DATASET_DISCARD_DRAFT, { datasetId }),
        method: 'DELETE',
      }),
      invalidatesTags: [tags.InsightsDatasetList],
    }),
    getSampleRecords: builder.mutation<
      DatasetRecord,
      { datasetId: string; variableMap: Record<string, VariableValue> }
    >({
      query: ({ datasetId, variableMap }) => {
        return {
          method: 'POST',
          url: makeUrl(DataUrlConstants.INSIGHTS_DATASET_SAMPLE_RECORD, { datasetId }),
          body: variableMap,
        };
      },
    }),
    getPreview: builder.mutation<DatasetRecord, { dataset: Dataset; mode: ConfigMode }>({
      query: ({ dataset, mode }) => {
        return {
          method: 'POST',
          url:
            mode === 'BASIC'
              ? makeUrl(DataUrlConstants.INSIGHTS_DATACARD_WITH_DATASET_SAMPLE)
              : makeUrl(DataUrlConstants.INSIGHTS_DATACARD_WITH_DATASET_SAMPLE_WITH_QUERY),
          body: dataset,
        };
      },
    }),
    getQuery: builder.mutation<Dataset, Dataset>({
      query: (dataset) => {
        return {
          method: 'POST',
          url: makeUrl(DataUrlConstants.INSTGHTS_DATASET_GET_QUERY),
          body: dataset,
        };
      },
    }),
    getSchema: builder.query<string, void>({
      query: () => {
        return {
          url: makeUrl(DataUrlConstants.INSIGHTS_DATASET_SCHEMA),
          responseHandler: (response) => response.text(),
        };
      },
    }),
    getDatasetFromQuery: builder.mutation<Dataset, Dataset>({
      query: (dataset) => {
        return {
          method: 'POST',
          url: makeUrl(DataUrlConstants.INSIGHTS_DATASET_FROM_QUERY),
          body: dataset,
        };
      },
    }),
    getDatasetAndEntityInfo: builder.query<DataSource[], { withEntityInfo: boolean; isThoughtspot: boolean }>({
      query: ({ withEntityInfo, isThoughtspot }) => ({
        url: makeUrl(
          isThoughtspot ? DataUrlConstants.INSIGHTS_DATASET_TS_ENTITIES : DataUrlConstants.INSIGHTS_DATASET_ENTITIES,
          {},
          { withEntityInfo }
        ),
      }),
    }),
    getDatasetFunctions: builder.query<DatasetFunction[], void>({
      query: () => ({
        url: DataUrlConstants.INSIGHTS_DATASET_FUNCTIONS,
      }),
    }),
    createVariable: builder.mutation<
      DatasetVariable,
      { datasetId: string; variable: Omit<DatasetVariable, 'apiName'> }
    >({
      query: ({ datasetId, variable }) => ({
        url: makeUrl(DataUrlConstants.INSIGHTS_DATASET_CREATE_VARIABLE, { datasetId }),
        method: 'POST',
        body: variable,
      }),
      invalidatesTags: [tags.InsightsDatasetVariableList],
    }),
    getVariable: builder.query<DatasetVariable[], { datasetId: string }>({
      query: (variableParams) => ({
        url: makeUrl(DataUrlConstants.INSIGHTS_DATASET_VARIABLE, { datasetId: variableParams.datasetId }),
      }),
      providesTags: () => [tags.InsightsDatasetVariableList],
    }),
    deleteVariable: builder.mutation<DatasetVariable, { datasetId: string; apiName: string }>({
      query: ({ datasetId, apiName }) => ({
        url: makeUrl(DataUrlConstants.INSIGHTS_DATASET_DELETE_VARIABLE, { datasetId, apiName }),
        method: 'DELETE',
      }),
      invalidatesTags: [tags.InsightsDatasetVariableList],
    }),
    updateVariable: builder.mutation<DatasetVariable, { datasetId: string; variable: DatasetVariable }>({
      query: ({ datasetId, variable }) => {
        return {
          url: makeUrl(DataUrlConstants.INSIGHTS_DATASET_UPDATE_VARIABLE, { datasetId }),
          method: 'POST',
          body: variable,
        };
      },
      invalidatesTags: [tags.InsightsDatasetVariableList],
    }),
    getDataSourceFields: builder.query<
      { dataSourceFields: DataSourceFields[]; dataSourceAlias: string },
      { dataSourceId: string; dataSourceType: string; alias: string }
    >({
      query: ({ dataSourceId, dataSourceType, alias }) => ({
        url: makeUrl(
          DataUrlConstants.INSIGHTS_DATASET_DATA_SOURCE_FIELDS,
          { dataSourceId, dataSourceType },
          { datasourceAlias: alias }
        ),
      }),
      providesTags: (result, _, { dataSourceId, dataSourceType }) => [
        tags.InsightsDatasetSourceField(`${dataSourceId}${dataSourceType}`),
        tags.InsightsDatasetSourceFieldList,
      ],
      transformResponse: (dsFieldsPayload: { dataSourceFields: DataSourceFields[]; dataSourceAlias: string }) => ({
        dataSourceFields: dsFieldsPayload?.dataSourceFields.map((fields) => {
          return {
            ...fields,
            type: 'variable', // Server expected hardcoded value. TODO: Server need to remove this!
          };
        }),
        dataSourceAlias: dsFieldsPayload.dataSourceAlias,
      }),
    }),
    getSuggestedJoin: builder.query<Joins[], DataSource[]>({
      query: (dataSources) => {
        return {
          url: makeUrl(DataUrlConstants.INSIGHTS_DATASET_SUGGEST_JOINS),
          method: 'POST',
          body: dataSources,
        };
      },
      transformResponse: fillJoinId,
    }),
    getRecommendedJoin: builder.query<Joins[], { existingDataSources: DataSource[]; newDataSources: DataSource[] }>({
      query: (dataSources) => {
        return {
          url: makeUrl(DataUrlConstants.INSIGHTS_DATASET_AUTO_JOIN),
          method: 'POST',
          body: dataSources,
        };
      },
      transformResponse: fillJoinId,
    }),
    getDatasetDependencies: builder.query<UsedByItem[], { datasetId: string }>({
      query: ({ datasetId }) => {
        return {
          url: makeUrl(DataUrlConstants.INSIGHTS_DATASET_DEPENDENCIES, { datasetId }),
        };
      },
    }),
    getCount: builder.query<DatasetRecord, { dataset: Dataset; mode: ConfigMode }>({
      query: ({ dataset, mode }) => {
        return {
          url:
            mode === 'BASIC' ? DataUrlConstants.INSIGHTS_DATASET_COUNT : DataUrlConstants.INSIGHTS_DATASET_COUNT_QUERY,
          method: 'POST',
          body: dataset,
        };
      },
    }),
    getExportJobs: builder.query<DatasetExportJob[], string>({
      query: (datasetId) => ({
        url: makeUrl(DataUrlConstants.INSIGHTS_DATASET_EXPORT_JOBS, { datasetId }),
      }),
      providesTags: () => [tags.InsightsDatasetExportList],
    }),
    createExport: builder.mutation<any, Dataset>({
      query: (dataset) => {
        return {
          url: makeUrl(DataUrlConstants.INSIGHTS_DATASET_EXPORT, { datasetId: dataset.id }),
          method: 'POST',
          body: dataset,
        };
      },
      invalidatesTags: () => [tags.InsightsDatasetExportList],
    }),
    cancelExport: builder.mutation<any, string>({
      query: (exportJobId) => {
        return {
          url: makeUrl(DataUrlConstants.INSIGHTS_DATASET_CANCEL_EXPORT, { exportJobId }),
          method: 'POST',
        };
      },
      invalidatesTags: () => [tags.InsightsDatasetExportList],
    }),
    deleteExport: builder.mutation<any, string>({
      query: (exportJobId) => ({
        url: makeUrl(DataUrlConstants.INSIGHTS_DATASET_DELETE_EXPORT, { exportJobId }),
        method: 'DELETE',
      }),
      invalidatesTags: [tags.InsightsDatasetExportList],
    }),
    readData: builder.mutation<DatasetRecord, DatasetReadData>({
      query: (readData) => {
        return {
          method: 'POST',
          url: makeUrl(DataUrlConstants.INSIGHTS_DATASET_READ_DATA),
          body: readData,
        };
      },
    }),
  }),
});

export const {
  useGetDatasetsQuery,
  useLazyGetDatasetsQuery,
  useCreateDatasetMutation,
  useUpdateDatasetMutation,
  useLazyGetDatasetQuery,
  useCreateDatasetDraftMutation,
  usePublishDatasetMutation,
  useDiscardDatasetDraftMutation,
  useDeleteDatasetMutation,
  useDeleteDatasetsMutation,
  useGetSampleRecordsMutation,
  useGetPreviewMutation,
  useGetDatasetAndEntityInfoQuery,
  useGetDatasetFunctionsQuery,
  useCreateVariableMutation,
  useGetVariableQuery,
  useLazyGetVariableQuery,
  useDeleteVariableMutation,
  useUpdateVariableMutation,
  useLazyGetDataSourceFieldsQuery,
  useLazyGetSuggestedJoinQuery,
  useLazyGetRecommendedJoinQuery,
  useLazyGetDatasetDependenciesQuery,
  useGetCountQuery,
  useLazyGetCountQuery,

  useGetExportJobsQuery,
  useLazyGetExportJobsQuery,
  useCreateExportMutation,
  useCancelExportMutation,
  useDeleteExportMutation,
  useReadDataMutation,
  useGetQueryMutation,
  useGetSchemaQuery,
  useGetDatasetFromQueryMutation,
  util: datasetApiUtil,
  endpoints: { getDataSourceFields },
} = datasetApi;
