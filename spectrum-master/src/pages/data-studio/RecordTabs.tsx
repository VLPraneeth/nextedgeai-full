import { Link } from '@reach/router';
import cx from 'classnames';
import * as React from 'react';

import { HStack } from 'components/layout';

import './RecordTabs.less';

const Tabs = ({ children }: { children?: React.ReactNode }) => {
  return (
    <HStack className="record-tabs" spacing="sm">
      {children}
    </HStack>
  );
};

interface TabProps {
  icon: React.ReactNode;
  title: string;
  badge?: React.ReactNode;
  url: string;
}

const Tab = ({ icon, title, badge, url }: TabProps) => {
  return (
    <Link
      to={url}
      getProps={({ isCurrent }) => ({
        className: cx('record-tab', {
          active: isCurrent,
        }),
      })}>
      <HStack spacing="xs" justify="center">
        <span className="record-tab-icon">{icon}</span>
        <span className="record-tab-title">{title}</span>
        {badge && <span>{badge}</span>}
      </HStack>
    </Link>
  );
};

export { Tab, Tabs };
