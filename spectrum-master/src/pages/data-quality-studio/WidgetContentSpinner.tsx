import Spin, { SpinProps } from 'antd/lib/spin';
import * as React from 'react';

const WidgetContentSpinner = ({ children, spinning = true, ...props }: SpinProps & { children?: React.ReactNode }) => (
  <div className="widget-content-spinner-container">
    <Spin spinning={spinning} {...props}>
      {children}
    </Spin>
  </div>
);

export default WidgetContentSpinner;
