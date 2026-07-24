import { Button, message, Modal } from 'antd';

import { useResendInviteMutation } from 'store/error-notifications-v2/api';
import { tc, tNamespaced } from 'utils/i18nUtil';

import { useErrorNotificationContext } from '../context/ErrorNotificationFormContext';

interface ResendInviteModalProps {
  isModalOpen: boolean;
  setIsModalOpen: (isModalOpen: boolean) => void;
  email: string | undefined;
}

const tn = tNamespaced('Settings.ErrorNotifications');

export function ResendInviteModal({ isModalOpen, setIsModalOpen, email }: ResendInviteModalProps) {
  const { currentNotificationConfig } = useErrorNotificationContext();
  const [resendInvite, { isLoading: isResending }] = useResendInviteMutation();

  function handleResendInvite() {
    if (!currentNotificationConfig?.id) {
      return;
    }
    resendInvite({ email: email || '', id: currentNotificationConfig.id })
      .unwrap()
      .then(() => {
        message.success(tn('invite_sent_success'));
        setIsModalOpen(false);
      })
      .catch(() => message.error(tn('invite_sent_error')));
  }
  return (
    <Modal
      title={tn('resend_invite_modal_title')}
      visible={isModalOpen}
      footer={
        <>
          <Button key="cancel" onClick={() => setIsModalOpen(false)}>
            {tc('cancel')}
          </Button>
          <Button key="ok" type="primary" onClick={handleResendInvite} loading={isResending}>
            {tc('resend')}
          </Button>
        </>
      }
      onCancel={() => setIsModalOpen(false)}>
      <p>{tn('resend_invite_text', { email })}</p>
    </Modal>
  );
}
