import { useEnhancedSelector } from 'hooks/redux';
import { useTestResultContext } from 'pages/sync-studio/test/test-panels/test-hooks/TestResultPanel.hooks';
import { RootState } from 'reducers/index';

export const useSelectedEntityIsRunningTest = () => {
  const context = useTestResultContext();
  const draftIsReadOnly = useEnhancedSelector(
    (state: RootState) => state.entityPipeline.entityPipeline?.draft?.readOnly
  );

  return !!draftIsReadOnly || context?.testRunIsProcessing;
};
