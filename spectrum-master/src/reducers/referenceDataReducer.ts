// @ts-nocheck
//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { map } from 'lodash';

import { ActionTypes } from 'actions/referenceDataActions';
import AppConstants from 'utils/AppConstants';
import { getReducerDefaultValues } from 'utils/LocalStorageUtil';

function _transformReferenceMetaData(data) {
  const referenceMetaDataList = [];
  map(data, (refData) => {
    const newRefData = {
      key: refData.id,
      ...refData,
    };
    referenceMetaDataList.push(newRefData);
  });
  return referenceMetaDataList;
}

function _getMetaData(data) {
  let referenceMetaDataList = [];
  if (data) {
    referenceMetaDataList = _transformReferenceMetaData(data);
  }
  return { referenceMetaDataList };
}

function _transformReferenceData(data) {
  const referenceDataPreviewRows = [];
  map(data, (refData) => {
    const newRefData = {
      key: refData.id,
      ...refData,
    };
    referenceDataPreviewRows.push(newRefData);
  });
  return referenceDataPreviewRows;
}

function _previewData(data) {
  let referenceDataPreviewRows = [];
  if (data) {
    referenceDataPreviewRows = _transformReferenceData(data);
  }
  return { referenceDataPreviewRows };
}

export function _getDefaultState() {
  return {
    ...getReducerDefaultValues(AppConstants.REDUCER_NAME.REFERENCE_DATA),
    fetchingRefDataMeta: false,
  };
}

export default function referenceDataReducer(state = _getDefaultState(), action) {
  switch (action.type) {
    case ActionTypes.GET_REFERENCE_DATA_PENDING:
      return {
        ...state,
        fetchingRefDataMeta: true,
      };
    case ActionTypes.GET_REFERENCE_DATA_FULFILLED:
      return {
        ...state,
        fetchingRefDataMeta: false,
        ..._getMetaData(action.referenceMetaDataList),
      };
    case ActionTypes.GET_REFERENCE_DATA_FAILED:
      return {
        ...state,
        fetchingRefDataMeta: false,
      };
    case ActionTypes.SHOW_REFERENCE_DATA_MODAL:
      return {
        ...state,
        refDataModalVisible: action.visible,
      };
    case ActionTypes.SHOW_REFERENCE_DATA_PREVIEW_MODAL:
      return {
        ...state,
        refDataPreviewModalVisible: action.visible,
      };
    case ActionTypes.PREVIEW_REFERENCE_DATA_FULFILLED:
      return {
        ...state,
        referenceDataPreviewHeader: action.referenceDataPreviewHeader,
        ..._previewData(action.referenceDataPreviewRows),
        refDataPreviewModalVisible: true,
      };
    case ActionTypes.SHOW_REFERENCE_DATA_DELETE_MODAL:
      return {
        ...state,
        refDataDeleteModalVisible: action.visible,
      };
    case ActionTypes.SHOW_REFERENCE_DATA_UPDATE_MODAL:
      return {
        ...state,
        refDataUpdateModalVisible: action.visible,
      };
    case ActionTypes.GET_SELECTED_REFERENCE_DATA:
      return {
        ...state,
        selectedReferenceData: action.selectedRows,
      };
    case ActionTypes.REMOVE_REFERENCE_DATA_ERROR:
    case ActionTypes.CREATE_REFERENCE_DATA:
    case ActionTypes.UPDATE_REFERENCE_DATA:
      return {
        ...state,
        error: '',
      };
    case ActionTypes.CREATE_REFERENCE_DATA_FAILED:
    case ActionTypes.UPDATE_REFERENCE_DATA_FAILED:
      return {
        ...state,
        error: action.errorMessage,
      };
    default:
      return state;
  }
}
