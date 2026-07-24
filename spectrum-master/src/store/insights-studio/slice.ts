import { createSlice } from '@reduxjs/toolkit';

import { VariableValue } from './types';
import { makeUserDataCardConfigKey } from './util';

export interface UpdateUserDataCardConfig {
  dashboardId: string;
  dataCardId: string;
  configuration: Record<string, VariableValue>;
}
export type UserDataCardConfig = Record<string, UpdateUserDataCardConfig>;

export interface InsightStudioState {
  userDataCardConfig: UserDataCardConfig;
}

export interface Payload<T> {
  payload: T;
}

const initialState: InsightStudioState = {
  userDataCardConfig: {},
};

const insightStudioSlice = createSlice({
  name: 'insightStudio',
  initialState,
  reducers: {
    updateUserDataCardConfig: (state, { payload }: Payload<UpdateUserDataCardConfig>) => {
      state.userDataCardConfig[makeUserDataCardConfigKey(payload.dashboardId, payload.dataCardId)] = payload;
    },
  },
});

export const {
  reducer,
  actions: { updateUserDataCardConfig },
} = insightStudioSlice;
