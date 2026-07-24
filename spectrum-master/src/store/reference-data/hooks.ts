import ObjectID from 'bson-objectid';
import { useCallback, useEffect, useMemo, useState } from 'react';

import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { FetchStatus } from 'store/types';
import AppConstants from 'utils/AppConstants';
import { makeFetchStatus } from 'utils/AppUtil';
import { exhaustIterable, zipper } from 'utils/ArrayUtil';
import { getRtkQueryErrorMessage } from 'utils/getRtkQueryErrorMessage';
import { getCombinedFetchStatus } from 'utils/NetworkUtil';

import { useDeleteReferenceDataMutation, useGetReferenceDataQuery } from './api';
import { selectors } from './slice';
import { getReferenceDataPreview } from './thunks';

export const useReferenceDataList = () => {
  const { data = [], refetch, error, isError, isLoading, isFetching, isSuccess } = useGetReferenceDataQuery();

  return useMemo(() => {
    return {
      data,
      error: getRtkQueryErrorMessage(error),
      loading: isLoading,
      status: makeFetchStatus(isSuccess, isError, isLoading, isFetching),
      refetch,
    };
  }, [data, error, isError, isFetching, isLoading, isSuccess, refetch]);
};

export const useReferenceData = (referenceDataId: string) => {
  const list = useReferenceDataList();
  const preview = useReferenceDataPreview(referenceDataId);

  return useMemo(() => {
    const status = getCombinedFetchStatus(list.status, preview.status);

    return {
      data: list.data.find((record) => record.id === referenceDataId),
      error: list.error || preview.error,
      loading: status === AppConstants.FETCH_STATUS.LOADING,
      status,
    };
  }, [list, preview, referenceDataId]);
};

export const useReferenceDataPreview = (referenceDataId: string) => {
  const dispatch = useEnhancedDispatch();

  const data = useEnhancedSelector((state) => selectors.selectById(state.referenceData, referenceDataId));
  const error = useEnhancedSelector((state) => state.referenceData.previewError[referenceDataId]);
  const status = useEnhancedSelector((state) => state.referenceData.previewStatus[referenceDataId]);

  const loading = status === AppConstants.FETCH_STATUS.LOADING;

  const fetchPreview = useCallback(() => dispatch(getReferenceDataPreview(referenceDataId)), [
    dispatch,
    referenceDataId,
  ]);

  useEffect(() => {
    if (!data?.preview && !loading && !error && referenceDataId) {
      fetchPreview();
    }
  }, [dispatch, fetchPreview, data?.preview, error, loading, referenceDataId]);

  const previewData = useMemo(() => {
    const { headerColumns, rows } = data?.preview || { headerColumns: [], rows: [] };

    const applyHeadersToRow = (row: string[], idx: number) =>
      Object.fromEntries(exhaustIterable(zipper(['__uniqueId', ...headerColumns], [ObjectID.generate(), ...row])));

    return {
      headerColumns,
      rows: rows.map(applyHeadersToRow),
    };
  }, [data]);

  return useMemo(
    () => ({
      data: previewData,
      error,
      loading,
      status,
      refetch: fetchPreview,
    }),
    [previewData, error, fetchPreview, loading, status]
  );
};

export type UseDeleteReferenceDataResult = [
  (refDataId: string) => void,
  {
    referenceDataId: string | undefined;
    error: string | undefined;
    status: FetchStatus;
  }
];

export const useDeleteReferenceData = (): UseDeleteReferenceDataResult => {
  const [referenceDataId, setReferenceDataId] = useState<string>();
  const [deleteReferenceData, { error, isError, isLoading, isSuccess }] = useDeleteReferenceDataMutation();

  const deleteFn = useCallback(
    (refDataId: string) => {
      if (isLoading) {
        return;
      }

      setReferenceDataId(refDataId);
      return deleteReferenceData(refDataId);
    },
    [deleteReferenceData, isLoading]
  );

  return useMemo(
    () => [
      deleteFn,
      {
        referenceDataId,
        error: getRtkQueryErrorMessage(error),
        status: makeFetchStatus(isSuccess, isError, isLoading),
      },
    ],
    [deleteFn, error, isError, isLoading, isSuccess, referenceDataId]
  );
};
