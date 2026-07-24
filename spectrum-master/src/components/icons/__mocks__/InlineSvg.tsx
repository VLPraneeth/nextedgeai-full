//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import cx from 'classnames';

import { InlineSvgProps } from '../InlineSvg';

const InlineSvg = ({ src, title, className }: InlineSvgProps) => {
  // I'm putting these props as data-attributes so we can see them in snapshots
  return <div className={cx('synri-inline-svg', className)} data-icon-src={src} data-icon-title={title} />;
};

export default InlineSvg;
