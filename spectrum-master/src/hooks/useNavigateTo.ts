import { navigate } from '@reach/router';
import { isFunction } from 'lodash/fp';

import { selectPipelineChange } from 'selectors/pipelineSelectors';
import {
  setChangesInProgress,
  setChangesInProgressModal,
  setNavigatingTo as setNavigatingToAction,
} from 'store/app/actions';
import { showUnsavedConfirmModal } from 'store/pipeline/actions';
import { getNavigateParams, navigateTo } from 'utils/AppUtil';

import { useEnhancedDispatch, useEnhancedSelector } from './redux';

function canNavigateTo(url: string, params: any) {
  return !params.changed;
}

const useNavigateTo = (params?: any) => {
  return (url: string) => {
    if (canNavigateTo(url, { ...params, url })) {
      navigate(url);
    } else if (isFunction(params.showConfirmModal)) {
      params.setNavigatingTo(url);
      params.showConfirmModal(true);
    }
  };
};

export const useChangeAwareNavigation = () => {
  const pipelineChange = useEnhancedSelector(selectPipelineChange);
  const dispatch = useEnhancedDispatch();
  const setNavigatingTo = (url: string) => dispatch(setNavigatingToAction(url));
  const showConfirmModal = (boolean: boolean) => dispatch(showUnsavedConfirmModal(boolean));
  const dataChange = useEnhancedSelector((state) => state.app.changesInProgress);
  const changesInProgressModalSelector = useEnhancedSelector((state) => state.app.changesInProgressModal);

  return (url: string, event?: React.MouseEvent<HTMLAnchorElement, MouseEvent>) => {
    if ((pipelineChange.changed || dataChange) && event) {
      event.preventDefault();
    }

    if (dataChange) {
      dispatch(setChangesInProgress(false));
      dispatch(
        setChangesInProgressModal({
          visible: true,
          variant: changesInProgressModalSelector.variant || undefined,
          discardChangesAction: () => navigateTo(url),
        })
      );
      return;
    }
    navigateTo(url, getNavigateParams({ ...pipelineChange, showConfirmModal, setNavigatingTo, event }));
  };
};

export default useNavigateTo;
