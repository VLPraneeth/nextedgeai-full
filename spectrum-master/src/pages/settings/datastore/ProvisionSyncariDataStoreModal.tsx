import { message } from 'antd';

import Button from 'components/Button';
import Modal from 'components/Modal';
import { useProvisionSyncariDataStoreMutation } from 'store/datastore/api';
import { getRtkQueryErrorMessage } from 'utils/getRtkQueryErrorMessage';
import { tCommon, tNamespaced } from 'utils/i18nUtil';

const tn = tNamespaced('Settings.DataStore');

export interface ProvisionSyncariDataStoreModalProps {
  open: boolean;
  onClose: () => void;
}

const ProvisionSyncariDataStoreModal = ({ open, onClose }: ProvisionSyncariDataStoreModalProps) => {
  const [provisionDataStore, { isLoading }] = useProvisionSyncariDataStoreMutation();

  return (
    <Modal
      title={tn('provision_modal_title')}
      visible={open}
      onCancel={onClose}
      destroyOnClose
      footer={
        <>
          {!isLoading && (
            <Button key="cancel" onClick={() => onClose()}>
              {tCommon('cancel')}
            </Button>
          )}
          <Button
            key="ok"
            type="primary"
            loading={isLoading}
            onClick={() =>
              provisionDataStore().then((res: any) => {
                if ('error' in res) {
                  message.error(tn('provision_failed', { errorMessage: getRtkQueryErrorMessage(res.error) }));
                } else {
                  onClose();
                  message.success(tn('provision_success'));
                }
              })
            }>
            {tn('provision')}
          </Button>
        </>
      }>
      <p>{tn('provision_modal_description')}</p>
    </Modal>
  );
};

export default ProvisionSyncariDataStoreModal;
