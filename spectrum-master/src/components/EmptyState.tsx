import cx from 'classnames';
import * as React from 'react';

import InlineSvg from 'components/icons/InlineSvg';
import { HStack, Stack } from 'components/layout';

import './EmptyState.less';

const defaultEmptyStateIcon = '';

export interface EmptyStateProps {
  className?: string;
  title?: string;
  description?: string;
  icon?: string;
  iconComponent?: React.ReactNode;
}

const EmptyState = ({
  title = '',
  description,
  icon = defaultEmptyStateIcon,
  iconComponent,
  className,
}: EmptyStateProps) => {
  const iconNode = icon ? (
    <div className="empty-state-icon-container">
      <InlineSvg title={title} src={icon} />
    </div>
  ) : (
    iconComponent
  );

  return (
    <div className={cx('empty-state-container', className)}>
      <Stack fill className="empty-state-stack">
        <HStack spacing="xs">
          {iconNode}
          <div className="empty-state-title">{title}</div>
        </HStack>
        <div className="empty-state-description">{description}</div>
      </Stack>
    </div>
  );
};

export default EmptyState;
