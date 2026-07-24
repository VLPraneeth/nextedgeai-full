import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';

import { FetchStatus } from 'store/types';
import { deleteRequest } from 'utils/AjaxUtil';
import AppConstants from 'utils/AppConstants';
import DataUrlConstants from 'utils/DataUrlConstants';
import { makeUrl } from 'utils/UrlUtil';

export interface SchemaState {
  deletingFieldStatus: FetchStatus;
  deletingFieldError: string | null;
}

export const initialState: SchemaState = {
  deletingFieldStatus: AppConstants.FETCH_STATUS.IDLE,
  deletingFieldError: null,
};

export interface UpdateDeleteSchemaFieldArgs {
  entityId: string;
  fieldId: string;
}

export const deleteSchemaField = createAsyncThunk(
  'schema/field/delete',
  ({ entityId, fieldId }: UpdateDeleteSchemaFieldArgs, { rejectWithValue }) =>
    deleteRequest(makeUrl(DataUrlConstants.UPDATE_FIELD, { entityId, fieldId }))
      .then((resp) => resp.data)
      .catch((error) => rejectWithValue(error?.response?.data?.message))
);

const schemaSlice = createSlice({
  name: 'schema',
  initialState,
  reducers: {},
  extraReducers: (builder) => {
    builder.addCase(deleteSchemaField.pending, (state, action) => {
      state.deletingFieldStatus = AppConstants.FETCH_STATUS.LOADING;
      state.deletingFieldError = null;
    });

    builder.addCase(deleteSchemaField.fulfilled, (state, action) => {
      state.deletingFieldStatus = AppConstants.FETCH_STATUS.SUCCESS;
    });

    builder.addCase(deleteSchemaField.rejected, (state, action) => {
      state.deletingFieldStatus = AppConstants.FETCH_STATUS.ERROR;
      state.deletingFieldError = typeof action.payload === 'string' ? action.payload : 'An unknown error occurred';
    });
  },
});

export const { reducer } = schemaSlice;
