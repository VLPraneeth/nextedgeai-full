//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Icon, Tooltip } from 'antd';
import cx from 'classnames';
import * as React from 'react';
import { useContext, useMemo, useState } from 'react';
import { animated, useTransition } from 'react-spring';

import { ReactComponent as ChevronDown } from 'assets/icons/chevron-down.svg';

import './Fieldset.less';

export interface FieldsetContextShape {
  isCollapsed: boolean;
  toggleCollapsed: () => void;
  updateCollapsedBadge: (value: string) => void;
}

const FieldsetContext = React.createContext<FieldsetContextShape>({
  isCollapsed: false,
  toggleCollapsed: () => {},
  updateCollapsedBadge: (value: string) => {},
});
export const useFieldsetContext = () => useContext(FieldsetContext);

interface FieldsetBadgeProps {
  children?: React.ReactNode;
}

const FieldsetBadge = ({ children }: FieldsetBadgeProps) => {
  return <span className="fieldset-badge">{children}</span>;
};

interface DisclosureArrowButtonProps {
  ariaLabel?: string;
  isOpen: boolean;
  onClick: (evt: any) => void;
}

const DisclosureArrowButton = ({ isOpen, onClick, ariaLabel }: DisclosureArrowButtonProps) => {
  return (
    <button aria-label={ariaLabel} type="button" className="fieldset-disclosure-arrow-btn" onClick={onClick}>
      <ChevronDown
        className={cx('fieldset-disclosure-arrow', {
          'is-open': isOpen,
        })}
      />
    </button>
  );
};

export interface FieldSetProps {
  /**
   * Title text that will be in the legend html element.
   */
  title?: string;

  /**
   * Extra class name that will be added to the fieldset.
   */
  className?: string;

  /**
   * Fieldset tooltipo
   */
  tooltip?: string;

  collapsible?: boolean;
  collapsed?: boolean;
  collapsedBadge?: string;
  defaultCollapsed?: boolean;
  showBottomBorder?: boolean;
  onToggleCollapse?: () => void;
  children?: React.ReactNode;
}

/**
 * Fieldset element with syncari styles.
 * @param FieldSetProps
 */
const Fieldset = ({
  title,
  children,
  collapsible = false,
  tooltip,
  collapsed,
  defaultCollapsed,
  showBottomBorder = false,
  onToggleCollapse,
  className,
  ...rest
}: FieldSetProps) => {
  const [collapsedBadge, setCollapsedBadge] = useState<string | number>();
  const [_collapsed, setCollapsed] = useState(defaultCollapsed || false);
  const toggle = () => setCollapsed((prev) => !prev);

  // these allow us to use this component as controlled or uncontrolled
  const isCollapsed = typeof collapsed !== 'undefined' ? collapsed : _collapsed;
  const handleToggleCollapse = typeof onToggleCollapse !== 'undefined' ? onToggleCollapse : toggle;

  const contextValue = useMemo(
    () => ({
      isCollapsed,
      toggleCollapsed: handleToggleCollapse,
      updateCollapsedBadge: setCollapsedBadge,
    }),
    [isCollapsed, handleToggleCollapse]
  );

  const initialScale = isCollapsed ? 0 : 1;
  const badgeTransitions = useTransition(isCollapsed, {
    from: {
      display: 'inline-block',
      top: '-1px',
      opacity: isCollapsed ? 1 : 1,
      transform: `scale3d(${initialScale}, ${initialScale}, 1)`,
    },
    enter: { opacity: 1, transform: 'scale3d(1, 1, 1)' },
    leave: { opacity: 0, transform: 'scale3d(0, 0, 0)' },
  });

  const contentTransitions = useTransition(!isCollapsed, {
    from: { opacity: 0, maxHeight: 0 },
    enter: { opacity: 1, maxHeight: 'fit-content' },
    leave: { opacity: 0, maxHeight: 0 },
  });

  return (
    <FieldsetContext.Provider value={contextValue}>
      <fieldset
        className={cx('synri-fieldset', className, {
          expanded: showBottomBorder && collapsible && !isCollapsed,
        })}
        {...rest}>
        <legend className={cx({ collapsible })}>
          {collapsible ? (
            <>
              <DisclosureArrowButton
                ariaLabel={`collapse ${title}`}
                isOpen={!isCollapsed}
                onClick={handleToggleCollapse}
              />
              <button type="button" className="debuttonize" onClick={handleToggleCollapse}>
                {title}
              </button>
            </>
          ) : (
            title
          )}
          {tooltip && (
            <Tooltip title={tooltip}>
              <Icon type="question-circle" theme="filled" />
            </Tooltip>
          )}
          {collapsedBadge &&
            badgeTransitions(
              (style, item) =>
                item && (
                  <animated.span style={{ position: 'absolute', ...style }}>
                    <FieldsetBadge>{collapsedBadge}</FieldsetBadge>
                  </animated.span>
                )
            )}
        </legend>
        {contentTransitions((props, item) => item && <animated.div style={props}>{children}</animated.div>)}
      </fieldset>
    </FieldsetContext.Provider>
  );
};

export default Fieldset;
