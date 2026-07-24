import Layout, { LayoutProps } from 'antd/lib/layout';
import cx from 'classnames';
import { useEffect } from 'react';

import useDimensions from 'hooks/useDimensions';

import { useLayoutContext } from './LayoutContext';

const { Content } = Layout;

const MainContent = ({ className, children }: LayoutProps) => {
  const [measurementRef, dimensions] = useDimensions({ liveMeasure: true });
  const { updateDimensions } = useLayoutContext();

  useEffect(() => {
    updateDimensions('content', dimensions);
  }, [dimensions, updateDimensions]);

  return (
    <div className={cx('main-content-measurement-wrapper', 'main-content')} ref={measurementRef}>
      <Content className={className}>
        <div className="main-page-layout-contents">{children}</div>
      </Content>
    </div>
  );
};

export default MainContent;
