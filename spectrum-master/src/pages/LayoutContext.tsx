import * as React from 'react';
import { useCallback, useContext, useState } from 'react';

import { ElementDimensions, EmptyElementDimensions } from 'hooks/useDimensions';

const emptyLayoutDimensions = {
  header: EmptyElementDimensions,
  content: EmptyElementDimensions,
  sider: EmptyElementDimensions,
};

// Layout context constants
const constants = {
  CONTENT_PADDING: 64,
} as const;

const LayoutContext = React.createContext({
  dimensions: emptyLayoutDimensions,
  constants,
  updateDimensions: (key: keyof LayoutDimensionsShape, dimensions: ElementDimensions) => {},
});
export const useLayoutContext = () => useContext(LayoutContext);

interface LayoutDimensionsShape {
  header: ElementDimensions;
  content: ElementDimensions;
  sider: ElementDimensions;
}

export const LayoutContextProvider = ({ children }: { children?: React.ReactNode }) => {
  const [dimensions, setDimensions] = useState<LayoutDimensionsShape>(emptyLayoutDimensions);
  const updateDimensions = useCallback(
    (key: keyof LayoutDimensionsShape, dimensions: ElementDimensions) =>
      setDimensions((prev) => ({
        ...prev,
        [key]: dimensions,
      })),
    []
  );

  return (
    <LayoutContext.Provider
      value={{
        dimensions,
        constants,
        updateDimensions,
      }}>
      {children}
    </LayoutContext.Provider>
  );
};
