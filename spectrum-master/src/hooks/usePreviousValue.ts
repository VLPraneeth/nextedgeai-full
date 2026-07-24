import { useEffect, useRef } from 'react';

/**
 * used to store the previous value of the render,
 * useful if you absolutely need to compare the prevProp
 * to the current prop. There are almost always better/more
 * declarative patterns that can be used instead of
 * comparing values but it can be useful in legacy
 * situations when updating to FC from Class components
 */
function usePreviousValue<T>(value?: T): T | undefined {
  const previousValue = useRef<T>();

  useEffect(() => {
    previousValue.current = value;
  });

  return previousValue.current;
}

export default usePreviousValue;
