//
// NextEdge AI private application.
//
import cx from 'classnames';
import { ReactNode } from 'react';

import Logo from 'assets/images/Logo.svg';
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
        <img className="logo" src={Logo} alt="NextEdge AI" />
      </div>
      <div className="authentication-content">{children}</div>
      {footer}
    </CenterLayout>
  );
}

export default AuthenticationWrapper;
