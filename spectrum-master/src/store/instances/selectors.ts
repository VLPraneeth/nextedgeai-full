import { useEnhancedSelector } from 'hooks/redux';
import { RootState } from 'reducers';
import AppConstants from 'utils/AppConstants';

export const selectAllInstances = (state: RootState) => state.instance.instances;
export const selectInstancesLoading = (state: RootState) =>
  state.instance.instancesStatus === AppConstants.FETCH_STATUS.LOADING;

export const useSelectInstanceById = (instanceId: string) => {
  const instances = useEnhancedSelector(selectAllInstances);
  return instances.find((instance) => instance.syncariId === instanceId);
};
