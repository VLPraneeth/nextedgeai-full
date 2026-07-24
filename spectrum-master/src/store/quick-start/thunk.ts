//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { createAsyncThunk } from '@reduxjs/toolkit';

import { EMPTY_ARRAY } from 'store/constants';
import { SaveQuickStartResponse, QuickStartDynamicStep, SaveQuickStartRejected } from 'store/quick-start/types';
import { post, RequestResponseExceptionType } from 'utils/AjaxUtil';
import DataUrlConstants from 'utils/DataUrlConstants';

export const saveQuickStart = createAsyncThunk<
  SaveQuickStartResponse,
  QuickStartDynamicStep,
  { rejectValue: SaveQuickStartRejected }
>('quick-start/saveQuickStart', async (params, { rejectWithValue }) => {
  try {
    const reqPayload = new FormData();
    params.id && reqPayload.append('id', params.id);
    reqPayload.append('displayName', params.displayName);
    reqPayload.append('pipelines', JSON.stringify(params.pipelines));
    reqPayload.append('publishToQuickStartLibrary', params.publishToQuickStartLibrary);
    reqPayload.append('shareWithInstances', JSON.stringify(params.shareWithInstances || EMPTY_ARRAY));
    reqPayload.append('postInstallationInstruction', params.postInstallationInstruction || '');
    reqPayload.append('description', params.description || '');
    reqPayload.append('tags', JSON.stringify(params.tags || EMPTY_ARRAY));
    reqPayload.append('shareWithOrg', params.shareWithOrg ?? false);
    // Note that iconPath becomes an icon on submit of type File
    // The backend is expecting it as icon if the user pick or modify the icon
    params.iconPath?.name && reqPayload.append('icon', params.iconPath);

    return (await post<SaveQuickStartResponse>(DataUrlConstants.QUICK_START_CREATE_QUICK_START, reqPayload)).data;
  } catch (error) {
    return rejectWithValue({
      message: (error as RequestResponseExceptionType).response.data?.message,
    });
  }
});
