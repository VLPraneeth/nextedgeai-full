import { throttle } from 'lodash';
import React, { ReactNode, useCallback, useLayoutEffect, useState } from 'react';

interface ScrollableAreaProps {
  bottomOffset?: number;
  children: ReactNode;
  className?: string;
  collapse?: boolean;
  scrollable?: boolean;
}

/**
 * Component that calculates it's own height and creates a scrollable area within.
 * @param {number} bottomOffset Number of pixels by which to reduce height (simulates a bottom margin). Default is `8`
 * @param {string} className additional classes that will be added to the container
 * @param {boolean} collapse If true, collapses to inner content's height until the inner content exceeds it's maximum height. Default is `false`.
 * @param {boolean} scrollable If false, the overflow will be hidden. Default is `true`.
 */
export const ScrollableArea = ({
  bottomOffset = 8,
  children,
  className,
  collapse,
  scrollable = true,
}: ScrollableAreaProps) => {
  const [columnHeight, setColumnHeight] = useState<'auto' | number>('auto');
  const scrollerRef = React.createRef<HTMLDivElement>();

  const calculateColumnHeight = useCallback(() => {
    if (!scrollerRef.current) {
      return;
    }

    const viewportHeight = window.innerHeight;
    const topOfScroller = scrollerRef.current.getBoundingClientRect().top;

    const columnHeight = viewportHeight - topOfScroller - bottomOffset;

    setColumnHeight(columnHeight);
  }, [scrollerRef, bottomOffset]);

  const delayedCalculateHeight = throttle(calculateColumnHeight, 500);

  useLayoutEffect(() => {
    // calc column's initial height
    delayedCalculateHeight();

    // set event listener to handle updates on resize
    window.addEventListener('resize', delayedCalculateHeight);

    // cleanup event listener on unmount
    return () => window.removeEventListener('resize', delayedCalculateHeight);
  }, [delayedCalculateHeight]);

  return (
    <div
      className={className}
      data-component-name="ScrollableArea"
      ref={scrollerRef}
      style={{
        height: collapse ? 'initial' : columnHeight,
        maxHeight: columnHeight,
        overflowY: scrollable ? 'auto' : 'hidden',
      }}>
      {children}
    </div>
  );
};
