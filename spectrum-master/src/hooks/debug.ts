import { useEffect, useRef } from 'react';

export function useWhyDidYouUpdate<T extends Record<string, any>>(name: string, props: T) {
  // Get a mutable ref object where we can store props ...
  // ... for comparison next time this hook runs.
  const previousProps = useRef<T>();

  useEffect(() => {
    if (previousProps.current) {
      // Get all keys from previous and current props
      const allKeys = Object.keys({ ...previousProps.current, ...props });

      // Use this object to keep track of changed props
      const changesObj: Partial<T> = {};

      // Iterate through keys
      allKeys.forEach((key) => {
        // If previous is different from current

        if (previousProps?.current && previousProps.current[key] !== props[key]) {
          // not sure why I can't index a partial Record<string, any> with string 🤔
          // @ts-ignore
          changesObj[key] = {
            from: previousProps.current[key],
            to: props[key],
          };
        }
      });

      if (Object.keys(changesObj).length) {
        console.log('[why-did-you-update]', name, changesObj);
      }
    }

    previousProps.current = props;
  });
}
