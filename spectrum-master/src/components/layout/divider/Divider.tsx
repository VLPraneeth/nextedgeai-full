import cx from 'classnames';

import { Spacing } from '../types';

import './Divider.less';

interface DividerProps {
  className?: string;

  /** vertical spacing around divider */
  y?: Spacing;
}

export const Divider = ({ y = 'md', className }: DividerProps) => {
  return (
    <div className={cx('synri-divider', `synri-divider-${y}`, className)}>
      <div className="divider" />
    </div>
  );
};
