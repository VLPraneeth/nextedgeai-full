//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Icon, Tooltip } from 'antd';
import cx from 'classnames';
import * as React from 'react';
import { useMemo } from 'react';
import { animated, useTransition } from 'react-spring';

import { HStack, Spacer } from 'components/layout';
import { tNamespaced } from 'utils/i18nUtil';
import { UserflowTags } from 'utils/UserflowTags';

import './Toolbar.less';

const tn = tNamespaced('SchemaStudio.Toolbar');

interface ToolbarProps {
  backToName?: string;
  onRequestBack?: () => void;
  leftChildren?: React.ReactElement;
  className?: string;
  children?: React.ReactNode;
}

const ArrowIconStyle = { fontSize: 18 };

const BackButtonTransitionConfig = {
  from: { maxWidth: 0, opacity: 0, height: '100%' },
  enter: { maxWidth: 200, opacity: 1, height: '100%' },
  leave: { opacity: 0, maxWidth: 0 },
  unique: true,
};

const Toolbar = ({ backToName, onRequestBack, className, leftChildren, children }: ToolbarProps) => {
  const backButtonTooltipTitle = useMemo(() => (backToName ? tn('back_to_name', { name: backToName }) : undefined), [
    backToName,
  ]);

  const backButtonTransitions = useTransition(onRequestBack && backButtonTooltipTitle, BackButtonTransitionConfig);

  return (
    <div className={cx('toolbar-container', className, {})}>
      <div className="left-group">
        {backButtonTransitions(
          (style, item) =>
            item && (
              <animated.div style={style}>
                <Tooltip title={backButtonTooltipTitle} placement="bottom">
                  <div className="toolbar-button" data-userflow-tag={UserflowTags.Toolbar.Back} onClick={onRequestBack}>
                    <Icon type="arrow-left" style={ArrowIconStyle} />
                  </div>
                </Tooltip>
              </animated.div>
            )
        )}
        {leftChildren && (
          <HStack>
            <Spacer x="md" />
            {React.Children.toArray(leftChildren)}
          </HStack>
        )}
      </div>
      <div className="right-group">{children}</div>
    </div>
  );
};

export default Toolbar;
