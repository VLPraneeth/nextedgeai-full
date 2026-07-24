import { useEffect, useState } from 'react';

import usePreviousValue from 'hooks/usePreviousValue';

/**
 * Helper hook to keep track of current and previous values with strict equality check on the desired value.
 * @param value value that we are tracking for changes
 * @param desiredValue desired value or a function for custom equality check.
 *                     Passed parameters are the current and previous value.
 * @param stableFn invoked when the the desired value is true.
 * @param reinitialize reinitialize the fired state
 */
const useEffectForValue = <T extends string | number | boolean | undefined | null | object>(
  value: T,
  desiredValue: T | ((value?: T, previousValue?: T) => boolean | undefined | null),
  stableFn: () => void,
  reinitialize = false
) => {
  const [fnFired, setFnFired] = useState(false);
  const previousValue = usePreviousValue<T>(value);

  useEffect(() => {
    if (
      typeof value === 'undefined' ||
      typeof previousValue === 'undefined' ||
      previousValue === value ||
      (typeof desiredValue === 'function' ? !desiredValue(value, previousValue) : value !== desiredValue) ||
      fnFired
    ) {
      return;
    }

    stableFn();
    setFnFired(true);
  }, [fnFired, value, desiredValue, previousValue, stableFn]);

  useEffect(() => {
    if (fnFired && reinitialize && value !== desiredValue) {
      setFnFired(false);
    }
  }, [fnFired, reinitialize, value, desiredValue]);
};

export default useEffectForValue;
