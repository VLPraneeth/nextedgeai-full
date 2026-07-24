import cn from 'classnames';
import { MouseEventHandler, ReactNode } from 'react';

import './TopBanner.less';

export enum TopBannerTypes {
  Info,
  Warn,
  Ghosted,
}

interface TopBannerProps {
  children?: ReactNode;
  type?: TopBannerTypes;
}

export const TopBanner = ({ children, type = TopBannerTypes.Info }: TopBannerProps) => {
  const classes = cn('top-banner', {
    'top-banner--info': type === TopBannerTypes.Info,
    'top-banner--warn': type === TopBannerTypes.Warn,
    'top-banner--ghosted': type === TopBannerTypes.Ghosted,
  });

  return <div className={classes}>{children}</div>;
};

export interface TopBannerButtonProps {
  ghost?: boolean;
  onClick: MouseEventHandler;
  children?: React.ReactNode;
}
export const TopBannerButton = ({ children, ghost, onClick }: TopBannerButtonProps) => {
  const classes = cn('top-banner-button', {
    'top-banner-button--ghost': ghost,
  });
  return (
    <button className={classes} onClick={onClick}>
      {children}
    </button>
  );
};
