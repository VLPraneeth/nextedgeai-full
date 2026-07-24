//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { first } from 'lodash';

import { SyncariThunkDispatch } from 'hooks/redux';
import { deleteRequest, get, post, put } from 'utils/AjaxUtil';
import AppConstants from 'utils/AppConstants';
import { getErrorMessage } from 'utils/AppUtil';
import DataUrlConstants from 'utils/DataUrlConstants';
import { replaceToken } from 'utils/UrlUtil';

// prettier-ignore
// Action types that are dispatched by this action
export const ActionTypes = {
  GET_REFERENCE_DATA            : 'GET_REFERENCE_DATA',
  GET_REFERENCE_DATA_PENDING    : 'GET_REFERENCE_DATA_PENDING',
  GET_REFERENCE_DATA_FULFILLED  : 'GET_REFERENCE_DATA_FULFILLED',
  GET_REFERENCE_DATA_FAILED     : 'GET_REFERENCE_DATA_FAILED',

  ACTIVATE_REFERENCE_DATA           : 'ACTIVATE_REFERENCE_DATA',
  ACTIVATE_REFERENCE_DATA_PENDING   : 'ACTIVATE_REFERENCE_DATA_PENDING',
  ACTIVATE_REFERENCE_DATA_FULFILLED : 'ACTIVATE_REFERENCE_DATA_FULFILLED',
  ACTIVATE_REFERENCE_DATA_FAILED    : 'ACTIVATE_REFERENCE_DATA_FAILED',

  DEACTIVATE_REFERENCE_DATA           : 'DEACTIVATE_REFERENCE_DATA',
  DEACTIVATE_REFERENCE_DATA_PENDING   : 'DEACTIVATE_REFERENCE_DATA_PENDING',
  DEACTIVATE_REFERENCE_DATA_FULFILLED : 'DEACTIVATE_REFERENCE_DATA_FULFILLED',
  DEACTIVATE_REFERENCE_DATA_FAILED    : 'DEACTIVATE_REFERENCE_DATA_FAILED',

  DELETE_REFERENCE_DATA           : 'DELETE_REFERENCE_DATA',
  DELETE_REFERENCE_DATA_PENDING   : 'DELETE_REFERENCE_DATA_PENDING',
  DELETE_REFERENCE_DATA_FULFILLED : 'DELETE_REFERENCE_DATA_FULFILLED',
  DELETE_REFERENCE_DATA_FAILED    : 'DELETE_REFERENCE_DATA_FAILED',

  SHOW_REFERENCE_DATA_MODAL      : 'SHOW_REFERENCE_DATA_MODAL',

  CREATE_REFERENCE_DATA          : 'CREATE_REFERENCE_DATA',
  CREATE_REFERENCE_DATA_FUFILLED : 'CREATE_REFERENCE_DATA_FUFILLED',
  CREATE_REFERENCE_DATA_FAILED   : 'CREATE_REFERENCE_DATA_FAILED',

  UPDATE_REFERENCE_DATA                 : 'UPDATE_REFERENCE_DATA',
  UPDATE_REFERENCE_DATA_FAILED          : 'UPDATE_REFERENCE_DATA_FAILED',

  REMOVE_REFERENCE_DATA_ERROR           : 'REMOVE_REFERENCE_DATA_ERROR',

  PREVIEW_REFERENCE_DATA                : 'PREVIEW_REFERENCE_DATA',
  PREVIEW_REFERENCE_DATA_PENDING        : 'PREVIEW_REFERENCE_DATA_PENDING',
  PREVIEW_REFERENCE_DATA_FULFILLED      : 'PREVIEW_REFERENCE_DATA_FULFILLED',
  PREVIEW_REFERENCE_DATA_FAILED         : 'PREVIEW_REFERENCE_DATA_FAILED',
  SHOW_REFERENCE_DATA_PREVIEW_MODAL     : 'SHOW_REFERENCE_DATA_PREVIEW_MODAL',
  SHOW_REFERENCE_DATA_DELETE_MODAL      : 'SHOW_REFERENCE_DATA_DELETE_MODAL',
  SHOW_REFERENCE_DATA_UPDATE_MODAL      : 'SHOW_REFERENCE_DATA_UPDATE_MODAL',
  GET_SELECTED_REFERENCE_DATA           : 'GET_SELECTED_REFERENCE_DATA',
};

/**
 * Get the list of metadata for reference data
 */
export function getReferenceDataMetas() {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.GET_REFERENCE_DATA_PENDING,
    });

    return get(DataUrlConstants.REFERENCE_DATA)
      .then((resp) => {
        dispatch({
          type: ActionTypes.GET_REFERENCE_DATA_FULFILLED,
          referenceMetaDataList: resp.data,
        });
      })
      .catch((error) => {
        dispatch({
          type: ActionTypes.GET_REFERENCE_DATA_FAILED,
        });
      });
  };
}

/**
 * Get the sample reference data
 */
export function previewReferenceData(id: string) {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.PREVIEW_REFERENCE_DATA_PENDING,
    });
    const url = replaceToken(DataUrlConstants.PREVIEW_REF_DATA, {
      refMetaId: id,
    });

    return get(url)
      .then((resp) => {
        dispatch({
          type: ActionTypes.PREVIEW_REFERENCE_DATA_FULFILLED,
          referenceDataPreviewRows: resp.data.rows,
          referenceDataPreviewHeader: resp.data.headerColumns,
        });
      })
      .catch((error) => {
        dispatch({
          type: ActionTypes.PREVIEW_REFERENCE_DATA_FAILED,
        });
      });
  };
}

