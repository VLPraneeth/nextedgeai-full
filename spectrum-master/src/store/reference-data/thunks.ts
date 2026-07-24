import { createAsyncThunk } from '@reduxjs/toolkit';
import { RcFile } from 'antd/lib/upload';

import { get, post, put } from 'utils/AjaxUtil';
import AppConstants from 'utils/AppConstants';
import DataUrlConstants from 'utils/DataUrlConstants';
import { t } from 'utils/i18nUtil';
import { makeUrl } from 'utils/UrlUtil';

import { ReferenceDataPreview, ReferenceDataRecord } from './types';

export const getReferenceDataPreview = createAsyncThunk<ReferenceDataPreview, string>(
  'referenceData/preview',
  (refMetaId: string, { rejectWithValue }) =>
    get<ReferenceDataPreview>(makeUrl(DataUrlConstants.PREVIEW_REF_DATA, { refMetaId }, { numberOfRows: 50 }))
      .then((resp) => resp.data)
      .catch((err) =>
        rejectWithValue(err?.response?.data?.message || 'There was an issue fetching Reference Data Preview.')
      )
);

export type UpsertReferenceDataParams = Pick<ReferenceDataRecord, 'name' | 'type'> &
  Partial<Pick<ReferenceDataRecord, 'id' | 'secretKey' | 'accessKey'>> & {
    fileName?: string;
    csvFile: RcFile | null;
  };

const { REFDATAMETA_CONST } = AppConstants;

export const upsertReferenceData = createAsyncThunk<ReferenceDataRecord, UpsertReferenceDataParams>(
  'referenceData/upsert',
  ({ id, name, type, secretKey, accessKey, fileName, csvFile }: UpsertReferenceDataParams, { rejectWithValue }) => {
    const payload = new FormData();
    payload.append(REFDATAMETA_CONST.NAME, name);
    payload.append(REFDATAMETA_CONST.TYPE, type);
    payload.append(REFDATAMETA_CONST.META_ID, id || '');
    payload.append(REFDATAMETA_CONST.FILE, (csvFile as Blob) || '');
    payload.append(REFDATAMETA_CONST.FILENAME, fileName || '');
    payload.append(REFDATAMETA_CONST.SECRETKEY, secretKey || '');
    payload.append(REFDATAMETA_CONST.ACCESSKEY, accessKey || '');

    const fn = !!id ? put : post;

    return fn<ReferenceDataRecord>(DataUrlConstants.REFERENCE_DATA, payload)
      .then((resp) => resp.data)
      .catch((err) =>
        rejectWithValue(err?.response?.data?.message || err?.message || t('ReferenceDataModal.error_uploading'))
      );
  }
);

export type ChangeActivationStatusParams = {
  referenceDataId: string;
  activationStatus: 'ACTIVE' | 'INACTIVE';
};

export const changeActivationStatus = createAsyncThunk(
  'referenceData/status/change',
  ({ referenceDataId: refMetaId, activationStatus }: ChangeActivationStatusParams, { rejectWithValue }) =>
    post(
      makeUrl(
        activationStatus === 'ACTIVE' ? DataUrlConstants.ACTIVATE_REF_DATA : DataUrlConstants.DEACTIVATE_REF_DATA,
        { refMetaId }
      )
    )
      .then((resp) => resp.data)
      .catch((err) => rejectWithValue(err.message || 'Unable to change activation status of Reference Data.'))
);

export const activateReferenceData = (referenceDataId: string) =>
  changeActivationStatus({ referenceDataId, activationStatus: 'ACTIVE' });
export const deactivateReferenceData = (referenceDataId: string) =>
  changeActivationStatus({ referenceDataId, activationStatus: 'INACTIVE' });

/**
 * Thunk to clear upsert error for a reference data record
 */
export const clearReferenceDataUpsertError = createAsyncThunk<void, string>('referenceData/clearUpsertError', () => {
  // Thunk returns void, slice will handle clearing via fulfilled action
  return;
});
