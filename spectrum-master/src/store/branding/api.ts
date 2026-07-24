import DataUrlConstants from 'utils/DataUrlConstants';
import { makeUrl } from 'utils/UrlUtil';

import { injectEndpoints, tags } from '../api';

const brandApi = injectEndpoints({
  endpoints: (builder) => ({
    getBranding: builder.query<any | undefined, void>({
      query: () => {
        return {
          url: DataUrlConstants.BRAND,
        };
      },
      providesTags: [tags.BrandingList],
    }),
    resetBranding: builder.mutation<void, void>({
      query: () => {
        return {
          url: makeUrl(DataUrlConstants.BRAND_RESET),
          method: 'DELETE',
        };
      },
      invalidatesTags: [tags.BrandingList],
    }),
  }),
});

export const { useGetBrandingQuery, useResetBrandingMutation } = brandApi;