/**
 * Show the ref data modal
 * @param {Boolean} show - make the ref data modal visible
 *                         default to show the ref data otherwise, false
 */
export function showReferenceDataModal(show = true) {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.SHOW_REFERENCE_DATA_MODAL,
      visible: show,
    });
  };
}

export function showReferenceDataPreviewModal(show = true) {
  return {
    type: ActionTypes.SHOW_REFERENCE_DATA_PREVIEW_MODAL,
    visible: show,
  };
}

export function showReferenceDataDeleteModal(show = true) {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.SHOW_REFERENCE_DATA_DELETE_MODAL,
      visible: show,
    });
  };
}

export function showReferenceDataUpdateModal(show = true) {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.SHOW_REFERENCE_DATA_UPDATE_MODAL,
      visible: show,
    });
  };
}

export function referenceDataProcessingError(err: any, whileCreating = true) {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: whileCreating ? ActionTypes.CREATE_REFERENCE_DATA_FAILED : ActionTypes.UPDATE_REFERENCE_DATA_FAILED,
      errorMessage: err.errorMessage,
    });
  };
}

export function hideReferenceDataError() {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.REMOVE_REFERENCE_DATA_ERROR,
    });
  };
}

/**
 * Action for creating a ref data metadata
 * @param {Object} params - metadata to create a ref data
 */
export function createReferenceDataMeta(params: any) {
  const { name, type, secretKey, accessKey, fileName, csvFile } = params;
  const refDataMetaConst = AppConstants.REFDATAMETA_CONST;

  const payload = new FormData();
  payload.append(refDataMetaConst.NAME, name);
  payload.append(refDataMetaConst.TYPE, type);
  payload.append(refDataMetaConst.SECRETKEY, secretKey);
  payload.append(refDataMetaConst.ACCESSKEY, accessKey);
  payload.append(refDataMetaConst.FILENAME, fileName);
  payload.append(refDataMetaConst.FILE, csvFile);

  return (dispatch: SyncariThunkDispatch) => {
    return post(DataUrlConstants.REFERENCE_DATA, payload)
      .then((resp) => {
        dispatch({
          type: ActionTypes.CREATE_REFERENCE_DATA,
        });
        getReferenceDataMetas()(dispatch);
        showReferenceDataModal(false)(dispatch);
      })
      .catch((err) => {
        const parsedErrorObj = getErrorMessage(err);
        referenceDataProcessingError(parsedErrorObj)(dispatch);
      });
  };
}

/**
 * Action for updating a ref data metadata
 * @param {Object} params - metadata to update a ref data
 */
export function updateReferenceDataMeta(params: any) {
  const { metaId, name, type, secretKey, accessKey, fileName, csvFile } = params;
  const refDataMetaConst = AppConstants.REFDATAMETA_CONST;

  const payload = new FormData();
  payload.append(refDataMetaConst.META_ID, metaId);
  payload.append(refDataMetaConst.NAME, name);
  payload.append(refDataMetaConst.TYPE, type);
  payload.append(refDataMetaConst.SECRETKEY, secretKey);
  payload.append(refDataMetaConst.ACCESSKEY, accessKey);
  payload.append(refDataMetaConst.FILENAME, fileName);
  payload.append(refDataMetaConst.FILE, csvFile);

  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.UPDATE_REFERENCE_DATA,
    });

    return put(DataUrlConstants.REFERENCE_DATA, payload)
      .then((resp) => {
        getReferenceDataMetas()(dispatch);
        showReferenceDataUpdateModal(false)(dispatch);
      })
      .catch((err) => {
        const parsedErrorObj = getErrorMessage(err);
        referenceDataProcessingError(parsedErrorObj, false)(dispatch);
      });
  };
}

export function activateReferenceDataMeta(referenceDataId: string) {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.ACTIVATE_REFERENCE_DATA,
    });
    const url = replaceToken(DataUrlConstants.ACTIVATE_REF_DATA, {
      refMetaId: referenceDataId,
    });

    return post(url, new FormData()).then((resp) => {
      getReferenceDataMetas()(dispatch);
    });
  };
}

export function deactivateReferenceDataMeta(referenceDataId: string) {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.DEACTIVATE_REFERENCE_DATA,
    });
    const url = replaceToken(DataUrlConstants.DEACTIVATE_REF_DATA, {
      refMetaId: referenceDataId,
    });

    return post(url, new FormData()).then((resp) => {
      getReferenceDataMetas()(dispatch);
    });
  };
}

export function deleteReferenceDataMeta(referenceDataList: any[]) {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.DELETE_REFERENCE_DATA,
    });
    const url = replaceToken(DataUrlConstants.DELETE_REF_DATA, {
      refMetaId: first(referenceDataList).id,
    });

    return deleteRequest(url).then((resp) => {
      getReferenceDataMetas()(dispatch);
    });
  };
}

export function setSelectedReferenceDataRows(selectedRows: any[]) {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.GET_SELECTED_REFERENCE_DATA,
      selectedRows,
    });
  };
}
