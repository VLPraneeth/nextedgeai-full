//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import cx from 'classnames';
import SVG from 'react-inlinesvg';

import './InlineSvg.less';

export interface InlineSvgProps {
  className?: string;
  size?: '1x' | '2x';
  src?: string;
  title: string;
}

const InlineSvg = ({ className, size, src, title }: InlineSvgProps) => {
  const isSvg = src?.endsWith('.svg');
  return (
    <div
      className={cx(className, 'synri-inline-svg', { 'synri-inline-svg--2x': size === '2x' }, { 'non-svg': !isSvg })}>
      {/* Use the browser not found image if its blank */}
      {isSvg && src ? <SVG src={src} title={title} cacheRequests /> : <img src={src} alt={title} />}
    </div>
  );
};

export default InlineSvg;
