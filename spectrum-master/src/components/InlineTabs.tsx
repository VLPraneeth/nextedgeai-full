import cx from 'classnames';
import * as React from 'react';
import { useContext } from 'react';

import './InlineTabs.less';
import { HStack } from './layout';

export type InlineTabsContextShape = {
  selectedTab: string;
  onChange: (key: string) => void;
};

const InlineTabsContext = React.createContext<InlineTabsContextShape>({
  selectedTab: '',
  onChange: (key: string) => {
    /* noop */
  },
});

export type InlineTabsProps = {
  selectedTab: string;
  onChange: (key: string) => void;
  className?: string;
  children?: React.ReactNode;
};

const InlineTabs = ({ children, className, selectedTab, onChange }: InlineTabsProps) => {
  return (
    <InlineTabsContext.Provider value={{ selectedTab, onChange }}>
      <div className={cx(className, 'synri-inline-tabs-container')}>
        <HStack spacing="z">{children}</HStack>
      </div>
    </InlineTabsContext.Provider>
  );
};

const useTab = (id: string) => {
  const { selectedTab, onChange } = useContext(InlineTabsContext);

  return {
    isCurrent: selectedTab === id,
    activateTab: () => onChange(id),
  };
};

type InlineTabProps = {
  id: string;
  className?: string;
  children: string | React.ReactNode;
};

const InlineTab = ({ id, className, children }: InlineTabProps) => {
  const { isCurrent, activateTab } = useTab(id);

  return (
    <div className={cx(className, 'synri-inline-tab', isCurrent && 'is-current')} role="button" onClick={activateTab}>
      {children}
    </div>
  );
};

export { InlineTab, InlineTabs, useTab };
