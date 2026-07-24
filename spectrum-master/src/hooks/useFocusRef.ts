import { useCallback, useRef } from 'react';

export interface UseFocusOptions {
  autoFocus?: boolean;
}

export const useFocusRef = (params: UseFocusOptions = { autoFocus: false }) => {
  const element = useRef<HTMLElement | null>(null);

  // ref callback pattern: https://reactjs.org/docs/refs-and-the-dom.html#callback-refs
  const refCallback = useCallback(
    (node: HTMLElement | null) => {
      element.current = node;
      // antd drawer pulls focus tot he title when opened, setTimeout allows us
      // to set focus somewhere else after that so
      setTimeout(() => {
        if (params.autoFocus) {
          element.current?.focus();
        }
      });
    },
    [params.autoFocus]
  );

  return { refCallback, element };
};
