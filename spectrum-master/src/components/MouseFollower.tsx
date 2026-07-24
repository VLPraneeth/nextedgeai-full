import cx from 'classnames';
import { useEffect, useRef } from 'react';
import * as React from 'react';

import { createPortal } from 'utils/PortalUtils';

import './MouseFollower.less';

export type MouseFollowerOffset = [number, number];

export type MouseFollowerProps = {
  children: React.ReactNode;
  className?: string;

  /** ID of this mouse follower group. This is used to mount a container in the DOM,
   * it's useful to keep this ID the same for each "group" of mouse follower uses so that they
   * don't conflict with each other.
   */
  id?: string;
  /**
   * Tuple describing the X, Y offset away from the mouse cursor for the MouseFollower children.
   * Increasing X will shift the content right.
   * Increasing Y will shift the content down.
   */
  offset?: MouseFollowerOffset;
};

export const DEFAULT_OFFSET: MouseFollowerOffset = [10, 10];

const MouseFollower = ({ children, className, id = 'mouseFollower', offset = DEFAULT_OFFSET }: MouseFollowerProps) => {
  const ref = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    const el = ref.current;
    const [xOffset, yOffset] = offset;

    if (el) {
      // modify style imperatively outside of React vs declaratively updating an inline
      // style object for better performance
      const updatePosition = (evt: MouseEvent) => {
        el.style.left = `${evt.pageX + xOffset}px`;
        el.style.top = `${evt.pageY + yOffset}px`;
      };

      document.addEventListener('mousemove', updatePosition);

      return () => {
        document.removeEventListener('mousemove', updatePosition);
      };
    }
  }, [offset]);

  return createPortal(
    <div className={cx('mouse-follower', className)} ref={ref}>
      {children}
    </div>,
    id
  );
};

export default MouseFollower;
