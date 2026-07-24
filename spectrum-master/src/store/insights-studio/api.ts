//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { VariableMapping } from 'pages/insights-studio/settings/MultiVariableMapping';
import { InstanceFeature } from 'store/instance-feature/types';
import DataUrlConstants from 'utils/DataUrlConstants';
import { makeUrl } from 'utils/UrlUtil';

import { injectEndpoints, tags } from '../api';
import {
  DataCardRequestParams,
  InsightsDashboard,
  DataCardWithData,
  DataCard,
  DataCardWithDataset,
  UsedByItem,
  LastVisitedDashboard,
  DataCardRequestPageParams,
  DataCardData,
  DashboardVariablePreferences,
} from './types';

const insightsApi = injectEndpoints({
  endpoints: (builder) => ({
    enableInsights: builder.mutation<InstanceFeature, string>({
      query: () => ({
        method: 'POST',
        url: makeUrl(DataUrlConstants.ENABLE_INSIGHTSPROVIDER),
      }),
      invalidatesTags: (result) => [...(result?.name ? [tags.InstanceFeature(result.name)] : [])],
    }),
    generateTSToken: builder.mutation<string, void>({
      query: () => ({
        method: 'POST',
        url: makeUrl(DataUrlConstants.INSIGHTS_TS_TOKEN),
        responseHandler: (response) => response.text(), // Parse as text
      }),
    }),
    shareTSObject: builder.mutation<string, { metadataType: string; metadataId: string }>({
      query: ({ metadataType, metadataId }) => ({
        method: 'POST',
        url: makeUrl(DataUrlConstants.INSIGHTS_TS_SHARE, { metadataType, metadataId }),
      }),
    }),
    getTSLiveboards: builder.query<Record<string, string>, void>({
      query: () => ({
        url: makeUrl(DataUrlConstants.INSIGHTS_TS_LIVEBOARDS),
      }),
    }),
    getDashboards: builder.query<InsightsDashboard[] | undefined, void>({
      query: () => {
        return { url: makeUrl(DataUrlConstants.INSIGHTS_DASHBOARDS) };
      },
      providesTags: () => [tags.InsightsDashboardList],
    }),
    getDashboard: builder.query<InsightsDashboard | undefined, string>({
      query: (dashboardId) => {
        return { url: makeUrl(DataUrlConstants.INSIGHTS_DASHBOARD, { dashboardId }) };
      },
      providesTags: [tags.InsightsDashboard('dashboard')],
    }),
    getDashDataCardWithConfiguration: builder.query<DataCardWithData, DataCardRequestParams>({
      query: ({ dashboardId, dataCardId, configuration }) => {
        return {
          url: makeUrl(DataUrlConstants.INSIGHTS_GET_DATACARD, { dashboardId, dataCardId }),
          method: 'POST',
          body: configuration,
        };
      },
      providesTags: (card) => (!!card ? [tags.InsightsDashDataCard(card.id)] : []),
    }),
    getDashDataCardWithConfigPage: builder.mutation<DataCardData, DataCardRequestPageParams>({
      query: ({ dashboardId, dataCardId, ...body }) => {
        return {
          url: makeUrl(DataUrlConstants.INSIGHTS_GET_DATACARD_PAGE, { dashboardId, dataCardId }),
          method: 'POST',
          body,
        };
      },
    }),
    createDashboard: builder.mutation<
      InsightsDashboard,
      Pick<InsightsDashboard, 'displayName' | 'description' | 'tags'>
    >({
      query: (newDashboard) => {
        return {
          url: makeUrl(DataUrlConstants.INSIGHTS_DASHBOARDS),
          method: 'POST',
          body: newDashboard,
        };
      },
      invalidatesTags: () => [tags.InsightsDashboardList],
    }),
    editDashboard: builder.mutation<InsightsDashboard, InsightsDashboard>({
      query: (dashboard) => {
        return {
          url: makeUrl(DataUrlConstants.INSIGHTS_DASHBOARD, { dashboardId: dashboard.id }),
          method: 'PUT',
          body: dashboard,
        };
      },
      invalidatesTags: [tags.InsightsDashboardList, tags.InsightsDashboard('dashboard')],
    }),
    publishDraftDashboard: builder.mutation<undefined, string>({
      query: (dashboardId) => {
        return {
          url: makeUrl(DataUrlConstants.INSIGHTS_PUBLISH_DRAFT_DASHBOARD, { dashboardId }),
          method: 'POST',
        };
      },
      invalidatesTags: () => [tags.InsightsDashboardList, tags.InsightsDashboard('dashboard')],
    }),
    deleteDashboard: builder.mutation<undefined, string>({
      query: (dashboardId) => {
        return {
          url: makeUrl(DataUrlConstants.INSIGHTS_DASHBOARD, { dashboardId }),
          method: 'DELETE',
        };
      },
      invalidatesTags: () => [tags.InsightsDashboardList],
    }),
    deleteDraftDashboard: builder.mutation<undefined, string>({
      query: (dashboardId) => {
        return {
          url: makeUrl(DataUrlConstants.INSIGHTS_DELETE_DRAFT_DASHBOARD, { dashboardId }),
          method: 'DELETE',
        };
      },
      invalidatesTags: () => [tags.InsightsDashboardList, tags.InsightsDashboard('dashboard')],
    }),
    createDraftDashboard: builder.mutation<InsightsDashboard, string>({
      query: (dashboardId) => {
        return {
          url: makeUrl(DataUrlConstants.INSIGHTS_CREATE_DRAFT_DASHBOARD, { dashboardId }),
          method: 'POST',
        };
      },
      invalidatesTags: () => [tags.InsightsDashboardList, tags.InsightsDashboard('dashboard')],
    }),
    getLastVisitedDashboard: builder.query<LastVisitedDashboard, null | void>({
      query: () => {
        return {
          url: makeUrl(DataUrlConstants.INSIGHTS_DASHBOARD, { dashboardId: 'lastVisited' }),
        };
      },
      providesTags: [tags.InsightsDashboard('lastVisited')],
    }),
    setLastVisitedDashboard: builder.mutation<LastVisitedDashboard, LastVisitedDashboard>({
      query: (payload) => {
        return {
          method: 'POST',
          url: makeUrl(DataUrlConstants.INSIGHTS_DASHBOARD, { dashboardId: 'lastVisited' }),
          body: payload,
        };
      },
      invalidatesTags: [tags.InsightsDashboard('lastVisited')],
    }),
    setDashboardVariable: builder.mutation<void, { dashboardId: string; variableMappings: VariableMapping[] }>({
      query: ({ dashboardId, variableMappings }) => {
        return {
          method: 'POST',
          url: makeUrl(DataUrlConstants.INSIGHTS_DASHBOARD_VARIABLES, { dashboardId }),
          body: variableMappings,
        };
      },
    }),
    getDashboardVariable: builder.query<VariableMapping[], { dashboardId: string }>({
      query: ({ dashboardId }) => {
        return {
          method: 'GET',
          url: makeUrl(DataUrlConstants.INSIGHTS_DASHBOARD_VARIABLES, { dashboardId }),
        };
      },
    }),
    setDasbhardVariablePreferences: builder.mutation<
      void,
      { dashboardId: string; dataCardsVariableMappings: DashboardVariablePreferences }
    >({
      query: ({ dashboardId, dataCardsVariableMappings }) => {
        return {
          method: 'POST',
          url: makeUrl(DataUrlConstants.INSIGHTS_DASHBOARD_VARIABLES_PREF, { dashboardId }),
          body: dataCardsVariableMappings,
        };
      },
    }),
  }),
});

