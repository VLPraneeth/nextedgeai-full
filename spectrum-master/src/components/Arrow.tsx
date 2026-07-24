import * as React from 'react';
import { useMemo } from 'react';

import { variables } from 'utils/LessConstants';

export enum Direction {
  UP,
  DOWN,
  LEFT,
  RIGHT,
}

const makeBorders = ({ color, size }: Required<Omit<ArrowProps, 'className' | 'direction'>>) => {
  const makeBorder = (side: 'Top' | 'Bottom' | 'Left' | 'Right', color: ArrowProps['color'] = 'transparent') => ({
    [`border${side}Color`]: color,
    [`border${side}Style`]: 'solid',
    [`border${side}Width`]: size,
  });

  return {
    ...makeBorder('Left'),
    ...makeBorder('Right'),
    ...makeBorder('Bottom', color),
  };
};

const getRotationForDirection = (direction: Direction) => {
  switch (direction) {
    case Direction.UP:
      return 0;
    case Direction.DOWN:
      return 180;
    case Direction.LEFT:
      return 270;
    case Direction.RIGHT:
      return 90;
  }
};

export interface ArrowProps {
  className?: string;
  color?: React.CSSProperties['color'];
  size?: React.CSSProperties['height'];
  direction?: Direction;
}

const Arrow = ({ className, color = 'black', size = '1rem', direction = Direction.RIGHT }: ArrowProps) => {
  const arrowStyle = useMemo(
    () => ({
      display: 'inline-block',
      height: 0,
      width: 0,
      transform: `rotateZ(${getRotationForDirection(direction)}deg)`,
      transition: `all .2s ${variables.easings.easeInOut}`,
      ...makeBorders({ color, size }),
    }),
    [color, size, direction]
  );

  return <span className={className} style={arrowStyle} />;
};

export default Arrow;
