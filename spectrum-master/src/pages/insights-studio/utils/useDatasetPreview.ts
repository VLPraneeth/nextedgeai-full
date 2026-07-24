import { useCallback, useMemo, useState } from 'react';

import { useGetPreviewMutation, useGetSampleRecordsMutation, useLazyGetCountQuery } from 'store/insights-studio';
import {
  DataCardVizConfig,
  DataCardWithDataset,
  Dataset,
  DatasetRecord,
  DatasetRecordTable,
} from 'store/insights-studio/types';
import { getRtkQueryErrorMessage } from 'utils/getRtkQueryErrorMessage';

import { useDataCardAuthoringContext } from '../context/DataCardAuthoringContext';
import { useUnifiedDataCardAuthoringContext } from '../context/UnifiedDataCardAuthoringContext';
import { makeDatasetResult } from './UnifiedDataCard.util';
import { useUnifiedDataCardAuthoring } from './useUnifiedDataCardAuthoring';

const initialResult = {
  data: [],
  columns: [],
  isLoading: false,
  errorMessage: '',
};

export const useDatasetPreview = ({
  getDatasetAndDataCard,
}: {
  getDatasetAndDataCard: (vizConfig?: DataCardVizConfig) => DataCardWithDataset | undefined;
}) => {
  const [fetchDatasetPreview] = useGetPreviewMutation();
  const { preselectedDatasetId } = useDataCardAuthoringContext();
  const {
    setDatasetConfigPreviewChanged,
    dataCardWithNewDataset,
    displayName,
    apiName: name,
    description,
    tags,
    configMode,
  } = useUnifiedDataCardAuthoringContext();
  const [getSampleRecords] = useGetSampleRecordsMutation();
  const [datasetPreviewResult, setDatasetPreviewResult] = useState<DatasetRecordTable>({ ...initialResult });
  const { getDatasetPayload } = useUnifiedDataCardAuthoring();

  const [fetchTotalCount, { data: countData, error: countError, isLoading: countIsLoading }] = useLazyGetCountQuery();

  const getTotalCount = () => {
    fetchTotalCount(
      { dataset: getDatasetPayload({ displayName, name, description, tags }) as Dataset, mode: configMode },
      false
    );
  };

  const totalCountResult = useMemo(() => {
    const { data } = countData ? makeDatasetResult(countData) : { ...initialResult };
    const count = Object.values(data?.[0] || {})?.[0];
    const errorMessage = getRtkQueryErrorMessage(countError);
    return {
      count,
      isLoading: countIsLoading,
      errorMessage,
    };
  }, [countData, countError, countIsLoading]);

  const initializeResult = (isLoading: boolean) => {
    setDatasetPreviewResult({ ...initialResult, isLoading });
  };

  const processResponse = useCallback(
    (requestPromise: Promise<DatasetRecord>) => {
      requestPromise
        .then((result) => {
          setDatasetConfigPreviewChanged(false);
          setDatasetPreviewResult((current) => {
            return { ...current, ...{ isLoading: false }, ...makeDatasetResult(result), lastRefreshDate: new Date() };
          });
        })
        .catch((err) => {
          setDatasetConfigPreviewChanged(false);
          setDatasetPreviewResult((current) => {
            return {
              ...current,
              ...{
                isLoading: false,
                columns: [],
                data: [],
                lastRefreshDate: new Date(),
                errorMessage: getRtkQueryErrorMessage(err),
              },
            };
          });
        });
    },
    [setDatasetConfigPreviewChanged]
  );

  const getDatasetPreview = useCallback(async () => {
    initializeResult(true);
    if (dataCardWithNewDataset) {
      const datasetWithDatacard = getDatasetAndDataCard();
      if (datasetWithDatacard?.dataset) {
        processResponse(
          fetchDatasetPreview({ dataset: datasetWithDatacard.dataset as Dataset, mode: configMode }).unwrap()
        );
      }
    } else if (preselectedDatasetId) {
      processResponse(getSampleRecords({ datasetId: preselectedDatasetId, variableMap: {} }).unwrap());
    }
  }, [
    dataCardWithNewDataset,
    fetchDatasetPreview,
    getDatasetAndDataCard,
    getSampleRecords,
    preselectedDatasetId,
    processResponse,
    configMode,
  ]);

  return {
    getDatasetPreview,
    datasetPreviewResult,
    getTotalCount,
    totalCountResult,
  };
};
