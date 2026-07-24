import { Button, Modal } from 'antd';
import { find } from 'lodash';
import { useCallback, useState } from 'react';

import { useI18nContext, withI18n } from 'components/I18nProvider';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import useToastForFetchStatusChange from 'hooks/useToastForFetchStatusChange';
import { Instance } from 'store/instances/slice';
import { FetchStatus } from 'store/types';
import { useRevokeGhostAccessMutation } from 'store/user/api';
import { getProfile, getUserInstances } from 'store/user/thunks';
import AppConstants from 'utils/AppConstants';

export interface RevokeGhostAccessModalProps {
  visible: boolean;
  setVisible: (visible: boolean) => void;
  instance: Instance | null;
}

export const RevokeGhostAccessModal = withI18n(({ visible, setVisible, instance }: RevokeGhostAccessModalProps) => {
  const { tn, tc } = useI18nContext();
  const dispatch = useEnhancedDispatch();

  const [revokeGhostAccess] = useRevokeGhostAccessMutation();
  const [revokeGhostAccessStatus, setRevokeGhostAccessStatus] = useState<FetchStatus>(AppConstants.FETCH_STATUS.IDLE);
  const [errorMessage, setErrorMessage] = useState('');
  const userRoles = useEnhancedSelector((state) => state.user.userRoles);
  const allRoles = useEnhancedSelector((state) => state.user.allRoles);

  useToastForFetchStatusChange(revokeGhostAccessStatus, {
    success: tn('success', { syncariId: instance?.syncariId }),
    error: tn('error', { error: errorMessage }),
  });

  const handleClose = useCallback(() => {
    setVisible(false);
  }, [setVisible]);

  const handleRevokeAccess = useCallback(() => {
    if (instance) {
      const roleName = userRoles[instance.syncariId]?.[0];
      const roleId = find(allRoles, { name: roleName })?.id;

      setRevokeGhostAccessStatus(AppConstants.FETCH_STATUS.LOADING);
      revokeGhostAccess({ syncariId: instance.syncariId, roleId })
        .unwrap()
        .then(() => {
          setRevokeGhostAccessStatus(AppConstants.FETCH_STATUS.SUCCESS);
          dispatch(getUserInstances());
          dispatch(getProfile());
        })
        .catch((resp) => {
          setErrorMessage(resp.data.message || resp.data.error);
          setRevokeGhostAccessStatus(AppConstants.FETCH_STATUS.ERROR);
        });
    }
    handleClose();
  }, [allRoles, dispatch, handleClose, instance, revokeGhostAccess, userRoles]);

  const footer = (
    <>
      <Button type="default" onClick={handleClose}>
        {tc('cancel')}
      </Button>
      <Button type="danger" onClick={handleRevokeAccess}>
        {tn('revoke_access')}
      </Button>
    </>
  );

  return (
    <Modal visible={visible} onCancel={handleClose} footer={footer} title={tn('title')}>
      {tn('description', { instanceName: instance?.displayName, syncariId: instance?.syncariId })}
    </Modal>
  );
}, 'RevokeGhostAccessModal');
