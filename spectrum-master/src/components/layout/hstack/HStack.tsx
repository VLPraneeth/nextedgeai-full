import cx from 'classnames';
import * as React from 'react';

import { Spacer } from '../';
import { Alignment, Justification, Spacing } from '../types';

import './HStack.less';

export interface HStackProps {
  /** className to add to the HStack container */
  className?: string;

  /** space between items */
  spacing?: Spacing;

  /** controls vertical alignment in the stack (flex align-items) */
  align?: Alignment;

  /** justification/alignment of items: stack at the start, in the center, at the end */
  justify?: Justification;

  /** flex-grow */
  grow?: boolean;

  /** flex-shrink */
  shrink?: boolean;

  children?: React.ReactNode;
}

export const HStack = ({
  spacing = 'md',
  align = 'center',
  justify = 'start',
  grow = false,
  shrink = false,
  className,
  children,
}: HStackProps) => {
  const wrapperClassName = cx(
    'synri-h-stack',
    justify && `justify-${justify}`,
    align && `align-${align}`,
    grow && 'flex-grow',
    shrink && 'flex-shrink',
    className
  );

  // if no spacing, just return children
  if (spacing === 'z') {
    return <div className={wrapperClassName}>{children}</div>;
  }

  return (
    <div className={wrapperClassName}>
      {React.Children.toArray(children)
        .filter(Boolean)
        .map((child, idx) => {
          if (!React.isValidElement(child)) {
            return <React.Fragment key={idx}>{child}</React.Fragment>;
          }

          // if there isn't a key provided, then we'll use the current array idx
          const childKey = child?.key || idx;

          // no reason to clone the child if the key already exists
          const newChild = child.key !== childKey ? React.cloneElement(child, { key: childKey }) : child;

          // only render spacers if this isn't the first item
          if (idx > 0) {
            return [<Spacer key={`${childKey}-divider`} x={spacing} />, newChild];
          }

          return newChild;
        })}
    </div>
  );
};
