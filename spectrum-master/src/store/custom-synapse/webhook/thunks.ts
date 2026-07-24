import { createAsyncThunk } from '@reduxjs/toolkit';
import { RcFile } from 'antd/lib/upload';

import { getConnectorsMetadata } from 'actions/connectorActions';
import { CustomSynapse } from 'components/custom-synapse/types';
import { RootState } from 'store/types';
import { post, put, RequestResponseExceptionType } from 'utils/AjaxUtil';
import DataUrlConstants from 'utils/DataUrlConstants';
import { makeUrl } from 'utils/UrlUtil';

import { SaveCustomSynapseRejected } from '../types';

export const createWebhookCustomSynapse = createAsyncThunk<
  CustomSynapse & { iconFile?: RcFile },
  Partial<CustomSynapse>,
  { state: RootState; rejectValue: SaveCustomSynapseRejected }
>('custom-synapse/createWebhook', async (params, { rejectWithValue, dispatch }) => {
  try {
    const payload = makeWebhookFormDataPayload(params);
    const response = await post<CustomSynapse>(DataUrlConstants.WEBHOOK_CUSTOM_SYNAPSE, payload);
    dispatch(getConnectorsMetadata());
    return response.data;
  } catch (error) {
    return rejectWithValue({
      message: (error as RequestResponseExceptionType).response.data?.message,
    });
  }
});

export const updateWebhookCustomSynapse = createAsyncThunk<
  CustomSynapse & { iconFile?: RcFile },
  Partial<CustomSynapse>,
  { state: RootState; rejectValue: SaveCustomSynapseRejected }
>('custom-synapse/updateWebhook', async (params, { rejectWithValue, dispatch }) => {
  try {
    const payload = makeWebhookFormDataPayload(params);
    const response = await put<CustomSynapse>(
      makeUrl(DataUrlConstants.WEBHOOK_CUSTOM_SYNAPSE_UPDATE_DRAFT, {
        metadataId: params.id,
      }),
      payload
    );
    dispatch(getConnectorsMetadata());
    return response.data;
  } catch (error) {
    return rejectWithValue({
      message: (error as RequestResponseExceptionType).response.data?.message,
    });
  }
});

const makeWebhookFormDataPayload = (params: Partial<CustomSynapse> & { iconFile?: RcFile }) => {
  const payload = new FormData();

  payload.append('displayName', params.displayName || '');
  payload.append('name', params.name || '');
  payload.append('authType', params.authType || '');
  payload.append('idSelector', params.idSelector || '');
  payload.append('recordSelector', params.recordSelector || '');
  payload.append('responseCode', params.responseCode?.toString() || '');

  params?.responseTemplate?.trim() && payload.append('responseTemplate', JSON.stringify(params.responseTemplate));
  params?.schema?.trim() && payload.append('schema', JSON.stringify(params.schema));
  params.iconFile && payload.append('iconFile', params.iconFile);

  return payload;
};
