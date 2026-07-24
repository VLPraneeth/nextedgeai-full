import { Link, LinkProps, useLocation } from '@reach/router';
import cx from 'classnames';
import { cloneElement, ReactNode, useEffect, useState } from 'react';
import { animated, useSpring } from 'react-spring';

import Arrow, { Direction } from 'components/Arrow';
import { HStack, Stack } from 'components/layout';
import { colors } from 'utils/LessConstants';
import './Sidebar.less';

const SidebarItemBadge = ({ children }: { children?: ReactNode }) => {
  return <div className="data-studio-sidebar-item-badge">{children}</div>;
};

interface SidebarExpansionArrowProps {
  isOpen: boolean;
  onClick?: () => void;
}

const SidebarExpansionArrow = ({ isOpen, onClick }: SidebarExpansionArrowProps) => {
  return (
    <div role="button" onClick={onClick} className="sidebar-expansion-arrow">
      <Arrow direction={isOpen ? Direction.DOWN : Direction.RIGHT} color={colors.lightGray} size={5} />
    </div>
  );
};

interface SidebarSectionProps {
  callToAction?: React.ReactNode;
  className?: string;
  contentClassName?: string;
  title: string;
  children?: ReactNode;
}

const SidebarSection = ({ title, className, contentClassName, callToAction, children }: SidebarSectionProps) => {
  const [isExpanded, setIsExpanded] = useState(true);
  const toggleExpanded = () => setIsExpanded((prev) => !prev);

  const spring = useSpring(isExpanded ? { opacity: 1, maxHeight: 2000 } : { maxHeight: 0, opacity: 0 });

  return (
    <div className={cx('data-studio-sidebar-section', className)}>
      <HStack align="center" justify="space-between" className="data-studio-sidebar-section-header">
        <div
          role="button"
          aria-expanded={isExpanded}
          onClick={toggleExpanded}
          className="data-studio-sidebar-section-title">
          <HStack spacing="xs">
            <span>{title}</span>
            <SidebarExpansionArrow isOpen={isExpanded} />
          </HStack>
        </div>
        {callToAction}
      </HStack>
      <animated.div className={cx('data-studio-sidebar-section-content', contentClassName)} style={spring}>
        <Stack spacing="z">{children}</Stack>
      </animated.div>
    </div>
  );
};

export interface SidebarSectionItemProps {
  badge?: React.ReactNode;
  className?: string;
  children: React.ReactNode;
  rightChildren?: React.ReactElement;
}

const SidebarSectionItem = ({ badge, className, children, rightChildren }: SidebarSectionItemProps) => {
  return (
    <HStack className={cx('data-studio-sidebar-section-item', className)} spacing="xs">
      {children}
      {badge && <SidebarItemBadge key="badge">{badge}</SidebarItemBadge>}
      {rightChildren && cloneElement(rightChildren, { key: 'right-children' })}
    </HStack>
  );
};

export type SidebarSectionLinkItemProps<LinkState> = LinkProps<LinkState> &
  SidebarSectionItemProps & { highlightPartialMatch?: boolean };

const SidebarSectionLinkItem = <LinkState extends unknown>({
  highlightPartialMatch,
  children,
  to,
  state,
  ...props
}: SidebarSectionLinkItemProps<LinkState>) => {
  const [highlightAsCurrent, setHighlightAsCurrent] = useState(false);
  const [toPathname, toSearch] = to.split('?');

  // enhanced re-implementation of isCurrent/isPartially current data that is passed to Link
  // via getProps. I've re-implemented this here so we can hoist these props to the parent component
  const { pathname, search } = useLocation();
  const isCurrent = pathname === toPathname && search.slice(1) === toSearch;
  const isPartiallyCurrent = highlightPartialMatch && pathname.startsWith(to);

  useEffect(() => {
    if (isCurrent || isPartiallyCurrent) {
      setHighlightAsCurrent(true);
    } else if (highlightAsCurrent) {
      setHighlightAsCurrent(false);
    }
  }, [highlightPartialMatch, isCurrent, isPartiallyCurrent, highlightAsCurrent]);

  return (
    <SidebarSectionItem
      className={cx('data-studio-sidebar-section-item-link-wrapper', {
        'is-current': highlightAsCurrent,
      })}
      {...props}>
      <Link className="data-studio-sidebar-section-item-link data-studio-item-title-truncated" to={to} state={state}>
        {children}
      </Link>
    </SidebarSectionItem>
  );
};

const Sidebar = ({ children }: { children?: ReactNode }) => {
  return (
    <div className="data-studio-sidebar">
      <Stack spacing="xl">{children}</Stack>
    </div>
  );
};

export default Sidebar;
export { SidebarItemBadge, SidebarSection, SidebarSectionItem, SidebarSectionLinkItem };
