import { Button, message, Modal } from 'antd';

import {
  useDeleteErrorNotificationsConfigMutation,
  useUpdateErrorNotificationsConfigMutation,
} from 'store/error-notifications-v2/api';
import { ErrorNotificationConfig } from 'store/error-notifications-v2/types';
import { getRtkQueryErrorMessage } from 'utils/getRtkQueryErrorMessage';
import { tc, tNamespaced } from 'utils/i18nUtil';

interface DeleteEmailsModalProps {
  isModalOpen: boolean;
  setIsModalOpen: (isModalOpen: boolean) => void;
  emailsToDelete: string[];
  closeDrawerPanel: () => void;
  currentNotificationConfig: ErrorNotificationConfig | undefined;
}

const tn = tNamespaced('Settings.ErrorNotifications');

export function DeleteEmailsModal({
  isModalOpen,
  setIsModalOpen,
  emailsToDelete,
  closeDrawerPanel,
  currentNotificationConfig,
}: DeleteEmailsModalProps) {
  const [updateErrorNotificationsConfig, { isLoading: isUpdating }] = useUpdateErrorNotificationsConfigMutation();

  const isDeletingAllEmails = emailsToDelete.length === currentNotificationConfig?.configuration?.emails?.length;

  const [deleteErrorNotificationConfig, { isLoading: isDeleting }] = useDeleteErrorNotificationsConfigMutation();

  const handleRemoveEmails = () => {
    if (isDeletingAllEmails) {
      deleteErrorNotificationConfig(currentNotificationConfig?.id)
        .unwrap()
        .then(() => {
          message.success(tn('all_subscribers_delete_success'));
          setIsModalOpen(false);
          closeDrawerPanel();
        })
        .catch((error) => message.error(getRtkQueryErrorMessage(error, tn('subscribers_delete_error'))));
    } else {
      updateErrorNotificationsConfig({
        ...currentNotificationConfig,
        configuration: {
          ...(currentNotificationConfig?.configuration || {}),
          emails: currentNotificationConfig?.configuration?.emails?.filter(
            (element) => !emailsToDelete.includes(element.email || '')
          ),
        },
      })
        .unwrap()
        .then(() => {
          message.success(tn('subscribers_delete_success', { count: emailsToDelete.length }));
          setIsModalOpen(false);
        })
        .catch((error) => message.error(getRtkQueryErrorMessage(error, tn('subscribers_delete_error'))));
    }
  };

  return (
    <Modal
      title={tn('delete_modal_title')}
      visible={isModalOpen}
      footer={
        <>
          <Button key="cancel" onClick={() => setIsModalOpen(false)}>
            {tc('cancel')}
          </Button>
          <Button key="ok" type="primary" onClick={handleRemoveEmails} loading={isUpdating || isDeleting}>
            {tc('delete')}
          </Button>
        </>
      }
      onCancel={() => setIsModalOpen(false)}>
      <p>
        {isDeletingAllEmails
          ? tn('delete_all_subscribers_text')
          : tn('delete_some_subscribers_text', { count: emailsToDelete.length })}
      </p>
    </Modal>
  );
}
