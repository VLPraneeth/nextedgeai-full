//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { createAsyncThunk } from '@reduxjs/toolkit';

import { post, put, RequestResponseExceptionType } from 'utils/AjaxUtil';
import DataUrlConstants from 'utils/DataUrlConstants';
import { makeUrl } from 'utils/UrlUtil';

import {
  SDKCustomSynapseCreatePayload,
  CustomSynapseCreateResponse,
  SaveCustomSynapseRejected,
  UpdateCustomSynapseResponse,
} from '../types';

const makeSDKFormDataPayload = (params: SDKCustomSynapseCreatePayload) => {
  const payload = new FormData();
  payload.append('connectorMetaName', params.connectorMetaName);
  payload.append('connectorMetaDisplayName', params.connectorMetaDisplayName);
  payload.append('publishToGlobal', `${params.publishToGlobal}`);
  params.iconFile && payload.append('iconFile', params.iconFile);

  // Only add the files if they're included in the params. Files are not required for updates.
  if (params.synapseFile && params.requirementsFile) {
    payload.append('synapseFile', params.synapseFile);
    payload.append('requirementsFile', params.requirementsFile);
  }

  return payload;
};

export const createSDKCustomSynapse = createAsyncThunk<
  CustomSynapseCreateResponse,
  SDKCustomSynapseCreatePayload,
  { rejectValue: SaveCustomSynapseRejected }
>('custom-synapse/createSDKCustomSynapse', async (params, { rejectWithValue }) => {
  try {
    const payload = makeSDKFormDataPayload(params);
    const response = await post<CustomSynapseCreateResponse>(DataUrlConstants.SDK_CUSTOM_SYNAPSE, payload);
    return response.data;
  } catch (error) {
    return rejectWithValue({
      message: (error as RequestResponseExceptionType).response.data?.message,
    });
  }
});

export const updateSDKCustomSynapse = createAsyncThunk<
  UpdateCustomSynapseResponse,
  SDKCustomSynapseCreatePayload,
  { rejectValue: SaveCustomSynapseRejected }
>('custom-synapse/updateSDKCustomSynapse', async (params, { rejectWithValue }) => {
  try {
    const response = await put<UpdateCustomSynapseResponse>(
      makeUrl(DataUrlConstants.SDK_CUSTOM_SYNAPSE_UPDATE_DRAFT, {
        connectorMetaDefinitionId: params.connectorMetaDefinitionId,
      }),

      makeSDKFormDataPayload(params)
    );
    return response.data;
  } catch (error) {
    return rejectWithValue({
      message: (error as RequestResponseExceptionType).response.data?.message,
    });
  }
});
