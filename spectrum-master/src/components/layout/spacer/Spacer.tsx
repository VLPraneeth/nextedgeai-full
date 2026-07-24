import cx from 'classnames';

import { Spacing } from '../types';

import './Spacer.less';

export interface SpacerPropsStatic {
  /** how wide is this spacer */
  x?: Spacing;
  /** how tall is this spacer */
  y?: Spacing;
}

export interface SpacerPropsFlex {
  flex: true;
}

export type SpacerProps = SpacerPropsStatic | SpacerPropsFlex;

export const Spacer = (props: SpacerProps) => {
  const classNames =
    'flex' in props
      ? 'synri-flex-spacer'
      : cx('synri-spacer', {
          [`synri-spacer-x-${props.x}`]: props.x,
          [`synri-spacer-y-${props.y}`]: props.y,
        });

  return <div className={cx(classNames)} />;
};
