import { delay } from 'lodash';
import { useCallback, useEffect, useMemo, useState } from 'react';

import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { FetchStatus } from 'store/types';
import { post } from 'utils/AjaxUtil';
import AppConstants from 'utils/AppConstants';
import DataUrlConstants from 'utils/DataUrlConstants';

import { selectCredential, useSelectCredential } from './selectors';
import { deleteCredential, getCredentials, showCredentialModal } from './slice';
import { ServiceCredential } from './types';

declare global {
  interface Window {
    oAuth: {
      oAuthWindow: any;
      oAuthClose: any;
      oAuthDispatch?: any;
    };
  }
}

const useHandleOAuth = () => {
  const dispatch = useEnhancedDispatch();

  const oAuthClose = useCallback(
    (closeParams: { success?: boolean }) => {
      const { oAuthWindow } = window.oAuth;
      delay(() => {
        dispatch(getCredentials());
        dispatch(showCredentialModal({ visible: false }));
      }, 0);

      if (closeParams.success) {
        oAuthWindow.close();
      }
    },
    [dispatch]
  );

  return useCallback(
    (authUrl: string) => {
      const oAuthWindow = window.open(
        authUrl,
        '_target',
        'toolbar=yes,scrollbars=yes,resizable=yes,top=150,left=500,width=650,height=750'
      );

      window.oAuth = { oAuthWindow, oAuthClose };
    },
    [oAuthClose]
  );
};

export const useCredentials = () => {
  const dispatch = useEnhancedDispatch();
  const credential = useSelectCredential();

  useEffect(() => {
    dispatch(getCredentials());
  }, [dispatch]);

  return {
    credentials: credential.credentials,
    loading: credential.fetchingCredentials === AppConstants.FETCH_STATUS.LOADING,
    status: credential.fetchingCredentials,
    error: credential.fetchingCredentialsError,
  };
};

export function useUpsertCredential() {
  const dispatch = useEnhancedDispatch();
  const handleOAuth = useHandleOAuth();

  const [updateStatus, setUpdateStatus] = useState<{
    status: FetchStatus;
    error: string | null;
  }>({ status: AppConstants.FETCH_STATUS.IDLE, error: null });

  const upsertCredential = useCallback(
    (params: ServiceCredential) => {
      setUpdateStatus({ status: AppConstants.FETCH_STATUS.LOADING, error: null });

      return post(DataUrlConstants.SERVICE_CREDENTIAL, params)
        .then((resp) => {
          let authUrl = resp.data;
          if (authUrl) {
            handleOAuth(authUrl);
          }

          setUpdateStatus({ status: AppConstants.FETCH_STATUS.SUCCESS, error: null });

          dispatch(getCredentials());
        })
        .catch((error) => {
          setUpdateStatus({ status: AppConstants.FETCH_STATUS.ERROR, error: error?.response?.data?.message });

          // We need to refetch credentials because it's possible to have a
          // successful save but still get an error response. i.e. when Zoominfo creds
          // don't create a successful connection.
          dispatch(getCredentials());
        });
    },
    [dispatch, handleOAuth]
  );

  return useMemo(
    () => ({
      upsertCredential,
      status: updateStatus.status,
      error: updateStatus.error,
      loading: updateStatus.status === AppConstants.FETCH_STATUS.LOADING,
    }),
    [upsertCredential, updateStatus]
  );
}

export const useDeleteCredential = (credentialId: string) => {
  const dispatch = useEnhancedDispatch();
  const { deleteCredentialStatusById, deleteCredentialErrorById } = useEnhancedSelector(selectCredential);
  const status = deleteCredentialStatusById[credentialId];

  return {
    deleteCredential: () => dispatch(deleteCredential({ credentialId })),
    status,
    error: deleteCredentialErrorById[credentialId],
    loading: status === AppConstants.FETCH_STATUS.LOADING,
  };
};
