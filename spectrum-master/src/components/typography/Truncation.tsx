import Tooltip, { TooltipPlacement, TooltipProps } from 'antd/lib/tooltip';
import cx from 'classnames';
import * as React from 'react';
import { useEffect, useState } from 'react';

import type { ElementType } from './types';
import useIsTextTruncated from './useIsTextTruncated';

import './Truncation.less';

export enum TruncationTooltipMode {
  ALWAYS = 'always',
  AUTO = 'auto',
  NEVER = 'never',
}

export interface TruncationProps extends Omit<TooltipProps, 'className' | 'children'> {
  as?: ElementType;
  className?: string;
  tooltipDisplayMode?: TruncationTooltipMode;
  tooltipPlacement?: TooltipPlacement;
  tooltipOverlayClassName?: string;
  /** Tooltip is shown when text is truncated and if `tooltipText` is provided */
  tooltipText?: string;
  /**
   * Tooltip title will be extracted from the children using `.innerText`. If `tooltipText` is provided
   * then this is ignored.
   * */
  autoTooltipText?: boolean;
  children?: React.ReactNode;
}

const Truncation = ({
  as: Component = 'span',
  autoTooltipText = true,
  children,
  className,
  tooltipDisplayMode = TruncationTooltipMode.AUTO,
  tooltipPlacement = 'top',
  tooltipOverlayClassName,
  tooltipText,
}: TruncationProps) => {
  const [setRef, isTruncated, element] = useIsTextTruncated();
  const [tooltipTitle, setTooltipTitle] = useState(() => tooltipText);

  useEffect(() => {
    if (element && !tooltipText && autoTooltipText) {
      setTooltipTitle(element.innerText);
    }
  }, [autoTooltipText, element, tooltipText]);

  const shouldShowTooltip =
    tooltipDisplayMode === TruncationTooltipMode.ALWAYS ||
    (tooltipDisplayMode === TruncationTooltipMode.AUTO && isTruncated);

  return (
    <Component ref={setRef} className={cx('synri-text-truncation', className)}>
      {shouldShowTooltip && tooltipTitle ? (
        <Tooltip overlayClassName={tooltipOverlayClassName} title={tooltipTitle} placement={tooltipPlacement}>
          {children}
        </Tooltip>
      ) : (
        children
      )}
    </Component>
  );
};

export default Truncation;
