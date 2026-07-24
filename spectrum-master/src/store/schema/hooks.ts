import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import AppConstants from 'utils/AppConstants';

import { selectFieldDeleteStatus } from './selectors';
import { deleteSchemaField, UpdateDeleteSchemaFieldArgs } from './slice';

export const useDeleteSchemaField = () => {
  const dispatch = useEnhancedDispatch();
  const { deleteStatus, deleteError } = useEnhancedSelector(selectFieldDeleteStatus);

  return {
    deleteSchemaField: (args: UpdateDeleteSchemaFieldArgs) => dispatch(deleteSchemaField(args)),
    deleteLoading: deleteStatus === AppConstants.FETCH_STATUS.LOADING,
    deleteError,
    deleteStatus,
  };
};
