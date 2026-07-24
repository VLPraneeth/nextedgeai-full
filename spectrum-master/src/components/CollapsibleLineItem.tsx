import cx from 'classnames';
import { useState } from 'react';
import * as React from 'react';
import { useTransition, animated } from 'react-spring';

import { ReactComponent as ChevronDown } from 'assets/icons/chevron-down.svg';
import { HStack } from 'components/layout';
import { tNamespaced } from 'utils/i18nUtil';

import './CollapsibleLineItem.less';

const tn = tNamespaced('CollapsibleLineItem');

interface DisclosureArrowButtonProps {
  ariaLabel?: string;
  isOpen: boolean;
  onClick: () => void;
}

const DisclosureArrowButton = ({ isOpen, onClick, ariaLabel }: DisclosureArrowButtonProps) => {
  return (
    <button aria-label={ariaLabel} type="button" className="synri-collapsible-disclosure-arrow-btn" onClick={onClick}>
      <ChevronDown className={cx('synri-collapsible-disclosure-arrow', { 'is-open': isOpen })} />
    </button>
  );
};

export interface CollapsibleLineItemProps {
  initialExpand?: boolean;
  expanded?: boolean;
  onToggle?: (expanded: boolean) => void;
  title: string;
  children?: React.ReactNode;
  leftTitleChildren?: React.ReactNode;
  rightTitleChildren?: React.ReactNode;
  contentContainerClassName?: string;
  /* approximate max height of the content. Set this to the size of your content or larger */
  contentMaxHeight?: number;
}

const CollapsibleLineItem = ({
  initialExpand = false,
  expanded,
  onToggle,
  title,
  leftTitleChildren,
  rightTitleChildren,
  contentContainerClassName,
  contentMaxHeight = 600,
  children,
}: CollapsibleLineItemProps) => {
  const [_expanded, setExpanded] = useState(initialExpand);

  const isExpanded = typeof expanded === 'boolean' ? expanded : _expanded;

  const toggle = () => {
    const willExpand = !isExpanded;
    setExpanded(willExpand);
    onToggle?.(willExpand);
  };

  const contentTransitions = useTransition(isExpanded, {
    from: { opacity: 0, maxHeight: 0 },
    enter: { opacity: 1, maxHeight: contentMaxHeight },
    leave: { opacity: 0, maxHeight: 0 },
  });

  return (
    <div className="synri-collapsible-line-item-container">
      <div className="synri-collapsible-line-item-header">
        <HStack spacing="sm">
          <DisclosureArrowButton
            isOpen={isExpanded}
            onClick={toggle}
            ariaLabel={tn('disclosure_btn_aria', { title })}
          />
          <span onClick={toggle} className="synri-collapsible-line-item-title">
            {title}
          </span>
          {leftTitleChildren}
        </HStack>
        <HStack>{rightTitleChildren}</HStack>
      </div>
      <div className={cx('synri-collapsible-line-item-content', { isExpanded })}>
        {contentTransitions(
          (style, item) =>
            item && (
              <animated.div style={{ overflow: 'hidden', ...style }} className={contentContainerClassName}>
                {children}
              </animated.div>
            )
        )}
      </div>
    </div>
  );
};

export default CollapsibleLineItem;
