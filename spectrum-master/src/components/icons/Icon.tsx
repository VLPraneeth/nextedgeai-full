//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import cx from 'classnames';

import './Icon.less';

export enum IconSize {
  /**
   * Small size 12x12
   */
  SMALL,

  /**
   * Medium size 18x18
   */
  MEDIUM,

  /**
   * Large size 24x24
   */
  LARGE,

  /**
   * L-Large size 38x38
   */
  L_LARGE,

  /**
   * L-Large size 52x52
   */
  X_LARGE,
}

export interface IconProps {
  /**
   * alternate string for the image
   */
  alt?: string;

  /**
   * source image path
   */
  src: string;

  /**
   * Extra classname that will be added to the icon
   */
  className?: string;

  /**
   * size of the image
   */
  size?: IconSize;
}

const Icon = ({ alt, src, className, size = IconSize.LARGE }: IconProps) => {
  const cls = cx(className, 'synri-img', {
    small: size === IconSize.SMALL,
    medium: size === IconSize.MEDIUM,
    large: size === IconSize.LARGE,
    'l-large': size === IconSize.L_LARGE,
    'x-large': size === IconSize.X_LARGE,
  });
  return <img className={cls} alt={alt} src={src} />;
};

export default Icon;
