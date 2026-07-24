import cx from 'classnames';
import * as React from 'react';

import { Divider, Spacer } from '../';
import { Spacing } from '../types';

import './Stack.less';

export interface StackProps {
  /**
   * CSS class name added to the container
   */
  className?: string;
  /**
   * space between items
   * @default "md"
   */
  spacing?: Spacing;
  /**
   * Show divider between items
   * @default false
   */
  divider?: boolean;
  /**
   * Fill up container
   * @default false
   */
  fill?: boolean;
  /** Allow scrolling overflow
   * @default false
   */
  scrollOverflow?: boolean;
  /** center the content
   * @default false
   * */
  center?: boolean;
  /** style */
  style?: React.CSSProperties;

  children?: React.ReactNode;
}

export const Stack = ({
  center = false,
  children,
  className,
  divider = false,
  fill = false,
  scrollOverflow = false,
  spacing = 'md',
  style,
}: StackProps) => {
  // if no spacing and no divider, just return children
  if (spacing === 'z' && !divider) {
    return <>{children}</>;
  }

  const SpacerComponent = divider ? Divider : Spacer;

  return (
    <div
      className={cx('synri-stack', className, {
        'stack-fill-parent': fill,
        'stack-scroll-overflow': scrollOverflow,
        'stack-center-content': center,
      })}
      style={style}>
      {React.Children.toArray(children)
        .filter(Boolean)
        .map((child, idx) => {
          if (!React.isValidElement(child)) {
            return <>{child}</>;
          }

          // if there isn't a key provided, then we'll use the current array idx
          const childKey = child?.key || idx;

          // no reason to clone the child if the key already exists
          const newChild = child.key !== childKey ? React.cloneElement(child, { key: childKey }) : child;

          // only render spacers/dividers if this isn't the first item
          if (idx > 0) {
            return [<SpacerComponent key={`${childKey}-divider`} y={spacing} />, newChild];
          }

          return newChild;
        })}
    </div>
  );
};
