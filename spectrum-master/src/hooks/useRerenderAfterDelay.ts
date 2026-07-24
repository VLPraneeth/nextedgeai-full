// This hooks returns false until the delay has passed and then returns true.
// This is useful if we want to delay fetching data after mount after a period
// of time. The hook will cause a rerender after the delay is complete.

import { useEffect, useState } from 'react';

const useRerenderAfterDelay = (delay: number) => {
  const [delayIsComplete, setDelayIsComplete] = useState(false);
  useEffect(() => {
    setTimeout(() => setDelayIsComplete(true), delay);
  }, [delay]);

  return delayIsComplete;
};

export default useRerenderAfterDelay;
