import { Button, message, Modal } from 'antd';

import { useDeleteErrorNotificationsConfigMutation } from 'store/error-notifications-v2/api';
import { tc, tNamespaced } from 'utils/i18nUtil';

import { useErrorNotificationContext } from './context/ErrorNotificationFormContext';

interface DeleteConfirmationModalProps {
  isModalOpen: boolean;
  setIsModalOpen: (isModalOpen: boolean) => void;
}

const tn = tNamespaced('Settings.ErrorNotifications');

export function DeleteConfirmationModal({ isModalOpen, setIsModalOpen }: DeleteConfirmationModalProps) {
  const [deleteErrorNotificationConfig] = useDeleteErrorNotificationsConfigMutation();
  const { currentNotificationConfig } = useErrorNotificationContext();

  function handleDelete() {
    deleteErrorNotificationConfig(currentNotificationConfig?.id)
      .unwrap()
      .then(() => {
        message.success(tn('crud_success', { type: currentNotificationConfig?.type, operation: 'deleted' }));
        setIsModalOpen(false);
      })
      .catch(() => message.error(tn('crud_error', { type: currentNotificationConfig?.type, operation: 'deleting' })));
  }
  return (
    <Modal
      title={`${currentNotificationConfig?.name}`}
      visible={isModalOpen}
      footer={
        <>
          <Button key="cancel" onClick={() => setIsModalOpen(false)}>
            {tc('cancel')}
          </Button>
          <Button key="ok" type="primary" onClick={handleDelete}>
            {tc('yes')}
          </Button>
        </>
      }
      onCancel={() => setIsModalOpen(false)}>
      <p>{tn('delete_confirmation', { type: currentNotificationConfig?.type })}</p>
    </Modal>
  );
}
