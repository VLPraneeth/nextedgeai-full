//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { createSlice } from '@reduxjs/toolkit';

import { SDKCustomSynapseState } from '../types';

export const initialState: SDKCustomSynapseState = {
  customSynapseSharePanel: {
    customSynapse: null,
    visible: false,
  },
  customSdkSynapseSharePanel: {
    customSynapse: null,
    visible: false,
  },
  customSynapseApprovalModal: {
    customSynapse: null,
    visible: false,
  },
};

const sdkCustomSynapseSlice = createSlice({
  name: 'customSynapse',
  initialState,
  reducers: {
    showCustomSynapseSharePanel: (state, action) => {
      state.customSynapseSharePanel = action.payload;
    },
    showCustomSdkSynapseSharePanel: (state, action) => {
      state.customSdkSynapseSharePanel = action.payload;
    },
    showCustomSynapseApprovalModal: (state, action) => {
      state.customSynapseApprovalModal = action.payload;
    },
  },
});

export const {
  reducer,
  actions: { showCustomSynapseSharePanel, showCustomSynapseApprovalModal, showCustomSdkSynapseSharePanel },
} = sdkCustomSynapseSlice;
