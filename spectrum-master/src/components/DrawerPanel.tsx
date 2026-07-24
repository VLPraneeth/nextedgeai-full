//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import Drawer, { DrawerProps } from 'antd/lib/drawer';
import cx from 'classnames';

import { useLayoutContext } from 'pages/LayoutContext';

import './DrawerPanel.less';

export interface DrawerPanelProps extends DrawerProps {
  absolutePositioning?: boolean;
  additionalHeightOffset?: number;
  footer?: React.ReactChild;
  footerClassName?: string;
  height?: number;
  noPadding?: boolean;
  useLandingZone?: boolean;
  width?: 'standard' | 'large' | 'xlarge' | 'full' | number;
  children?: React.ReactNode;
}

const DrawerPanel = ({
  absolutePositioning,
  additionalHeightOffset = 0,
  children,
  className,
  closable = true,
  footer,
  footerClassName,
  height,
  mask = false,
  maskClosable,
  noPadding,
  onClose,
  placement = 'right',
  getContainer = 'body',
  title,
  useLandingZone = false,
  visible,
  width = 'standard',
  ...rest
}: DrawerPanelProps) => {
  const layoutContext = useLayoutContext();

  const heightOffset = calculateHeightOffset(additionalHeightOffset);

  const drawerWidth =
    width === 'standard'
      ? 350
      : width === 'large'
      ? 550
      : width === 'xlarge'
      ? 900
      : width === 'full'
      ? window.innerWidth - layoutContext.dimensions.sider.width
      : width;

  return (
    <div
      className={cx('synri-drawer-panel', className, {
        'synri-drawer-panel--absolute': absolutePositioning,
        'synri-drawer-panel--bottom': placement === 'bottom',
        'synri-drawer-panel--no-body-padding': noPadding,
        'synri-drawer-panel--with-footer-or-header': (!!footer && !title) || (!footer && !!title),
        'synri-drawer-panel--with-footer-and-header': !!footer && !!title,
      })}>
      {useLandingZone ? <DrawerPanelLandingZone visible={visible} /> : null}
      <Drawer
        closable={closable}
        getContainer={false}
        height={height || `calc(100vh - ${heightOffset}px)`}
        mask={mask}
        // maskStyle={{ position: 'absolute', left: '0px' }}
        placement={placement}
        maskClosable={maskClosable}
        onClose={onClose}
        title={title}
        visible={visible}
        width={drawerWidth}
        {...rest}>
        {children}
        {footer && (
          <div data-testid="DrawerPanelFooter" className={cx('synri-drawer-panel__footer', footerClassName)}>
            {footer}
          </div>
        )}
      </Drawer>
    </div>
  );
};

export default DrawerPanel;

interface DrawerPanelLandingZoneProps {
  visible?: boolean;
}

const DrawerPanelLandingZone = ({ visible }: DrawerPanelLandingZoneProps) => {
  return (
    <div
      data-testid="DrawerPanelLandingZone"
      className={cx('synri-drawer-panel__landing-zone', { 'synri-drawer-panel__landing-zone--open': visible })}
    />
  );
};

function calculateHeightOffset(additionalOffset = 0) {
  const mainHeaderHeight = 52;
  const topBannerHeight = 36;

  let heightOffset = mainHeaderHeight + additionalOffset;

  const numberOfBannersOnPage = document.querySelectorAll('.top-banner').length;

  for (let i = 0; i < numberOfBannersOnPage; i++) {
    heightOffset += topBannerHeight;
  }

  return heightOffset;
}
