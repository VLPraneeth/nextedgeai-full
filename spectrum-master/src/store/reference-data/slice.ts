import { createEntityAdapter, createSlice } from '@reduxjs/toolkit';

import AppConstants from 'utils/AppConstants';

import {
  changeActivationStatus,
  clearReferenceDataUpsertError,
  getReferenceDataPreview,
  upsertReferenceData,
} from './thunks';
import { ExtraStateShape, ReferenceDataRecord } from './types';

/**
 * Helper to create an empty ReferenceDataRecord. This object is intentionally empty
 * AND missing id in order to force merging onto another partial ReferenceDataRecord.
 *
 * This is particularly useful for upsert of Preview before the list/metadata for the
 * RefData has returned
 */
const createEmptyReferenceDataRecord = (): Omit<ReferenceDataRecord, 'id'> => ({
  accessKey: null,
  csvFile: null,
  importDetails: null,
  key: '',
  lastImported: '',
  location: '',
  name: '',
  secretKey: null,
  status: 'NEW',
  standard: false,
  totalRecords: '',
  type: '',
  usedInPipelines: [],
  preview: undefined,
});

const referenceDataAdapter = createEntityAdapter<ReferenceDataRecord>({
  selectId: (refData) => refData.id,
  sortComparer: (a, b) => a.name.localeCompare(b.name),
});

export const selectors = referenceDataAdapter.getSelectors();

const referenceDataSlice = createSlice({
  name: 'referenceData',
  initialState: referenceDataAdapter.getInitialState<ExtraStateShape>({
    previewStatus: {},
    previewError: {},
    upsertStatus: {},
    upsertError: {},
  }),
  reducers: {},
  extraReducers: (builder) => {
    // get Reference Data Preview
    builder.addCase(getReferenceDataPreview.pending, (state, action) => {
      const referenceDataId = action.meta.arg;
      state.previewStatus[referenceDataId] = AppConstants.FETCH_STATUS.LOADING;
      delete state.previewError[referenceDataId];
    });

    builder.addCase(getReferenceDataPreview.fulfilled, (state, action) => {
      const referenceDataId = action.meta.arg;
      state.previewStatus[referenceDataId] = AppConstants.FETCH_STATUS.SUCCESS;

      const existingEntity = selectors.selectById(state, referenceDataId);

      if (existingEntity) {
        referenceDataAdapter.updateOne(state, {
          id: referenceDataId,
          changes: {
            preview: action.payload,
          },
        });
      } else {
        referenceDataAdapter.addOne(state, {
          ...createEmptyReferenceDataRecord(),
          id: referenceDataId,
          preview: action.payload,
        });
      }
    });

    builder.addCase(getReferenceDataPreview.rejected, (state, action) => {
      const referenceDataId = action.meta.arg;
      state.previewStatus[referenceDataId] = AppConstants.FETCH_STATUS.ERROR;
      state.previewError[referenceDataId] = action.payload as string;
    });

    // Upsert Reference Data
    builder.addCase(upsertReferenceData.pending, (state, action) => {
      const { id = 'new' } = action.meta.arg;
      state.upsertStatus[id] = AppConstants.FETCH_STATUS.LOADING;
      delete state.upsertError[id];
    });

    builder.addCase(upsertReferenceData.fulfilled, (state, action) => {
      const { id = 'new' } = action.meta.arg;
      state.upsertStatus[id] = AppConstants.FETCH_STATUS.SUCCESS;
      referenceDataAdapter.upsertOne(state, action.payload);
    });

    builder.addCase(upsertReferenceData.rejected, (state, action) => {
      const { id = 'new' } = action.meta.arg;
      state.upsertStatus[id] = AppConstants.FETCH_STATUS.ERROR;
      state.upsertError[id] = action.payload as string;
    });

    // Clear upsert error action
    builder.addCase(clearReferenceDataUpsertError.fulfilled, (state, action) => {
      const id = action.meta.arg;
      delete state.upsertError[id];
      delete state.upsertStatus[id];
    });

    // Change Activation Status
    builder.addCase(changeActivationStatus.pending, (state, action) => {
      const { referenceDataId: id } = action.meta.arg;
      state.upsertStatus[id] = AppConstants.FETCH_STATUS.LOADING;
      delete state.upsertError[id];
    });

    builder.addCase(changeActivationStatus.fulfilled, (state, action) => {
      const { referenceDataId: id, activationStatus: status } = action.meta.arg;
      state.upsertStatus[id] = AppConstants.FETCH_STATUS.SUCCESS;
      referenceDataAdapter.updateOne(state, {
        id,
        changes: { status },
      });
    });

    builder.addCase(changeActivationStatus.rejected, (state, action) => {
      const { referenceDataId: id } = action.meta.arg;
      state.upsertStatus[id] = AppConstants.FETCH_STATUS.ERROR;
      state.upsertError[id] = action.payload as string;
    });
  },
});

type ReferenceDataState = ReturnType<typeof referenceDataSlice.reducer>;
const { reducer } = referenceDataSlice;
export { reducer, ReferenceDataState };
