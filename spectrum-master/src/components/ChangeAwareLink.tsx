//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { Link as ReachLink } from '@reach/router';

import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { setNavigatingTo as setNavigatingToAction } from 'store/app/actions';
import { showUnsavedConfirmModal } from 'store/pipeline/actions';
import { navigateTo } from 'utils/AppUtil';
import { isCmdOrCtrlPressed } from 'utils/EventHandlerUtil';

export interface ChangeAwareLinkProps {
  children: React.ReactNode;
  className?: string;
  onClick?: () => void;
  state?: Record<any, any>;
  to: string;

  // Props added by Ant to enable breadcrumb popover
  onMouseEnter?: () => void;
  onMouseLeave?: () => void;
}

const ChangeAwareLink = ({
  children,
  className,
  onClick,
  state = {},
  to,
  // Props added by Ant to enable breadcrumb popover
  onMouseEnter,
  onMouseLeave,
}: ChangeAwareLinkProps) => {
  const dispatch = useEnhancedDispatch();
  const changed = useEnhancedSelector((state) => state.pipeline.changed);

  const showConfirmModal = (show: boolean) => dispatch(showUnsavedConfirmModal(show));
  const setNavigatingTo = (url: string) => dispatch(setNavigatingToAction(url));

  const _onClick = (evt: React.MouseEvent) => {
    if (isCmdOrCtrlPressed(evt)) {
      return;
    }
    evt.preventDefault();
    navigateTo(to, { changed, setNavigatingTo, showConfirmModal, state });
    if (onClick) {
      onClick();
    }
  };

  return (
    <ReachLink className={className} to={to} onClick={_onClick} onMouseEnter={onMouseEnter} onMouseLeave={onMouseLeave}>
      {children}
    </ReachLink>
  );
};

export default ChangeAwareLink;
