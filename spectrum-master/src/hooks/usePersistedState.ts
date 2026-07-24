import { useState } from 'react';

function usePersistedState<T>(key: string, defaultValue: T): [T, (value: T) => void] {
  const [value, setState] = useState<T>(() => {
    try {
      const item = localStorage.getItem(key);

      return item ? JSON.parse(item) : defaultValue;
    } catch (error) {
      console.error('Issue setting state in useStateHook:', error);
      return defaultValue;
    }
  });

  const setValue = (value: T) => {
    try {
      setState(value);
      localStorage.setItem(key, JSON.stringify(value));
    } catch (error) {
      console.log('Error setting value in SetValue function:', error);
    }
  };

  return [value, setValue];
}

export default usePersistedState;
