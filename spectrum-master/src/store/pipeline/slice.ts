//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { createSlice, PayloadAction } from '@reduxjs/toolkit';

import {
  graphChanged,
  groupNodeUpdateAction,
  moveGraphTooltip,
  nodeKebabAction,
  setCurrentGraph,
  setDisplayedGraph,
  setDragSelectMode,
  setPipelineId,
  setSelectedNodeIds,
  showConfirmDuplicateModal,
  showConfirmUngroupModal,
  showCreateGroupPanel,
  showDeleteMultipleNodesModal,
  showUnsavedConfirmModal,
} from './actions';
import * as ActionTypes from './types';
import { PipelineState } from './types';

export const initialState: PipelineState = {
  changed: false,
  changedId: null,
  changedScope: null,
  currentGraph: null,
  displayedGraph: null,
  dragSelectMode: false,
  selectedNodeIds: [],
  pipelineId: null,
  unsavedConfirmModalVisible: false,
  deleteMultipleNodesModalVisible: false,
  groupNodeUpdate: null,
  createGroupPanel: {
    visible: false,
  },
  confirmUngroupModal: {
    visible: false,
  },
  confirmDuplicateModal: {
    visible: false,
  },
  createVersionModal: {
    visible: false,
  },
  restoreVersionModal: {
    visible: false,
  },
  createTestVisible: false,
  testResultVisible: false,
  tooltipCoordinates: {
    top: 0,
    left: 0,
  },
  settingsPanel: {
    visible: false,
  },
};

const pipelineSlice = createSlice({
  name: 'pipeline',
  initialState,
  reducers: {
    showCreateVersionModal(state, { payload }: PayloadAction<ActionTypes.CreateVersionModalParams>) {
      state.createVersionModal = payload;
    },
    showRestoreVersionModal(state, { payload }: PayloadAction<ActionTypes.RestoreVersionModalParams>) {
      state.restoreVersionModal = payload;
    },
    showSettingsPanel(state, { payload }: PayloadAction<ActionTypes.SettingsPanelParams>) {
      state.settingsPanel = payload;
    },
  },
  extraReducers: (builder) => {
    builder.addCase(setDisplayedGraph, (state, action) => {
      state.displayedGraph = action.payload.displayedGraph;
    });

    builder.addCase(graphChanged, (state, action) => {
      state.changed = action.payload.changed ?? false;
      state.changedId = action.payload.changedId;
      state.changedScope = action.payload.changedScope;
    });

    builder.addCase(groupNodeUpdateAction, (state, action) => {
      state.groupNodeUpdate = action.payload;
    });

    builder.addCase(nodeKebabAction, (state, action) => {
      state.nodeKebabAction = action.payload;
    });

    builder.addCase(showDeleteMultipleNodesModal, (state, action) => {
      state.deleteMultipleNodesModalVisible = action.payload.visible;
    });

    builder.addCase(showCreateGroupPanel, (state, action) => {
      state.createGroupPanel = action.payload.createGroupPanelParams;
    });

    builder.addCase(showConfirmUngroupModal, (state, action) => {
      state.confirmUngroupModal = action.payload.confirmUngroupModalParams;
    });

    builder.addCase(showConfirmDuplicateModal, (state, action) => {
      state.confirmDuplicateModal = action.payload.confirmDuplicateModalParams;
    });

    builder.addCase(showUnsavedConfirmModal, (state, action) => {
      state.unsavedConfirmModalVisible = action.payload.visible;
    });

    builder.addCase(setPipelineId, (state, action) => {
      state.pipelineId = action.payload.pipelineId;
    });

    builder.addCase(setCurrentGraph, (state, action) => {
      state.currentGraph = action.payload.graphJson;
    });

    builder.addCase(moveGraphTooltip, (state, action) => {
      state.tooltipCoordinates = action.payload.coordinates;
    });

    builder.addCase(setSelectedNodeIds, (state, action) => {
      state.selectedNodeIds = action.payload.selectedNodeIds;
    });

    builder.addCase(setDragSelectMode, (state, action) => {
      state.dragSelectMode = action.payload.flag;
    });
  },
});

export const {
  reducer,
  actions: { showCreateVersionModal, showRestoreVersionModal, showSettingsPanel },
} = pipelineSlice;
