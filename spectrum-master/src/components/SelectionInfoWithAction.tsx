import cx from 'classnames';
import { animated } from 'react-spring';

import { Text } from './typography';

import './SelectionInfoWithAction.less';

export interface SelectionInfoWithActionProps {
  action?: () => void;
  actionText?: string;
  selectionText: string;
  style?: any;
  className?: string;
}

export const SelectionInfoWithAction = ({
  action,
  actionText,
  selectionText,
  style,
  className,
}: SelectionInfoWithActionProps) => {
  return (
    <animated.div style={style}>
      <div className={cx('selection-info-with-action', className)}>
        <Text className="selection-info-with-action__text">{selectionText}</Text>
        {action && actionText && (
          <a onClick={action}>
            <Text>{actionText}</Text>
          </a>
        )}
      </div>
    </animated.div>
  );
};
