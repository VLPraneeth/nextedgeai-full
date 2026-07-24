//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { createAsyncThunk } from '@reduxjs/toolkit';

import { post, RequestResponseExceptionType } from 'utils/AjaxUtil';
import DataUrlConstants from 'utils/DataUrlConstants';

import { tags } from '../api';
import { importedFilesApiUtil } from './api';
import {
  UploadFilePayload,
  UploadFileResponse,
  UploadFolder,
  UploadFolderPayload,
  UploadFolderRejected,
} from './types';

const makeFormDataFilePayload = (params: UploadFilePayload) => {
  const payload = new FormData();
  payload.append('file', params.file);
  payload.append('name', params.name);
  payload.append('idColumn', params.idColumn);
  payload.append('withTrim', JSON.stringify(params.withTrim));
  params.folderId && payload.append('folderId', params.folderId);
  if (params.tags.length > 0) {
    params.tags.forEach((item) => payload.append('tags', item));
  } else {
    payload.append('tags', '');
  }

  return payload;
};

// This could be in an .api file but we're hoping to include the file data here
// as well once we can figure out how to add an array to the FormData payload
export const createImportFolder = createAsyncThunk<
  UploadFolder,
  UploadFolderPayload,
  { rejectValue: UploadFolderRejected }
>('custom-synapse/createImportFolder', async (params, { rejectWithValue }) => {
  try {
    const response = await post<UploadFolder>(DataUrlConstants.IMPORTED_FILES_FOLDERS, params);
    return response.data;
  } catch (error) {
    return rejectWithValue({
      message: (error as RequestResponseExceptionType).response.data?.message,
    });
  }
});

export const createImportFile = createAsyncThunk<
  UploadFileResponse,
  UploadFilePayload,
  { rejectValue: UploadFolderRejected }
>('custom-synapse/createImportFile', async (params, { rejectWithValue, dispatch }) => {
  try {
    const payload = makeFormDataFilePayload(params);
    const response = await post<UploadFolder>(DataUrlConstants.IMPORT_FILE, payload);

    // Manually dispatch invalidateTags to refresh the list data
    dispatch(importedFilesApiUtil.invalidateTags([tags.ImportedFilesList]));
    return response.data;
  } catch (error) {
    return rejectWithValue({
      message: (error as RequestResponseExceptionType).response.data?.message,
    });
  }
});
