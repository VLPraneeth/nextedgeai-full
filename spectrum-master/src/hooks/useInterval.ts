import { useEffect, useRef } from 'react';

type IntervalCallbackFn = () => void;

/**
 * useInterval will let you trigger a callback on a given interval.
 * When delay is updated, the interval is properly cleared and re-set.
 */
const useInterval = (callback: IntervalCallbackFn, delay: number) => {
  const savedCallback = useRef<IntervalCallbackFn>();

  useEffect(() => {
    savedCallback.current = callback;
  }, [callback]);

  useEffect(() => {
    if (savedCallback.current && delay !== null) {
      const id = setInterval(savedCallback.current, delay);

      return () => clearInterval(id);
    }
  }, [delay]);
};

export default useInterval;
