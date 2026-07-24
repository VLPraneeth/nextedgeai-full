//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { createAsyncThunk } from '@reduxjs/toolkit';

import { EditedServerMapping, MappingsResponse, ServerMapping } from 'store/fast-mapper/types';
import { get, post, put } from 'utils/AjaxUtil';
import DataUrlConstants from 'utils/DataUrlConstants';
import { replaceToken } from 'utils/StringUtil';

export interface MappingRejected {
  message?: string;
}

export interface SaveMappingsArgs {
  entityId: string;
  mappings: ServerMapping[];
}

export interface EditMappingsArgs {
  entityId: string;
  editedMappings: EditedServerMapping[];
}

export const saveMappings = createAsyncThunk<MappingsResponse, SaveMappingsArgs, { rejectValue: MappingRejected }>(
  'fast-mapper/saveMapping',
  async ({ entityId, mappings }, { rejectWithValue }) => {
    try {
      return (await post<MappingsResponse>(replaceToken(DataUrlConstants.MAPPING, { entityId }), mappings)).data;
    } catch (error) {
      return rejectWithValue({
        message: (error as any).response.data?.message,
      });
    }
  }
);

export const editMappings = createAsyncThunk<MappingsResponse, EditMappingsArgs, { rejectValue: MappingRejected }>(
  'fast-mapper/editMappings',
  async ({ entityId, editedMappings }, { rejectWithValue }) => {
    try {
      return (await put<MappingsResponse>(replaceToken(DataUrlConstants.MAPPING, { entityId }), editedMappings)).data;
    } catch (error: any) {
      return rejectWithValue({
        message: error.response.data?.message ?? error.response.data?.error,
      });
    }
  }
);

export interface GetMappingsArgs {
  entityId: string;
}

export const getMappings = createAsyncThunk(
  'fast-mapper/getMapping',
  ({ entityId }: GetMappingsArgs, { rejectWithValue }) => {
    return get<Required<ServerMapping>[]>(replaceToken(DataUrlConstants.MAPPING, { entityId })).catch((error) =>
      rejectWithValue(error?.response?.data)
    );
  }
);

export interface DeleteMappingArgs {
  entityId: string;
  mappings: Pick<ServerMapping, 'id' | 'synapseFieldId' | 'syncariFieldId' | 'directions'>[];
}

export const deleteMappings = createAsyncThunk<MappingsResponse, DeleteMappingArgs, { rejectValue: MappingRejected }>(
  'fast-mapper/deleteMapping',
  async ({ entityId, mappings }: DeleteMappingArgs, { rejectWithValue }) => {
    try {
      return (await post<MappingsResponse>(replaceToken(DataUrlConstants.REMOVE_MAPPING, { entityId }), mappings)).data;
    } catch (error: any) {
      return rejectWithValue({
        message: error.response.data?.message ?? error.response.data?.error,
      });
    }
  }
);
