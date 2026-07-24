import { navigate } from '@reach/router';
import { Button, Modal } from 'antd';

import { format, SHORT_DATE_24_TIME_TZ_FORMAT } from 'utils/DateUtil';
import { tc, tNamespaced } from 'utils/i18nUtil';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

import { useErrorNotificationContext } from './context/ErrorNotificationFormContext';

interface DisabledStatusModalProps {
  isModalOpen: boolean;
  setIsModalOpen: (isModalOpen: boolean) => void;
}

const tn = tNamespaced('Settings.ErrorNotifications');

export function DisabledStatusModal({ isModalOpen, setIsModalOpen }: DisabledStatusModalProps) {
  const { currentNotificationConfig } = useErrorNotificationContext();

  const modalBody = {
    email: <p>{currentNotificationConfig?.statusMessage}</p>, //tn('email_disabled_status_help')
    webhook: <WebhookModalBody />,
  };

  if (currentNotificationConfig?.status !== 'Disabled') {
    return null;
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
          <Button
            key="ok"
            type="primary"
            onClick={() =>
              navigate(
                makeUrl(RouteConstants.SETTINGS_NOTIFICATIONS_TYPE_EDIT, {
                  id: currentNotificationConfig?.id,
                  type: currentNotificationConfig?.type,
                })
              )
            }>
            {tc('edit')}
          </Button>
        </>
      }
      onCancel={() => setIsModalOpen(false)}>
      {currentNotificationConfig?.type && modalBody[currentNotificationConfig.type]}
    </Modal>
  );
}

function WebhookModalBody() {
  const { currentNotificationConfig } = useErrorNotificationContext();
  return (
    <div>
      {currentNotificationConfig?.statusMessage && <p>{currentNotificationConfig.statusMessage}</p>}
      {currentNotificationConfig?.firstErrorOccured && (
        <p>
          {tn('error_happened')}: {format(currentNotificationConfig.firstErrorOccured, SHORT_DATE_24_TIME_TZ_FORMAT)}
        </p>
      )}
      {currentNotificationConfig?.lastErrorOccured && (
        <p>
          {tn('recent_timestamp')}: {format(currentNotificationConfig.lastErrorOccured, SHORT_DATE_24_TIME_TZ_FORMAT)}
        </p>
      )}
      {currentNotificationConfig?.retries && (
        <p>
          {tn('amount_of_retries')}: {currentNotificationConfig.retries}
        </p>
      )}
    </div>
  );
}
