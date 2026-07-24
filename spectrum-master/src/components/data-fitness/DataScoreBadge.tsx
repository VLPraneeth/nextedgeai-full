import cx from 'classnames';
import { useMemo } from 'react';

import { gaugeSegments, getSegmentForValue } from 'store/datascore';
import { getTextColorForBackgroundColor } from 'utils/ColorUtil';

import './DataScoreBadge.less';

export interface DataScoreBadgeProps {
  children?: string;
  className?: string;
  fontColorThreshold?: number;
  score: number;
}

const DataScoreBadge = ({ className, score, children, fontColorThreshold }: DataScoreBadgeProps) => {
  const { color: backgroundColor } = getSegmentForValue(gaugeSegments, score);
  const color = getTextColorForBackgroundColor(backgroundColor, fontColorThreshold);

  const style = useMemo(
    () => ({
      backgroundColor,
      color,
    }),
    [backgroundColor, color]
  );

  return (
    <div className={cx('synri-datascore-badge', className)} style={style}>
      {typeof children !== 'undefined' ? children : score}
    </div>
  );
};

export default DataScoreBadge;
