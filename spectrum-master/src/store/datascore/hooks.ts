import { useMemo } from 'react';

import { Predicate } from 'store/data-studio/types';

import { useGetDataScoreForEntityQuery } from './api';
import { gaugeSegments, unavailableDataScoreSegment } from './constants';
import { getSegmentForValue } from './utils';

export const useDataScoreForEntity = (entityId?: string, predicate?: Predicate) => {
  const hookResult = useGetDataScoreForEntityQuery({ entityId: entityId || '', predicate }, { skip: !entityId });

  return useMemo(() => {
    const { data, ...rest } = hookResult;
    const { data: responseData, status: dataScoreStatus } = data || {};
    const segmentConfig =
      dataScoreStatus === 'available'
        ? getSegmentForValue(gaugeSegments, responseData?.score || 0)
        : unavailableDataScoreSegment;

    return {
      ...rest,
      color: segmentConfig.color,
      data: responseData,
      dataScoreStatus,
    };
  }, [hookResult]);
};
