import { useEffect } from 'react';

import { testPipeline } from 'actions/entityPipelineActions';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import usePreviousValue from 'hooks/usePreviousValue';
import { RootState } from 'reducers/index';
import { selectCurrentEntityPipeline } from 'selectors/entityPipelineSelectors';
import { setTestPanelView } from 'store/test/actions';
import { selectEntityDraftId, selectTestPanelView } from 'store/test/selectors';
import { RunLiveTestPayload, TestPanelView } from 'store/test/types';

/**
 * Navigate to the live test results when we're notified a live test for this
 * pipeline has completed on the user is on the live test run panel.
 */
export const useNavigateWhenLiveTestCompletes = () => {
  const dispatch = useEnhancedDispatch();

  const visible = useEnhancedSelector(selectTestPanelView) === TestPanelView.LIVE_RUN;

  const currentPipeline = useEnhancedSelector(selectCurrentEntityPipeline);
  const { liveTestGraphId, liveTestCompletedTimestamp } = useEnhancedSelector((state: RootState) => ({
    liveTestGraphId: state.entityPipeline.liveTestGraphId,
    liveTestCompletedTimestamp: state.entityPipeline.liveTestCompletedTimestamp,
  }));

  const previousTimestamp = usePreviousValue(liveTestCompletedTimestamp);

  useEffect(() => {
    if (previousTimestamp !== liveTestCompletedTimestamp && visible && currentPipeline.targetId === liveTestGraphId) {
      dispatch(setTestPanelView(TestPanelView.LIVE_RESULTS));
    }
  }, [currentPipeline.targetId, dispatch, liveTestCompletedTimestamp, liveTestGraphId, previousTimestamp, visible]);
};

export const useRunLiveTest = () => {
  const dispatch = useEnhancedDispatch();

  const entityId = useEnhancedSelector((state) => state.pipeline.pipelineId);
  const graphId = useEnhancedSelector(selectEntityDraftId);

  return (criteria: RunLiveTestPayload) => {
    if (entityId) {
      dispatch(testPipeline(criteria, entityId, graphId));
    }
  };
};
