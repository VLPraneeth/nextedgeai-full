import { createAsyncThunk, createSlice, PayloadAction, SerializedError } from '@reduxjs/toolkit';
import { find } from 'lodash';

import { FetchStatus } from 'store/types';
import { deleteRequest, get } from 'utils/AjaxUtil';
import AppConstants from 'utils/AppConstants';
import DataUrlConstants from 'utils/DataUrlConstants';
import { getReducerDefaultValues } from 'utils/LocalStorageUtil';
import { makeUrl } from 'utils/UrlUtil';

import { ServiceCredential } from './types';

export interface ServiceCredentialsState {
  fetchingCredentials: FetchStatus;
  credentials: ServiceCredential[];
  credentialModal: boolean;
  credentialData?: Partial<ServiceCredential>;
  addCredentialResponse?: {};
  fetchingCredentialsError: SerializedError | null;
  deleteCredentialStatusById: Record<string, FetchStatus>;
  deleteCredentialErrorById: Record<string, string | null>;
}

export type ShowCredentialModalAction = PayloadAction<{
  visible: boolean;
  credentialId?: string;
}>;

export const _getDefaultState = (): ServiceCredentialsState => {
  return {
    // You can override the default values of the instance by putting some
    // values in the local storage
    ...getReducerDefaultValues(AppConstants.REDUCER_NAME.CREDENTIAL),
    fetchingCredentials: AppConstants.FETCH_STATUS.IDLE,
    credentialModal: false,
    fetchingCredentialsError: null,
    deleteCredentialStatusById: {},
    deleteCredentialErrorById: {},
  };
};

const initialState = _getDefaultState();

export const getCredentials = createAsyncThunk<ServiceCredential[]>('credentials/get', () =>
  get<ServiceCredential[]>(DataUrlConstants.SERVICE_CREDENTIAL).then((resp) => resp.data)
);

export interface DeleteCredentialArgs {
  credentialId: string;
}

export const deleteCredential = createAsyncThunk(
  'credentials/delete',
  ({ credentialId }: DeleteCredentialArgs, { rejectWithValue }) =>
    deleteRequest<string | null>(makeUrl(DataUrlConstants.SERVICE_CREDENTIAL_ITEM, { credentialId })).catch((error) =>
      rejectWithValue(error?.response?.data?.message)
    )
);

const credentialsSlice = createSlice({
  name: 'credentials',
  initialState,
  reducers: {
    showCredentialModal: (state, action: ShowCredentialModalAction) => {
      const { visible, credentialId } = action.payload;
      state.credentialModal = visible;
      state.credentialData = find(state.credentials, { id: credentialId });
    },
  },
  extraReducers: (builder) => {
    // Get Credentials
    builder.addCase(getCredentials.pending, (state, action) => {
      state.fetchingCredentials = AppConstants.FETCH_STATUS.LOADING;
      state.fetchingCredentialsError = null;
    });

    builder.addCase(getCredentials.fulfilled, (state, action) => {
      state.fetchingCredentials = AppConstants.FETCH_STATUS.SUCCESS;
      state.credentials = action.payload;
    });

    builder.addCase(getCredentials.rejected, (state, action) => {
      state.fetchingCredentials = AppConstants.FETCH_STATUS.ERROR;
      state.fetchingCredentialsError = action.error;
    });

    // Delete Credential
    builder.addCase(deleteCredential.pending, (state, action) => {
      const { credentialId } = action.meta.arg;

      state.deleteCredentialStatusById[credentialId] = AppConstants.FETCH_STATUS.LOADING;
      state.deleteCredentialErrorById[credentialId] = null;
    });

    builder.addCase(deleteCredential.fulfilled, (state, action) => {
      const { credentialId } = action.meta.arg;

      state.deleteCredentialStatusById[credentialId] = AppConstants.FETCH_STATUS.SUCCESS;
      state.credentials = state.credentials.filter(({ id }) => id !== credentialId);
    });

    builder.addCase(deleteCredential.rejected, (state, action) => {
      const { credentialId } = action.meta.arg;

      state.deleteCredentialStatusById[credentialId] = AppConstants.FETCH_STATUS.ERROR;
      state.deleteCredentialErrorById[credentialId] =
        typeof action.payload === 'string' ? action.payload : 'An unknown error occurred.';
    });
  },
});

export const {
  reducer,
  actions: { showCredentialModal },
} = credentialsSlice;
