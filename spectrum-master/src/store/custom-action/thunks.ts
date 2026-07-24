//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { createAsyncThunk } from '@reduxjs/toolkit';

import { EMPTY_ARRAY, EMPTY_OBJECT } from 'store/constants';
import { RequestResponseExceptionType, post } from 'utils/AjaxUtil';
import AppConstants from 'utils/AppConstants';
import DataUrlConstants from 'utils/DataUrlConstants';

import {
  CustomActionPayload,
  CustomActionTestingPayload,
  SaveCustomActionRejected,
  SaveCustomActionResponse,
} from './types';

const makeFormDataPayload = (params: CustomActionPayload) => {
  const reqPayload = new FormData();
  params.id && reqPayload.append('id', params.id);
  reqPayload.append('displayName', params.displayName || '');
  reqPayload.append('description', params.description || '');
  reqPayload.append('apiName', params.apiName || '');
  reqPayload.append('basicHelpText', params.basicHelpText || '');
  reqPayload.append('helpLink', params.helpLink || '');

  reqPayload.append('isBatch', params.isBatch || '');
  reqPayload.append('batchSize', params.batchSize || '');
  reqPayload.append('body', params.body || '');
  reqPayload.append('method', params.method || '');
  reqPayload.append('endpoint', params.endpoint || '');
  reqPayload.append('credentialId', params.credentialId || '');
  reqPayload.append('metadataId', params.metadataId || '');
  if (params.variableValues) {
    reqPayload.append('variableValues', JSON.stringify(params.variableValues || EMPTY_ARRAY));
  }
  reqPayload.append(
    'variables',
    JSON.stringify(
      params.variables?.map((variable) => {
        return {
          ...variable,
          multivalued: variable.multivalued === AppConstants.TRUE,
          required: variable.required === AppConstants.TRUE,
        };
      }) || EMPTY_ARRAY
    )
  );
  reqPayload.append('headers', JSON.stringify(params.headers || EMPTY_OBJECT));
  reqPayload.append('tags', JSON.stringify(params.tags || EMPTY_ARRAY));

  // Note that iconPath becomes an icon on submit of type File
  // The backend is expecting it as icon if the user pick or modify the icon
  params.iconPath?.name && reqPayload.append('icon', params.iconPath);

  return reqPayload;
};

export const saveCustomAction = createAsyncThunk<
  SaveCustomActionResponse,
  CustomActionPayload,
  { rejectValue: SaveCustomActionRejected }
>('custom-action/saveCustomAction', async (params, { rejectWithValue }) => {
  try {
    return (await post<SaveCustomActionResponse>(DataUrlConstants.CUSTOM_ACTION, makeFormDataPayload(params))).data;
  } catch (error) {
    return rejectWithValue({
      message: (error as RequestResponseExceptionType).response.data?.message,
    });
  }
});

export const validateCustomAction = createAsyncThunk<
  SaveCustomActionResponse,
  CustomActionPayload,
  { rejectValue: SaveCustomActionRejected }
>('custom-action/validateCustomAction', async (params, { rejectWithValue }) => {
  try {
    return (await post<SaveCustomActionResponse>(DataUrlConstants.CUSTOM_ACTION_VALIDATE, makeFormDataPayload(params)))
      .data;
  } catch (error) {
    return rejectWithValue({
      message: (error as RequestResponseExceptionType).response.data?.message,
    });
  }
});

export const testCustomAction = createAsyncThunk<
  SaveCustomActionResponse,
  CustomActionTestingPayload,
  { rejectValue: SaveCustomActionRejected }
>('custom-action/testingCustomAction', async (params, { rejectWithValue }) => {
  try {
    return (await post<SaveCustomActionResponse>(DataUrlConstants.CUSTOM_ACTION_TESTING, makeFormDataPayload(params)))
      .data;
  } catch (error) {
    // error.response.data has the response value from the backend which
    // allows us to show the test request/response even when the request fails.
    const data = (error as any)?.response?.data;
    if (data?.response) {
      return data;
    }

    return rejectWithValue({
      message: (error as RequestResponseExceptionType).response.data?.message,
    });
  }
});
