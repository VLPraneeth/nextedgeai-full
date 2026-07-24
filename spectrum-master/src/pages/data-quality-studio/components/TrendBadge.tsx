import Icon from 'antd/lib/icon';
import cx from 'classnames';

import './TrendBadge.less';

import { HStack } from 'components/layout';

export interface TrendBadgeProps {
  trendDirection: 'up' | 'neutral' | 'down';
  children: string;
}

const TrendBadge = ({ trendDirection = 'neutral', children }: TrendBadgeProps) => {
  return (
    <div className={cx('trend-badge', `trend-badge-${trendDirection}`)}>
      <HStack spacing="sm">
        <Icon
          data-testid={`trend-badge-${trendDirection}`}
          type={trendDirection === 'up' ? 'arrow-up' : trendDirection === 'down' ? 'arrow-down' : 'minus'}
        />
        <span>{children}</span>
      </HStack>
    </div>
  );
};

export default TrendBadge;
