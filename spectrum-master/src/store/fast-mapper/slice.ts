//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { createSlice, PayloadAction } from '@reduxjs/toolkit';

import { CreateFieldState } from 'pages/sync-studio/fast-mapper/CreateFieldDropdown';
import AppConstants from 'utils/AppConstants';

import { deleteMappings, editMappings, getMappings, saveMappings } from './thunks';
import { FastMapperState, MappingError, ServerMapping } from './types';

const { FETCH_STATUS } = AppConstants;

export enum CreateFieldModalMode {
  CREATE = 'create',
  EDIT = 'edit',
}

export const initialTestState: FastMapperState = {
  createFieldModal: { id: '', visible: false, mode: CreateFieldModalMode.CREATE },
  editMappingsStatus: FETCH_STATUS.IDLE,
  fastMapperEntityId: '',
  fastMapperVisible: false,
  mappingsStatus: FETCH_STATUS.IDLE,
  saveMappingsStatus: FETCH_STATUS.IDLE,
};

export type ShowFastMapperAction = PayloadAction<{ visible: boolean; entityId: string }>;

export interface CreateFieldModal {
  id: string;
  visible: boolean;
  mode: CreateFieldModalMode;
  position?: {
    top?: number;
    left?: number;
    width?: number;
  };
  data?: CreateFieldState;
}

export type ShowCreateFieldAction = PayloadAction<CreateFieldModal>;

export type SetAddMappingErrorAction = PayloadAction<{ error: MappingError[] }>;
export type SetEditMappingErrorAction = PayloadAction<{ error: MappingError[] }>;

const fastMapperSlice = createSlice({
  name: 'fastMapper',
  initialState: initialTestState,
  reducers: {
    showFastMapper: (state, { payload: { visible, entityId } }: ShowFastMapperAction) => {
      state.fastMapperVisible = visible;
      state.fastMapperEntityId = entityId;
      if (!visible) {
        state.saveMappingsStatus = FETCH_STATUS.IDLE;
        state.saveMappingsErrorMessage = '';
        state.saveMappingsResponse = null;
        state.mappingsStatus = FETCH_STATUS.IDLE;
        state.mappings = null;
        state.deleteMappingsResponse = null;
      }
    },
    showCreateField: (state, { payload }: ShowCreateFieldAction) => {
      state.createFieldModal = payload;
    },
    resetAddMappingModal: (state) => {
      state.saveMappingsStatus = FETCH_STATUS.IDLE;
      state.saveMappingsErrorMessage = '';
      state.saveMappingsResponse = null;
    },
    setAddMappingError: (state, { payload: { error } }: SetAddMappingErrorAction) => {
      state.saveMappingsStatus = FETCH_STATUS.ERROR;
      state.saveMappingsResponse = { success: false, error };
    },
    resetBrowseMappingModal: (state) => {
      state.editMappingsStatus = FETCH_STATUS.IDLE;
      state.editMappingsErrorMessage = '';
      state.editMappingsResponse = null;
      state.deleteMappingsResponse = null;
    },
    setEditMappingError: (state, { payload: { error } }: SetEditMappingErrorAction) => {
      state.editMappingsStatus = FETCH_STATUS.ERROR;
      state.editMappingsResponse = { success: false, error };
    },
  },
  extraReducers: {
    // Save mappings
    [saveMappings.pending.type]: (state) => {
      state.saveMappingsStatus = FETCH_STATUS.LOADING;
      state.saveMappingsErrorMessage = '';
      state.saveMappingsResponse = null;
    },
    [saveMappings.fulfilled.type]: (state, action) => {
      state.saveMappingsStatus = FETCH_STATUS.SUCCESS;
      state.saveMappingsResponse = action.payload;
    },
    [saveMappings.rejected.type]: (state, action) => {
      state.saveMappingsStatus = FETCH_STATUS.ERROR;
      state.saveMappingsErrorMessage = action.payload?.message;
    },

    // Edit mappings
    [editMappings.pending.type]: (state) => {
      state.editMappingsStatus = FETCH_STATUS.LOADING;
      state.editMappingsErrorMessage = '';
      state.editMappingsResponse = null;
    },
    [editMappings.fulfilled.type]: (state, action) => {
      state.editMappingsStatus = FETCH_STATUS.SUCCESS;
      state.editMappingsResponse = action.payload;
    },
    [editMappings.rejected.type]: (state, action) => {
      state.editMappingsStatus = FETCH_STATUS.ERROR;
      state.editMappingsErrorMessage = action.payload?.message;
    },

    // Get mappings
    [getMappings.pending.type]: (state) => {
      state.mappingsStatus = FETCH_STATUS.LOADING;
      state.mappings = null;
    },
    [getMappings.fulfilled.type]: (state, action) => {
      state.mappingsStatus = FETCH_STATUS.SUCCESS;
      state.mappings = action.payload.data;
    },

    // Delete mappings
    [deleteMappings.pending.type]: (state) => {
      state.deleteMappingsResponse = null;
    },
    [deleteMappings.fulfilled.type]: (state, action) => {
      const deletedIds = action.meta.arg.mappings.map((mapping: ServerMapping) => mapping.id);
      state.mappings = state.mappings?.filter((mapping) => !deletedIds.includes(mapping.id));
      state.deleteMappingsResponse = action.payload;
    },
  },
});

export const {
  reducer,
  actions: {
    showFastMapper,
    showCreateField,
    resetAddMappingModal,
    setAddMappingError,
    resetBrowseMappingModal,
    setEditMappingError,
  },
} = fastMapperSlice;
