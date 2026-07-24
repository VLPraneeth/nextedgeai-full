//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { MetadataDependsOn } from 'components/skull';
import DataUrlConstants from 'utils/DataUrlConstants';
import { makeUrl } from 'utils/UrlUtil';

import { injectEndpoints } from '../api';

const skullApi = injectEndpoints({
  endpoints: (builder) => ({
    getDependsOnValues: builder.mutation<
      any,
      {
        dependsOn: MetadataDependsOn;
        dependantValues: Record<string, unknown>;
      }
    >({
      query: ({ dependsOn, dependantValues }) => {
        return {
          url: makeUrl(DataUrlConstants.GET_METADATA_VALUES),
          method: 'POST',
          body: {
            componentType: 'quickstart',
            inputs: dependantValues,
            ...dependsOn.metadata,
          },
        };
      },
    }),
  }),
  overrideExisting: false,
});

export const { useGetDependsOnValuesMutation } = skullApi;
