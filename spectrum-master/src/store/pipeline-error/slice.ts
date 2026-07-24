import { createSlice } from '@reduxjs/toolkit';

export interface PipelineErrorState {
  placeholder: boolean;
  resultsPanelVisible: boolean;
}

export const initialState: PipelineErrorState = {
  placeholder: false,
  resultsPanelVisible: false,
};

const pipelineErrorSlice = createSlice({
  name: 'pipelineError',
  initialState,
  reducers: {
    setPlaceholder: (state, action) => {
      state.placeholder = action.payload;
    },
    showPipelineErrorResultsPanel: (state, action) => {
      state.resultsPanelVisible = action.payload;
    },
  },
});

export const {
  reducer,
  actions: { setPlaceholder, showPipelineErrorResultsPanel },
} = pipelineErrorSlice;
