//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import DataUrlConstants from 'utils/DataUrlConstants';
import { makeUrl } from 'utils/UrlUtil';

import { injectEndpoints, tags } from '../api';
import { InstanceFeature } from './types';

// List of BE defined instance features that UI show / hide components
export const InstanceFeatures = {
  INSIGHTS: 'Insights',
  INSIGHTS_PROVIDER: 'InsightsProvider',
  PIPELINE_EDITOR_V2: 'PipelineEditorV2',
  DFI_V2: 'DfiV2',
  ABAC: 'ABAC',
  BRAND: 'BRAND',
};

const instanceFeatureApi = injectEndpoints({
  endpoints: (builder) => ({
    enableFeature: builder.mutation<InstanceFeature, string>({
      query: (featureName) => ({
        method: 'POST',
        url: makeUrl(DataUrlConstants.INSTANCE_FEATURE_ENABLE, { featureName }),
      }),
      invalidatesTags: (result) => [...(result?.name ? [tags.InstanceFeature(result.name)] : [])],
    }),
    disableFeature: builder.mutation<InstanceFeature, string>({
      query: (featureName) => ({
        method: 'POST',
        url: makeUrl(DataUrlConstants.INSTANCE_FEATURE_DISABLE, { featureName }),
      }),
      invalidatesTags: (result) => [...(result?.name ? [tags.InstanceFeature(result.name)] : [])],
    }),
    getFeatureStatus: builder.query<InstanceFeature, string>({
      query: (featureName) => ({
        url: makeUrl(DataUrlConstants.INSTANCE_FEATURE, { featureName }),
      }),
      providesTags: (result) => [...(result?.name ? [tags.InstanceFeature(result.name)] : [])],
    }),
    getFeatures: builder.query<InstanceFeature[], void>({
      query: () => ({
        url: makeUrl(DataUrlConstants.INSTANCE_FEATURES),
      }),
      providesTags: () => [tags.InstanceFeatureList],
    }),
  }),
});

export const {
  useEnableFeatureMutation,
  useDisableFeatureMutation,
  useGetFeatureStatusQuery,
  useGetFeaturesQuery,
} = instanceFeatureApi;
