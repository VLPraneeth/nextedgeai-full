import { createAsyncThunk } from '@reduxjs/toolkit';
import { RcFile } from 'antd/lib/upload';

import { getConnectorsMetadata } from 'actions/connectorActions';
import { CustomSynapse } from 'components/custom-synapse/types';
import { RootState } from 'store/types';
import { post, put, RequestResponseExceptionType } from 'utils/AjaxUtil';
import DataUrlConstants from 'utils/DataUrlConstants';
import { makeUrl } from 'utils/UrlUtil';

import { SaveCustomSynapseRejected } from '../types';

export const createHTTPCustomSynapse = createAsyncThunk<
  CustomSynapse & { iconFile?: RcFile },
  Partial<CustomSynapse>,
  { state: RootState; rejectValue: SaveCustomSynapseRejected }
>('custom-synapse/createHttp', async (params, { rejectWithValue, dispatch }) => {
  try {
    const payload = makeHTTPFormDataPayload(params);
    const response = await post<CustomSynapse>(DataUrlConstants.HTTP_CUSTOM_SYNAPSE, payload);
    dispatch(getConnectorsMetadata());
    return response.data;
  } catch (error) {
    return rejectWithValue({
      message: (error as RequestResponseExceptionType).response.data?.message,
    });
  }
});

export const updateHTTPCustomSynapse = createAsyncThunk<
  CustomSynapse & { iconFile?: RcFile },
  Partial<CustomSynapse>,
  { state: RootState; rejectValue: SaveCustomSynapseRejected }
>('custom-synapse/updateHttp', async (params, { rejectWithValue, dispatch }) => {
  try {
    const payload = makeHTTPFormDataPayload(params);
    const response = await put<CustomSynapse>(
      makeUrl(DataUrlConstants.HTTP_CUSTOM_SYNAPSE_UPDATE_DRAFT, {
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

const makeHTTPFormDataPayload = (params: Partial<CustomSynapse> & { iconFile?: RcFile }) => {
  const payload = new FormData();

  payload.append('displayName', params.displayName || '');
  payload.append('name', params.name || '');
  payload.append('endpoint', params.endpoint || '');
  payload.append('method', params.method || '');
  payload.append('authType', params.authType || '');
  payload.append('authConfig', JSON.stringify(params.authConfig ? params.authConfig : {}));
  params?.body?.trim() && payload.append('body', JSON.stringify(params.body));
  payload.append('headers', JSON.stringify(params.headers ? params.headers : {}));
  payload.append('variableValues', JSON.stringify(params.variableValues ? params.variableValues : []));
  payload.append('variables', JSON.stringify(params.variables?.length ? params.variables : []));

  params.iconFile && payload.append('iconFile', params.iconFile);

  return payload;
};
