//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { SkullInput } from 'components/skull';
import DataUrlConstants from 'utils/DataUrlConstants';
import { makeUrl } from 'utils/UrlUtil';

import { injectEndpoints, tags } from '../api';
import {
  CreatePipelineVersionRequest,
  PipelineDetails,
  PipelineDiff,
  PipelineDocumentation,
  PipelineLogsParams,
  PipelineLogsResponse,
  PipelineSyncMetricDetails,
  PipelineTransactionDetails,
  PipelineVersion,
  PipelineVersionPipeline,
  RestorePipelineVersionRequest,
} from './types';

const credentialApi = injectEndpoints({
  endpoints: (builder) => ({
    getPipelineVersionList: builder.query<PipelineVersion[], string>({
      query: (syncariEntityId) => {
        const url = makeUrl(DataUrlConstants.PIPELINE_VERSIONS, { syncariEntityId });
        return { url };
      },
      providesTags: (result) => {
        return [
          ...(result || []).map((version: PipelineVersion) => {
            return tags.PipelineVersion(version.versionId);
          }),
          tags.PipelineVersionList,
        ];
      },
    }),
    getPipelinesDetails: builder.query<PipelineDetails[], void>({
      query: () => {
        const url = makeUrl(DataUrlConstants.GET_PIPELINES_DETAILS);
        return url;
      },
    }),
    getPipelinesTransactionDetails: builder.query<PipelineTransactionDetails[], void>({
      query: () => {
        const url = makeUrl(DataUrlConstants.GET_PIPELINES_TRANSACTION_DETAILS);
        return url;
      },
    }),
    getPipelinesSyncMetricDetails: builder.query<PipelineSyncMetricDetails[], void>({
      query: () => {
        const url = makeUrl(DataUrlConstants.GET_PIPELINES_SYNC_METRIC_DETAILS);

        return url;
      },
    }),
    getPipelinesForVersion: builder.query<PipelineVersionPipeline[], { syncariEntityId: string; versionId: string }>({
      query: ({ syncariEntityId, versionId }) => {
        const url = makeUrl(DataUrlConstants.GET_PIPELINES_FOR_VERSION, { syncariEntityId, versionId });
        return { url };
      },
    }),
    getPipelinesForCompare: builder.query<
      PipelineVersionPipeline[],
      { syncariEntityId: string; versionOneId: string; versionTwoId?: string }
    >({
      query: ({ syncariEntityId, versionOneId, versionTwoId }) => {
        const url = makeUrl(DataUrlConstants.GET_PIPELINES_FOR_COMPARE, {
          syncariEntityId,
          versionOneId,
          versionTwoId,
        });
        return { url };
      },
    }),
    getPipelinesDiff: builder.query<
      PipelineDiff[],
      {
        pipelineType: 'entityPipeline' | 'fieldPipeline';
        pipelineId: string;
        versionOneId: string;
        versionTwoId: string;
      }
    >({
      query: ({ pipelineType, pipelineId, versionOneId, versionTwoId }) => {
        const url = makeUrl(DataUrlConstants.GET_PIPELINES_DIFF, {
          pipelineType,
          pipelineId,
          versionOneId,
          versionTwoId,
        });
        return { url };
      },
    }),
    createPipelineVersion: builder.mutation<PipelineVersion, CreatePipelineVersionRequest>({
      query: (params) => {
        const { syncariEntityId, ...body } = params;
        const url = makeUrl(DataUrlConstants.CREATE_PIPELINE_VERSION, { syncariEntityId });

        return {
          url,
          method: 'POST',
          body,
        };
      },
      invalidatesTags: [tags.PipelineVersionList],
    }),
    restorePipelineVersion: builder.mutation<PipelineVersion, RestorePipelineVersionRequest>({
      query: (params) => {
        const { syncariEntityId, versionId, ...body } = params;
        const url = makeUrl(DataUrlConstants.RESTORE_VERSION, { syncariEntityId, versionId });
        return {
          url,
          method: 'POST',
          body,
        };
      },
      invalidatesTags: [tags.PipelineVersionList],
    }),
    generatePipelineDocumentation: builder.query<PipelineDocumentation, { syncariEntityId: string; version: string }>({
      query: (params) => makeUrl(DataUrlConstants.GENERATE_PIPELINE_DOCUMENTATION, params),
    }),
    getPipelineDocumentation: builder.query<PipelineDocumentation, { syncariEntityId: string; version: string }>({
      query: (params) => makeUrl(DataUrlConstants.PIPELINE_DOCUMENTATION, params),
      providesTags: (result) => [
        ...(result?.syncariEntityId ? [tags.PipelineDocumentation(result.syncariEntityId)] : []),
      ],
    }),
    savePipelineDocumentation: builder.mutation<
      PipelineDocumentation,
      { syncariEntityId: string; content: string; version: string }
    >({
      query: (params) => {
        return {
          url: makeUrl(DataUrlConstants.PIPELINE_DOCUMENTATION, params),
          method: 'POST',
          body: params,
        };
      },
      invalidatesTags: (_, __, arg) => [
        tags.PipelineDocumentation(arg.syncariEntityId),
        tags.PipelineDocumentationList,
      ],
    }),
    getPipelineLogs: builder.mutation<PipelineLogsResponse, PipelineLogsParams>({
      query: ({ cursor, direction, count, ...body }) => {
        return {
          url: makeUrl(
            DataUrlConstants.PIPELINE_LOGS,
            {},
            {
              cursor,
              direction,
              count,
            }
          ),
          method: 'POST',
          body,
        };
      },
    }),
    getPipelineSettingsMeta: builder.query<{ configurations: SkullInput[] }, Record<string, string>>({
      query: (params: Record<string, string>) => ({
        url: makeUrl(DataUrlConstants.PIPELINE_SETTINGS_META, params),
      }),
    }),
    patchPipelineSettings: builder.mutation<void, { entityId: string; payload: any }>({
      query: ({ entityId, payload }) => {
        return {
          url: makeUrl(DataUrlConstants.ENTITY_PIPELINE, { entityId }),
          method: 'PATCH',
          body: payload,
        };
      },
    }),
  }),
});

export const {
  useGetPipelineVersionListQuery,
  useCreatePipelineVersionMutation,
  useRestorePipelineVersionMutation,
  useGetPipelinesDetailsQuery,
  useGetPipelinesTransactionDetailsQuery,
  useGetPipelinesSyncMetricDetailsQuery,
  useGetPipelinesForVersionQuery,
  useGetPipelinesForCompareQuery,
  useGetPipelinesDiffQuery,
  useGeneratePipelineDocumentationQuery,
  useLazyGeneratePipelineDocumentationQuery,
  useGetPipelineDocumentationQuery,
  useSavePipelineDocumentationMutation,
  useGetPipelineLogsMutation,
  useGetPipelineSettingsMetaQuery,
  usePatchPipelineSettingsMutation,
  util: pipelineApiUtil,
} = credentialApi;
