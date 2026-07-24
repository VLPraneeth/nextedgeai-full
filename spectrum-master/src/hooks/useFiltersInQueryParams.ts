import { omitBy, some } from 'lodash';
import { isMoment } from 'moment';
import { useCallback, useEffect, useMemo, useState } from 'react';

import { MomentDateRange, deserializeMomentFields, serializeMomentFields } from 'utils/DateUtil';

import useUserLocalMoment from './moment';
import useQueryParams from './useQueryParams';

export const useQueryFilterValues = <T extends MomentDateRange>(defaultParamsPartial: Partial<T>) => {
  const moment = useUserLocalMoment();

  const [queryParams] = useQueryParams<T>();

  // Add the default start date and end date to the default params
  const [defaultFilters] = useState({
    startDate: moment().subtract(7, 'days').startOf('day'),
    endDate: moment().endOf('day'),
    ...defaultParamsPartial,
  } as T);

  const filterValues = useMemo(() => {
    const memoizedFilterValues = {} as T;
    (Object.keys(defaultFilters) as (keyof T)[]).forEach((key) => {
      if (queryParams[key]) {
        if (key === 'startDate' || key === 'endDate') {
          // @ts-ignore
          memoizedFilterValues[key] = moment(queryParams[key]);
        } else {
          // @ts-ignore
          memoizedFilterValues[key] = queryParams[key];
        }
      } else {
        memoizedFilterValues[key] = (defaultFilters as any)[key];
      }
    });
    return memoizedFilterValues;
  }, [defaultFilters, moment, queryParams]);

  const filterIsActive = useMemo(() => {
    return some(filterValues, (val, key) => {
      const defaultValue = (defaultFilters as any)[key];
      if (isMoment(val)) {
        return !val.isSame(defaultValue);
      }
      return val !== defaultValue;
    });
  }, [defaultFilters, filterValues]);

  return { filterValues, defaultFilters, filterIsActive };
};

// This hook handles updating the query params for a table filter. It generates
// the default start and end dates. Other default inputs can be passed to it.

// Pass the default values to this hook and the hook will generate the initial
// values based on a combination of the query params and the default values.
// Only changes that don't match the default values will be added to the query
// parameters.

// Accepts a callback that is invoked when the query parameters change. This
// allows us to refresh the data when navigating back and forth in the browser.
const useFiltersInQueryParams = <T extends MomentDateRange>(
  defaultParamsPartial: Partial<T>,
  onParamChange: (newParams: Partial<T>) => void
) => {
  const [queryParams, updateQueryParamsFull] = useQueryParams<T>();

  const { filterValues, defaultFilters, filterIsActive } = useQueryFilterValues(defaultParamsPartial);

  useEffect(() => {
    const updatedParams = { ...defaultFilters, ...queryParams };
    onParamChange(deserializeMomentFields(updatedParams as any));
  }, [defaultFilters, onParamChange, queryParams]);

  const updateQueryParams = useCallback(
    (newParams: Partial<T>) => {
      const paramsToUpdate = omitBy(newParams, (val, key) => {
        if (!val) {
          return true;
        }

        if (['startDate', 'endDate'].includes(key)) {
          // @ts-ignore
          return defaultFilters[key].isSame(val);
        } else {
          // @ts-ignore
          return defaultFilters[key] === val;
        }
      }) as Partial<T>;

      onParamChange({ ...defaultFilters, ...paramsToUpdate });

      const serializedParams = serializeMomentFields<Partial<T>>(paramsToUpdate);
      updateQueryParamsFull(serializedParams as Record<string, string>);
    },
    [defaultFilters, onParamChange, updateQueryParamsFull]
  );

  const resetToDefaultFilters = useCallback(() => {
    updateQueryParams(defaultFilters);
  }, [defaultFilters, updateQueryParams]);

  return {
    defaultFilters,
    filterValues,
    updateQueryParams,
    resetToDefaultFilters,
    filterIsActive,
  };
};

export default useFiltersInQueryParams;
