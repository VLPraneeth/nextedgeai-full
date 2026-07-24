import { InvalidationState } from '@reduxjs/toolkit/dist/query/core/apiState';

import { EntityTag, EntityTags } from './types';

/**
 * Creates an initial state object for RTK-Query api slices. This should be used
 * for initial state in tests, not for the reducer, which will init itself.
 */
export const makeInitialApiState = <Name extends string, Tag extends EntityTags>(
  reducerPath: Name,
  tagTypes: readonly Tag[]
) => ({
  queries: {},
  mutations: {},
  provided: tagTypes.reduce(
    (acc, tag) => ({
      ...acc,
      [tag]: {},
    }),
    {}
  ) as InvalidationState<Tag>,
  subscriptions: {},
  config: {
    reducerPath,
    focused: true,
    keepUnusedDataFor: 30,
    online: true,
    refetchOnFocus: true,
    refetchOnMountOrArgChange: true,
    refetchOnReconnect: true,
    middlewareRegistered: false,
  },
});

export default makeInitialApiState('api', Object.keys(EntityTag) as EntityTags[]);