export const {
  useCreateDashboardMutation,
  useCreateDraftDashboardMutation,
  useDeleteDashboardMutation,
  useDeleteDraftDashboardMutation,
  useEditDashboardMutation,
  useGetDashboardQuery,
  useGetDashboardsQuery,
  useLazyGetDashboardsQuery,
  useLazyGetDashDataCardWithConfigurationQuery,
  useGetDashDataCardWithConfigurationQuery,
  useGetDashDataCardWithConfigPageMutation,
  usePublishDraftDashboardMutation,
  util: insightsApiUtil,
  useGetLastVisitedDashboardQuery,
  useSetLastVisitedDashboardMutation,
  useSetDashboardVariableMutation,
  useGetDashboardVariableQuery,
  useSetDasbhardVariablePreferencesMutation,
  endpoints: dashboardEndpoints,
  useEnableInsightsMutation,
  useGenerateTSTokenMutation,
  useGetTSLiveboardsQuery,
  useShareTSObjectMutation,
} = insightsApi;

const dataCardAuthoringApi = injectEndpoints({
  endpoints: (builder) => ({
    getAllDataCards: builder.query<DataCard[] | undefined, void>({
      query: () => ({ url: DataUrlConstants.INSIGHTS_DATACARDS }),
      providesTags: [tags.InsightsDataCardList],
    }),
    getDataCard: builder.query<DataCard, string>({
      query: (dataCardId: string) => ({
        url: makeUrl(DataUrlConstants.INSIGHTS_DATACARD, { dataCardId }),
      }),
      providesTags: [tags.InsightsDataCard('card')],
    }),
    createDataCard: builder.mutation<DataCard, Pick<DataCard, 'description' | 'displayName' | 'name' | 'tags'>>({
      query: (dataCard) => ({
        method: 'POST',
        url: DataUrlConstants.INSIGHTS_DATACARDS,
        body: dataCard,
      }),
      invalidatesTags: [tags.InsightsDataCardList],
    }),
    createDraftDataCard: builder.mutation<DataCard, string>({
      query: (dataCardId) => {
        return {
          url: makeUrl(DataUrlConstants.INSIGHTS_DATACARD_DRAFT, { dataCardId }),
          method: 'POST',
        };
      },
      invalidatesTags: [tags.InsightsDataCardList],
    }),
    deleteDraftDataCard: builder.mutation<undefined, string>({
      query: (dataCardId) => {
        return {
          url: makeUrl(DataUrlConstants.INSIGHTS_DATACARD_DISCARD, { dataCardId }),
          method: 'DELETE',
        };
      },
      invalidatesTags: [tags.InsightsDataCardList],
    }),
    deleteDataCard: builder.mutation<undefined, string>({
      query: (dataCardId) => {
        return {
          url: makeUrl(DataUrlConstants.INSIGHTS_DATACARD_DELETE, { dataCardId }),
          method: 'DELETE',
        };
      },
      invalidatesTags: [tags.InsightsDataCardList],
    }),
    editDataCard: builder.mutation<DataCard, DataCard>({
      query: (dataCard) => ({
        method: 'PUT',
        url: makeUrl(DataUrlConstants.INSIGHTS_DATACARD, { dataCardId: dataCard.id }),
        body: dataCard,
      }),
      invalidatesTags: (card) => [
        tags.InsightsDataCard('card'),
        tags.InsightsDataCardList,
        tags.InsightsDashDataCard(card ? card.id : ''),
      ],
    }),
    editDataCardAndCreateDataset: builder.mutation<DataCardWithDataset, DataCardWithDataset>({
      query: (dataCardWithDataset) => ({
        method: 'PUT',
        url: makeUrl(DataUrlConstants.INSIGHTS_DATACARD_CREATE_DATASET, {
          dataCardId: dataCardWithDataset.datacard.id,
        }),
        body: dataCardWithDataset,
      }),
      invalidatesTags: (dataCardWithDataset) => [
        tags.InsightsDataCard('card'),
        tags.InsightsDataCardList,
        tags.InsightsDashDataCard(dataCardWithDataset?.datacard.id ?? ''),
        tags.InsightsDatasetList,
      ],
    }),
    publishDraftDataCard: builder.mutation<DataCard, string>({
      query: (dataCardId) => {
        return {
          url: makeUrl(DataUrlConstants.INSIGHTS_DATACARD_PUBLISH, { dataCardId }),
          method: 'POST',
        };
      },
      invalidatesTags: [tags.InsightsDataCardList],
    }),
    previewDataCard: builder.mutation<DataCardWithData, DataCardWithDataset>({
      query: (dataCardWithDataset) => ({
        method: 'POST',
        url: makeUrl(DataUrlConstants.INSIGHTS_DATACARD_PREVIEW),
        body: dataCardWithDataset,
      }),
    }),
    saveDataCardWithDataset: builder.mutation<DataCardWithDataset, DataCardWithDataset>({
      query: (dataCardWithDataset) => ({
        method: 'POST',
        url: makeUrl(DataUrlConstants.INSIGHTS_DATACARD_WITH_DATASET),
        body: dataCardWithDataset,
      }),
      invalidatesTags: [tags.InsightsDataCardList, tags.InsightsDatasetList],
    }),
    getDataCardDependencies: builder.query<UsedByItem[], { dataCardId: string }>({
      query: ({ dataCardId }) => {
        return {
          url: makeUrl(DataUrlConstants.INSIGHTS_DATACARD_DEPENDENCIES, { dataCardId }),
        };
      },
    }),
  }),
});

export const {
  useCreateDataCardMutation,
  useCreateDraftDataCardMutation,
  useDeleteDataCardMutation,
  useDeleteDraftDataCardMutation,
  useEditDataCardMutation,
  useEditDataCardAndCreateDatasetMutation,
  useGetAllDataCardsQuery,
  useLazyGetAllDataCardsQuery,
  useLazyGetDataCardQuery,
  usePreviewDataCardMutation,
  usePublishDraftDataCardMutation,
  useSaveDataCardWithDatasetMutation,
  useLazyGetDataCardDependenciesQuery,
} = dataCardAuthoringApi;
