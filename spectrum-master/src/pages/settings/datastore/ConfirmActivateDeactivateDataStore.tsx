import { message } from 'antd';

import Button from 'components/Button';
import Modal from 'components/Modal';
import { TranslatedText } from 'components/typography';
import { useActivateConnectionMutation, useDeactivateConnectionMutation } from 'store/datastore/api';
import { getRtkQueryErrorMessage } from 'utils/getRtkQueryErrorMessage';
import { tCommon, tNamespaced } from 'utils/i18nUtil';

const tn = tNamespaced('Settings.DataStore');

export interface ConfirmActivateDeactivateDataStoreProps {
  activateOrDeactivate: null | 'activate' | 'deactivate';
  dataStoreConfigId: string;
  connectionName: string;
  onClose: () => void;
  activeConnectionName?: string;
  activeStoreName?: string;
  newStoreName?: string;
}

const ConfirmActivateDeactivateDataStore = ({
  onClose,
  activateOrDeactivate,
  dataStoreConfigId,
  connectionName,
  activeConnectionName,
  activeStoreName,
  newStoreName,
}: ConfirmActivateDeactivateDataStoreProps) => {
  const activate = activateOrDeactivate === 'activate';

  const [activateConnection, { isLoading: isActivating }] = useActivateConnectionMutation();
  const [deactivateConnection, { isLoading: isDeactivating }] = useDeactivateConnectionMutation();

  const loading = isActivating || isDeactivating;

  return (
    <Modal
      title={tn(activate ? 'activate_connection_check' : 'deactivate_connection_check')}
      visible={Boolean(activateOrDeactivate)}
      onCancel={onClose}
      destroyOnClose
      footer={
        <>
          {!loading && (
            <Button key="cancel" onClick={() => onClose()}>
              {tCommon('cancel')}
            </Button>
          )}
          <Button
            key="ok"
            type="primary"
            loading={loading}
            onClick={() => {
              const method = activate ? activateConnection : deactivateConnection;
              method(dataStoreConfigId).then((res) => {
                if ('error' in res) {
                  message.error(getRtkQueryErrorMessage(res.error));
                } else {
                  onClose();
                  message.success(
                    tn(activate ? 'connection_activated_successfully' : 'connection_deactivated_successfully')
                  );
                }
              });
            }}>
            {tn(activate ? 'activate' : 'deactivate')}
          </Button>
        </>
      }>
      <TranslatedText
        namespace="Settings.DataStore"
        text={
          activate
            ? activeConnectionName
              ? 'activate_confirm_description'
              : 'activate_confirm_description_no_other_active'
            : 'deactivate_confirm_description'
        }
        beDangerous
        args={{
          activeConnectionName,
          connectionName,
          activeStoreName,
          newStoreName,
        }}
      />
    </Modal>
  );
};

export default ConfirmActivateDeactivateDataStore;
