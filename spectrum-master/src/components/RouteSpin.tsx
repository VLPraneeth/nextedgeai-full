import { Spin } from 'antd';
import * as React from 'react';

import { tNamespaced } from 'utils/i18nUtil';

import './RouteSpin.less';

const t = tNamespaced('Common');

interface RouteSpinProps {
  /* string to show on the Spinner */
  title?: string | React.ReactNode;
  /* delay showing the spinner for ms. This reduces flashes and screen flushes for short loads */
  delayMs?: number;
  children?: React.ReactNode;
}

// useful fallback when loading the bundle for a route using React.Suspense
const RouteSpin = ({ title = t('loading'), delayMs = 200, children }: RouteSpinProps) => {
  return (
    <div className="route-spinner-container">
      <Spin spinning delay={delayMs} />
      {title && <div className="route-spinner-title">{title}</div>}
      {children && <div className="route-spinner-content">{children}</div>}
    </div>
  );
};

export default RouteSpin;
