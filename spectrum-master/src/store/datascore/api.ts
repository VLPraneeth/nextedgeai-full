import DataUrlConstants from 'utils/DataUrlConstants';
import { makeUrl } from 'utils/UrlUtil';

import { injectEndpoints, tags } from '../api';
import { EntityDataScoreResponse, EnhancedEntityDataScore, GetDataScoreForEntityArgs } from './types';
import { encodeFactorId } from './utils';

const datascoreApi = injectEndpoints({
  endpoints: (builder) => ({
    getDataScoreForEntity: builder.query<EntityDataScoreResponse<EnhancedEntityDataScore>, GetDataScoreForEntityArgs>({
      query: ({ entityId, predicate }) => ({
        url: makeUrl(DataUrlConstants.DATASCORE_FOR_ENTITY, { entityId }, { includeTrend: true }),
      }),
      transformResponse: (response) => {
        const { data: responseData, status } = response as EntityDataScoreResponse;

        return {
          status,
          data: {
            ...responseData,
            factors: responseData.factors.map((f) => ({
              ...f,
              factorId: encodeFactorId(f.entityId, f.fieldName, f.ruleId),
            })),
          },
        };
      },
      providesTags: (_, __, { entityId, predicate }) => [
        tags.EntityDataScore(predicate ? `${entityId}_WITH_PREDICATE` : entityId),
      ],
    }),
  }),
  overrideExisting: false,
});

export const { useGetDataScoreForEntityQuery, useLazyGetDataScoreForEntityQuery } = datascoreApi;
