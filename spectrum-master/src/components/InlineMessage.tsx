//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Tooltip } from 'antd';
import cx from 'classnames';
import { useMemo } from 'react';
import * as React from 'react';
import { animated, useTransition } from 'react-spring';

import { ValuesOf } from 'utils/TypeUtils';

import './InlineMessage.less';

export const Types = {
  ERROR: 'error',
  WARNING: 'warning',
  INFO: 'info',
  SUCCESS: 'success',
} as const;

const HIDDEN_STYLE = { opacity: 0, maxHeight: 0 } as const;

export interface InlineMessageProps {
  type: ValuesOf<typeof Types>;
  children?: React.ReactNode;
  title?: string;
  className?: string;
  maxHeight?: number;
  initallyExpanded?: boolean;
  allowMultiline?: boolean;
}

const InlineMessage = ({
  title,
  type,
  children,
  className,
  initallyExpanded = false,
  maxHeight = 400,
  allowMultiline = false,
}: InlineMessageProps) => {
  const OPEN_STYLE = useMemo(() => ({ opacity: 1, maxHeight } as const), [maxHeight]);

  const childrenTransition = useTransition(children, {
    unique: true,
    from: HIDDEN_STYLE,
    initial: initallyExpanded && !!children ? OPEN_STYLE : HIDDEN_STYLE,
    enter: OPEN_STYLE,
    leave: HIDDEN_STYLE,
  });

  return (
    <>
      {childrenTransition(
        (props, item) =>
          item && (
            <animated.div style={props}>
              <div
                className={cx(
                  'synri-inline-message',
                  {
                    'single-line': !allowMultiline,
                    error: type === Types.ERROR,
                    warning: type === Types.WARNING,
                    info: type === Types.INFO,
                    success: type === Types.SUCCESS,
                  },
                  className
                )}>
                {title ? (
                  <Tooltip title={title} placement="bottomLeft">
                    {React.Children.toArray(children)}
                  </Tooltip>
                ) : (
                  React.Children.toArray(children)
                )}
              </div>
            </animated.div>
          )
      )}
    </>
  );
};

export default InlineMessage;
