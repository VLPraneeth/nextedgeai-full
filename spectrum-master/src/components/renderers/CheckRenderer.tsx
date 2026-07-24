//
// Copyright (c) 2019-Present Syncari - All rights reserved.
// This is readonly checkbox renderer for table

import { Checkbox } from 'antd';
import classNames from 'classnames';

import './CheckRenderer.less';

export default function CheckRenderer(props: any) {
  const { className, text, label } = props;
  const cls = classNames('synri-check-renderer', className);
  const checked = text === 'yes' || text === 'true' || text === true;
  // @ts-ignore
  return <Checkbox className={cls} checked={checked} label={label} />;
}
