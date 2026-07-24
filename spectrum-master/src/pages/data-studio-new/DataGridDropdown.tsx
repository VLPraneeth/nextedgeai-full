import { Link, LinkProps, useLocation, useNavigate } from '@reach/router';
import cx from 'classnames';
import { cloneElement, ReactNode, useEffect, useState, useCallback } from 'react';
import { animated, useSpring } from 'react-spring';

import Arrow, { Direction } from 'components/Arrow';
import { HStack, Stack } from 'components/layout';
import { colors } from 'utils/LessConstants';
import './DataGridDropdown.less';

const DataGridDropdownItemBadge = ({ children }: { children?: ReactNode }) => {
  return <div className="data-studio-sidebar-item-badge">{children}</div>;
};

interface DataGridDropdownExpansionArrowProps {
  isOpen: boolean;
  onClick?: () => void;
}

const DataGridDropdownExpansionArrow = ({ isOpen, onClick }: DataGridDropdownExpansionArrowProps) => {
  return (
    <div role="button" onClick={onClick} className="sidebar-expansion-arrow">
      <Arrow direction={isOpen ? Direction.DOWN : Direction.RIGHT} color={colors.lightGray} size={4} />
    </div>
  );
};

interface DataGridDropdownSectionProps {
  callToAction?: React.ReactNode;
  className?: string;
  contentClassName?: string;
  title: string;
  children?: ReactNode;
  isExpanded?: boolean;
  onToggle?: () => void;
}

const DataGridDropdownSection = ({
  title,
  className,
  contentClassName,
  callToAction,
  children,
  isExpanded: isExpandedProp = title === 'Entities',
  onToggle,
}: DataGridDropdownSectionProps) => {
  const [internalExpanded, setInternalExpanded] = useState(isExpandedProp);
  const isControlled = onToggle !== undefined;
  const isExpanded = isControlled ? isExpandedProp : internalExpanded;

  const toggleExpanded = useCallback(() => {
    if (isControlled) {
      onToggle?.();
    } else {
      setInternalExpanded((prev) => !prev);
    }
  }, [isControlled, onToggle]);

  const spring = useSpring(isExpanded ? { opacity: 1, maxHeight: 1000 } : { maxHeight: 0, opacity: 0 });

  return (
    <div className={cx('data-studio-sidebar-section', className)}>
      <HStack align="start" justify="space-between">
        <div
          role="button"
          aria-expanded={isExpanded}
          onClick={toggleExpanded}
          className="data-studio-sidebar-section-title">
          <HStack spacing="xs">
            <span>{title}</span>
            <DataGridDropdownExpansionArrow isOpen={isExpanded} />
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

export interface DataGridSectionItemProps {
  badge?: React.ReactNode;
  className?: string;
  children: React.ReactNode;
  rightChildren?: React.ReactElement;
}

const DataGridDropdownSectionItem = ({ badge, className, children, rightChildren }: DataGridSectionItemProps) => {
  return (
    <HStack className={cx('data-studio-sidebar-section-item', className)} spacing="xs" grow>
      {children}
      {badge && <DataGridDropdownItemBadge key="badge">{badge}</DataGridDropdownItemBadge>}
      {rightChildren && cloneElement(rightChildren, { key: 'right-children' })}
    </HStack>
  );
};

export type DataGridDropdownSectionLinkItemProps<LinkState> = LinkProps<LinkState> &
  DataGridSectionItemProps & { highlightPartialMatch?: boolean };

const DataGridDropdownSectionLinkItem = <LinkState extends unknown>({
  highlightPartialMatch,
  children,
  to,
  state,
  onClick,
  ...props
}: DataGridDropdownSectionLinkItemProps<LinkState>) => {
  const [highlightAsCurrent, setHighlightAsCurrent] = useState(false);
  const [toPathname, toSearch] = to.split('?');
  const navigate = useNavigate();

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

  const handleClick = useCallback(
    (e: React.MouseEvent<HTMLAnchorElement>) => {
      e.preventDefault();
      navigate(to, { replace: false }).then(() => {
        if (onClick) {
          onClick(e);
        }
      });
    },
    [navigate, onClick, state, to]
  );

  return (
    <DataGridDropdownSectionItem
      className={cx('data-studio-sidebar-section-item-link-wrapper', {
        'is-current': highlightAsCurrent,
      })}
      {...props}>
      <Link
        onClick={handleClick}
        className="data-studio-sidebar-section-item-link data-studio-item-title-truncated"
        to={to}
        state={state}>
        {children}
      </Link>
    </DataGridDropdownSectionItem>
  );
};

export {
  DataGridDropdownItemBadge,
  DataGridDropdownSection,
  DataGridDropdownSectionItem,
  DataGridDropdownSectionLinkItem,
};
