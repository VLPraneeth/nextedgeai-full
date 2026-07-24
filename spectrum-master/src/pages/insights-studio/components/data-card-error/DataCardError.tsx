import { Tooltip } from 'antd';
import { ReactNode } from 'react';
import { animated, useSpring } from 'react-spring';

import { ReactComponent as WarningSign } from 'assets/icons/warning-sign.svg';
import { DataCardErrorMessage } from 'store/insights-studio/types';
import { tNamespaced } from 'utils/i18nUtil';

import './DataCardError.less';

const tn = tNamespaced('DataCardError');

export interface DataCardErrorProps {
  error?: DataCardErrorMessage;
  tooltip?: string;
  icon?: ReactNode;
}

export const DataCardError = ({ error, tooltip, icon = <WarningSign width={16} /> }: DataCardErrorProps) => {
  const fadeInCardContent = useSpring({
    from: { opacity: 0, transform: 'scale(0.95)' },
    to: { opacity: 1, transform: 'scale(1)' },
  });

  const title = error?.title ?? tn('something_went_wrong');
  const body = error?.body ?? tn('error_getting_data');

  return (
    <animated.div style={fadeInCardContent} className="data-card-error" data-testid="data-card-error">
      <div className="data-card-error__title">
        <Tooltip title={tooltip}>
          <div className="data-card-error__title__icon">{icon}</div>
        </Tooltip>
        {title}
      </div>
      <div className="data-card-error__body">{body}</div>
    </animated.div>
  );
};
