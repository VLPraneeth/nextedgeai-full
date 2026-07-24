import { createSlice } from '@reduxjs/toolkit';

import { ValidationState } from './types';

export const initialState: ValidationState = {
  errors: [],
  isGotoBetweenFieldPipelines: false,
  validationToolbarVisible: false,
  validationResultsPanelVisible: false,
  warnings: [],
};

const validationSlice = createSlice({
  name: 'validation',
  initialState,
  reducers: {
    setErrors: (state, action) => {
      state.errors = action.payload;
    },
    setIsGotoBetweenFieldPipelines: (state, action) => {
      state.isGotoBetweenFieldPipelines = action.payload;
    },
    setValidationMode: (state, action) => {
      state.validationMode = action.payload;
    },
    setWarnings: (state, action) => {
      state.warnings = action.payload;
    },
    showValidationToolbar: (state, action) => {
      state.validationToolbarVisible = action.payload;
    },
    showValidationResultsPanel: (state, action) => {
      state.validationResultsPanelVisible = action.payload;
    },
  },
});

export const {
  reducer,
  actions: {
    setErrors,
    setIsGotoBetweenFieldPipelines,
    setValidationMode,
    setWarnings,
    showValidationToolbar,
    showValidationResultsPanel,
  },
} = validationSlice;
