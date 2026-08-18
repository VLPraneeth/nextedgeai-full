//
// NextEdge AI private application.
//
import cx from 'classnames';
import { ReactNode } from 'react';

import BrandLogo from 'components/brand/BrandLogo';
import CenterLayout from 'components/layout/CenterLayout';

import 'antd/dist/antd.css';
import './AuthenticationWrapper.less';

type AuthenticationProps = {
  className: string;
  children: ReactNode;
  footer?: string | ReactNode;
};

function AuthenticationWrapper({ className, children, footer }: AuthenticationProps) {
  const cls = cx('authentication-wrapper', className);

  return (
    <CenterLayout className={cls}>
      <div className="logo-container">
        <BrandLogo className="logo" />
      </div>
      <div className="authentication-content">{children}</div>
      {footer}
    </CenterLayout>
  );
}

export default AuthenticationWrapper;
