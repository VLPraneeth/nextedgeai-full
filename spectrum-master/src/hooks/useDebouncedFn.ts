import debounce from 'lodash/debounce';
import { useRef, useEffect } from 'react';

type FnToDebounce = Parameters<typeof debounce>[0];

/** Debounce a function for debounceMs. This will automatically cancel
 * any lingering debounced fn calls when the component unmounts.
 */
const useDebouncedFn = (
  fn: FnToDebounce,
  debounceMs?: Parameters<typeof debounce>[1],
  options?: Parameters<typeof debounce>[2]
) => {
  const debouncedFn = useRef(debounce(fn, debounceMs, options));

  useEffect(() => {
    if (debouncedFn.current) {
      const _fn = debouncedFn.current;

      // cancel any lingering fn calls on unmount
      return () => _fn.cancel();
    }

    // only run on mount/unmount
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return debouncedFn.current;
};

export default useDebouncedFn;
