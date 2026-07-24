import { createAsyncThunk } from '@reduxjs/toolkit';

import { getResyncDetails } from 'actions/entityPipelineActions';
import { RootState } from 'store/types';

export const resyncEntityStatusUpdateThunk = createAsyncThunk<
  void,
  {
    payload: {
      entityId: string;
      status: string;
    };
  },
  { state: RootState }
>('RESYNC_ENTITY_STATUS_UPDATE', (action, { dispatch }) => {
  const { entityId } = action.payload;

  // Simply refetch the resync details when the resync status changes
  dispatch(getResyncDetails(entityId));
});
