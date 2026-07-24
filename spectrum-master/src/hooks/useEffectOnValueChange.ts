import { DependencyList, useEffect } from 'react';

import usePreviousValue from './usePreviousValue';

/**
 * Just like useEffect but the dependency array does not have to include all
 * variables the callback references. The useEffect is called every render so
 * the function always has the latest references. This can be useful if a
 * function is not stable and you only want it to run when specific variables
 * change.
 */

const useEffectOnValueChange = (fn: (previousWatchers?: DependencyList) => void, watchers: DependencyList) => {
  const previousWatchers = usePreviousValue(watchers);
  const watchersChanged = previousWatchers
    ? previousWatchers.some((prevDep, index) => prevDep !== watchers[index])
    : true;

  useEffect(() => {
    if (watchersChanged) {
      return fn(previousWatchers);
    }
  });
};

export default useEffectOnValueChange;
