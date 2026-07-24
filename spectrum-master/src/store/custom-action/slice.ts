//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { createSlice } from '@reduxjs/toolkit';

import { CustomActionState } from './types';

export const initialState: CustomActionState = {
  customActionWizardVisible: false,
  customActionSharing: {
    visible: false,
  },
};

const customActionSlice = createSlice({
  name: 'customAction',
  initialState,
  reducers: {
    showCustomActionWizard: (state, action) => {
      state.customActionWizardVisible = action.payload.visible;
    },
    showCustomActionShare: (state, action) => {
      state.customActionSharing = {
        visible: action.payload.visible,
        customActionId: action.payload.customActionId,
      };
    },
  },
  extraReducers: {
    SAVE_CUSTOM_ACTION_SUCCESS: (state, action) => {},
  },
});

export const {
  reducer,
  actions: { showCustomActionWizard, showCustomActionShare },
} = customActionSlice;
