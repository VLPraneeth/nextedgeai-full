//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { createSlice } from '@reduxjs/toolkit';

import AppConstants from 'utils/AppConstants';

import { QuickStartState } from './types';

export const initialState: QuickStartState = {
  serverInstallStatus: {},
};

const quickStartSlice = createSlice({
  name: 'quickStart',
  initialState,
  reducers: {
    resetInstallStatus: (state, action) => {
      state.serverInstallStatus[action.payload.quickStartId] = AppConstants.FETCH_STATUS.IDLE;
    },
  },
  extraReducers: {
    INSTALL_QUICK_START_SUCCESS: (state, action) => {
      state.serverInstallStatus[action.payload.quickStartId] = AppConstants.FETCH_STATUS.SUCCESS;
    },
  },
});

export const {
  reducer,
  actions: { resetInstallStatus },
} = quickStartSlice;
