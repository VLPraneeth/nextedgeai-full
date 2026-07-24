import { navigate, useLocation } from '@reach/router';
import { message } from 'antd';
import { isEqual } from 'lodash';
import { useCallback, useEffect, useMemo, useState } from 'react';
import userflow from 'userflow.js';

import { stopTransitioningPipelineToast } from 'actions/entityPipelineActions';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import usePreviousValue from 'hooks/usePreviousValue';
import AppConstants from 'utils/AppConstants';
import { tNamespaced } from 'utils/i18nUtil';

import { PipelineStateState, PipelineStatusState } from '../PipelineDetailsFilterPanel';
import { parsePipelineState, parsePipelineStatus, parseSourcesOrDestinations } from './PipelineDetails.utils';

const tn = tNamespaced('PipelineDetails');

const VALID_SEARCH_PARAMS = ['panel', 'pipelineState', 'pipelineStatus', 'search', 'sources', 'destinations'];

export type SearchParamData = {
  name: string;
  value: string;
};

export interface SearchParamValues {
  panel: string;
  search: string;
  pipelineState: PipelineStateState;
  pipelineStatus: PipelineStatusState;
  sources: string[];
  destinations: string[];
}

export const usePipelineDetailsSearchParameters = () => {
  const location = useLocation();

  const [urlSearchParams, setUrlSearchParams] = useState(new URLSearchParams(location.search));

  // We need to filter the query params from the URL to prevent extra calls
  // from occuring to our userflow instance.
  userflow.setUrlFilter((url) => {
    const parsed = new URL(url);

    VALID_SEARCH_PARAMS.forEach((param) => {
      parsed.searchParams.delete(param);
    });

    return parsed.toString();
  });

  useEffect(() => {
    const updatedSearchParams = new URLSearchParams(location.search);

    // Prune any irrevalent search params
    for (const key of updatedSearchParams.keys()) {
      if (!VALID_SEARCH_PARAMS.includes(key)) {
        updatedSearchParams.delete(key);
      }
    }

    setUrlSearchParams(updatedSearchParams);
  }, [location]);

  const values: SearchParamValues = useMemo(
    () => ({
      panel: urlSearchParams.get('panel') ?? '',
      search: urlSearchParams.get('search') ?? '',
      pipelineState: parsePipelineState(urlSearchParams.get('pipelineState') ?? ''),
      pipelineStatus: parsePipelineStatus(urlSearchParams.get('pipelineStatus') ?? ''),
      sources: parseSourcesOrDestinations(urlSearchParams.get('sources') ?? ''),
      destinations: parseSourcesOrDestinations(urlSearchParams.get('destinations') ?? ''),
    }),
    [urlSearchParams]
  );

  const updateSearchParams = useCallback(
    (seachParamsData: SearchParamData[], baseUrl?: string) => {
      const updatedSearchParams = new URLSearchParams(urlSearchParams.toString());

      seachParamsData.forEach(({ name, value }) => {
        if (VALID_SEARCH_PARAMS.includes(name)) {
          if (value) {
            updatedSearchParams.set(name, value);
          } else {
            updatedSearchParams.delete(name);
          }
        }
      });

      const query = !!updatedSearchParams.toString() ? `?${updatedSearchParams.toString()}` : '';
      const url = (baseUrl ?? location.pathname) + query;

      navigate(url);
    },
    [location.pathname, urlSearchParams]
  );

  return {
    values,
    updateSearchParams,
    urlSearchParams,
  };
};

export const usePipelineStateToasts = () => {
  const dispatch = useEnhancedDispatch();

  const { entities } = useEnhancedSelector((state) => state.entity);
  const pipeline = useEnhancedSelector((state) => state.entityPipeline.pipelineTransitioning);

  const previousPipeline = usePreviousValue(pipeline);

  useEffect(() => {
    if (!isEqual(pipeline, previousPipeline) && pipeline.toast) {
      const entity = entities?.find((entity) => entity.id === pipeline.id);

      if (entity) {
        const displayName = entity.displayName;

        switch (pipeline.status) {
          case AppConstants.FETCH_STATUS.SUCCESS: {
            const action = pipeline.type === 'resume' ? tn('started') : tn('stopped');
            message.success(tn('toast_success', { action, displayName }));
            break;
          }
          case AppConstants.FETCH_STATUS.ERROR: {
            const action = pipeline.type === 'resume' ? tn('starting') : tn('stopping');
            message.error(tn('toast_error', { action, displayName }));
            break;
          }
        }
      }

      dispatch(stopTransitioningPipelineToast());
    }
  }, [dispatch, entities, pipeline, previousPipeline]);
};
