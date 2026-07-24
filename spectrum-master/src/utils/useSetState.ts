import { useMemo, useState } from 'react';

// This hook can be helpful if you want to combine multiple pieces of related
// state into a single setState. You can update just a single field on state and
// you can reset the entire state at once which is helpful when reseting a
// component.
const useSetState = <T>(initialState: T | (() => T)): [T, (newState: Partial<T> | ((prevState: T) => T)) => void] => {
  const [state, regularSetState] = useState<T>(initialState);

  const setState = useMemo(
    () => (newState: Partial<T> | ((prevState: T) => T)) => {
      if (typeof newState === 'function') {
        regularSetState(newState);
      } else {
        regularSetState((prevState) => ({
          ...prevState,
          ...newState,
        }));
      }
    },
    []
  );

  return [state, setState];
};
export default useSetState;
