import cx from 'classnames';
import * as React from 'react';

import { HStack } from 'components/layout';

import './StatBlock.less';

export interface StatBlockProps {
  value: React.ReactNode;
  label?: React.ReactNode;
  valueClassName?: string;
}

const StatBlock = ({ value, label, valueClassName }: StatBlockProps) => (
  <HStack spacing="sm">
    <span className={cx('synri-stat-block-value', valueClassName)}>{value}</span>
    {label && <span className="synri-stat-block-label">{label}</span>}
  </HStack>
);

export default StatBlock;
