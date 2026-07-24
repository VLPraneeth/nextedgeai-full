//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import ATabs, { TabPaneProps as ATabPaneProps, TabsProps as ATabsProps } from 'antd/lib/tabs';
import cx from 'classnames';
import * as React from 'react';
import './Tabs.less';

const { TabPane: ATabPane } = ATabs;

export type TabsProps = ATabsProps & {
  className?: string;
};

export type TabsComponents = {
  TabPane: React.FC<TabPaneProps>;
  Tab: React.FC<TabProps>;
  children?: React.ReactNode;
};

const Tabs = ({ className, animated = false, children, ...props }: TabsProps & Partial<TabsComponents>) => {
  return (
    <ATabs className={cx('synri-tabs', className)} animated={animated} {...props}>
      {children}
    </ATabs>
  );
};

export type TabPaneProps = ATabPaneProps & {
  tabIconPath?: string;
  tabTooltipTitle?: string;
  activeClassName?: string;
  active?: boolean;
  children?: React.ReactNode;
};

const TabPane = ({ tabIconPath, tabTooltipTitle, activeClassName, className, children, ...props }: TabPaneProps) => {
  const { active } = props;
  return (
    <ATabPane {...props}>
      <div className={cx(className, active && activeClassName)}>{children}</div>
    </ATabPane>
  );
};

export interface TabProps {
  className?: string;
  children?: React.ReactNode;
}

const Tab = ({ className, children }: TabProps) => {
  return <div className={cx('synri-tab', className)}>{children}</div>;
};

Tabs.Tab = Tab;
Tabs.TabPane = TabPane;

export default Tabs;
export { Tab, TabPane };
