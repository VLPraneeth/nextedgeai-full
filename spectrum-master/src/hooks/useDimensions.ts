import elementResizeDetectorFactory from 'element-resize-detector';
import { useState, useCallback, useLayoutEffect } from 'react';
import * as React from 'react';

const resizeDetector = elementResizeDetectorFactory({ callOnAdd: true, strategy: 'scroll' });

export interface ElementDimensions {
  bottom: number;
  height: number;
  left: number;
  right: number;
  top: number;
  width: number;
  x: number;
  y: number;
}

export const EmptyElementDimensions = {
  bottom: 0,
  height: 0,
  left: 0,
  right: 0,
  top: 0,
  width: 0,
  x: 0,
  y: 0,
};

const getDimensionObject = <T extends HTMLElement>(node: T): ElementDimensions => {
  const rect = node.getBoundingClientRect();

  return {
    bottom: rect.bottom,
    height: rect.height,
    left: rect.left,
    right: rect.right,
    top: rect.top,
    width: rect.width,
    x: rect?.x || rect.left,
    y: rect?.y || rect.top,
  };
};

interface UseDimensionsConfig {
  /** liveMeasure will subscribe the component to resize events for the underlying DOM node and
   * will update the dimensions returned by the hook */
  liveMeasure?: boolean;
}

function useDimensions<T extends HTMLElement = HTMLDivElement>({ liveMeasure = false }: UseDimensionsConfig = {}): [
  React.RefCallback<T>,
  ElementDimensions,
  T | null,
  () => ElementDimensions
] {
  const [dimensions, setDimensions] = useState<ElementDimensions>(EmptyElementDimensions);
  const [node, setNode] = useState<T | null>(null);

  const ref = useCallback((node: T) => {
    setNode(node);
  }, []);

  useLayoutEffect(() => {
    if (node) {
      const measure = (n: HTMLElement) => setDimensions(getDimensionObject(n));

      measure(node);

      if (liveMeasure) {
        // Attach ResizeObverver for constant updates
        resizeDetector.listenTo(node, measure);

        return () => {
          resizeDetector.removeListener(node, measure);
        };
      } else {
        // Just measure one time, but make sure to clean up if we get unmounted
        const animationFrameId = requestAnimationFrame(() => measure(node));

        return () => {
          if (animationFrameId) {
            cancelAnimationFrame(animationFrameId);
          }
        };
      }
    }
  }, [liveMeasure, node]);

  const remeasure = useCallback(() => {
    const newDimensions = node ? getDimensionObject(node as T) : EmptyElementDimensions;
    setDimensions(newDimensions);
    return newDimensions;
  }, [node]);

  return [ref, dimensions, node, remeasure];
}

export default useDimensions;
