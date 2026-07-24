import { pick } from 'lodash';
import { useEffect, useMemo, useCallback } from 'react';
import { useDispatch } from 'react-redux';

import { useEnhancedSelector } from 'hooks/redux';
import { EMPTY_ARRAY } from 'store/constants';
import { getEntities as selectEntities, getEntitiesFetching as selectEntitiesFetching } from 'store/entity/selectors';
import { getEntities, getEntityRecordsCounts } from 'store/entity/thunks';
import { Entity } from 'store/entity/types';
import AppConstants from 'utils/AppConstants';
import { sort } from 'utils/ArrayUtil';

interface UseSyncariEntitiesResult {
  loading: boolean;
  data: Entity[];
}

const useSyncariEntities = (): UseSyncariEntitiesResult => {
  const dispatch = useDispatch();
  const entities = useEnhancedSelector((state) => selectEntities(state));
  const entitiesFetching = useEnhancedSelector((state) => selectEntitiesFetching(state));

  useEffect(() => {
    if (!entities && !entitiesFetching) {
      dispatch(getEntities());
    }
  }, [entities, entitiesFetching, dispatch]);

  return {
    loading: entitiesFetching,
    data: entities || EMPTY_ARRAY,
  };
};

const usePublishedSyncariEntities = () => {
  const { loading, data } = useSyncariEntities();

  return useMemo(
    () => ({
      loading,
      data: sort(
        Array.isArray(data)
          ? data.filter(
              (entity) =>
                entity.pipelineStatus === AppConstants.SYNCARI_NODE_STATUS.PUBLISHED ||
                entity.pipelineStatus === AppConstants.SYNCARI_NODE_STATUS.PUBLISHED_WITH_DRAFT
            )
          : EMPTY_ARRAY,
        (first, second) => first.displayName.localeCompare(second.displayName)
      ),
    }),
    [loading, data]
  );
};

const useEntityRecordsCount = (entityApiNames: string[]) => {
  const dispatch = useDispatch();

  const recordCounts = useEnhancedSelector((state) => state.entity.entityRecordsCounts);
  const totalCounts = useEnhancedSelector((state) => state.entity.entityRecordTotalCounts);
  const fetchStatus = useEnhancedSelector((state) => state.entity.entityRecordsCountsStatus);

  const fetchEntitiesCount = useCallback(
    (forceFetch: boolean = false) => {
      // only fetch items that are not already in flight
      const entityCountsToFetch = entityApiNames.filter((apiName) => {
        if (forceFetch || !(apiName in fetchStatus)) {
          return true;
        }

        return (
          fetchStatus[apiName] !== AppConstants.FETCH_STATUS.LOADING &&
          fetchStatus[apiName] !== AppConstants.FETCH_STATUS.SUCCESS &&
          fetchStatus[apiName] !== AppConstants.FETCH_STATUS.ERROR
        );
      });

      if (entityCountsToFetch.length > 0) {
        dispatch(getEntityRecordsCounts(entityCountsToFetch));
      }
    },
    [dispatch, entityApiNames, fetchStatus]
  );

  useEffect(() => fetchEntitiesCount(), [fetchEntitiesCount]);

  return useMemo(
    () => ({
      hasSyncedData: Object.values(recordCounts).some((value) => value > 0),
      recordCounts: pick(recordCounts, entityApiNames),
      fetchStatus: pick(fetchStatus, entityApiNames),
      totalCounts,
      fetchEntitiesCount,
    }),
    [entityApiNames, fetchEntitiesCount, fetchStatus, recordCounts, totalCounts]
  );
};

export default useSyncariEntities;
export { useEntityRecordsCount, usePublishedSyncariEntities, useSyncariEntities };
