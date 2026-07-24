import { Button, Modal, Tooltip, message } from 'antd';
import { omit } from 'lodash';
import { useState } from 'react';

import { HStack } from 'components/layout';
import { TranslatedText } from 'components/typography';
import { useUserInputConfirmationModal } from 'hooks/modal';
import { useDeleteConnectionMutation, useUpdateConnectionMutation } from 'store/datastore/api';
import { DataStoreConfig } from 'store/datastore/types';
import AppConstants from 'utils/AppConstants';
import { getRtkQueryErrorMessage } from 'utils/getRtkQueryErrorMessage';
import { tCommon, tNamespaced } from 'utils/i18nUtil';

import ConfirmActivateDeactivateDataStore from './ConfirmActivateDeactivateDataStore';

const tn = tNamespaced('Settings.DataStore');

export interface DataStoreActionsProps {
  dataStoreConfig: DataStoreConfig;
  activeDataStore?: DataStoreConfig;
  isSyncariDataStore?: boolean;
}

const DataStoreActions = ({ dataStoreConfig, activeDataStore, isSyncariDataStore }: DataStoreActionsProps) => {
  const showDeleteModal = useUserInputConfirmationModal();

  const [activateOrDeactivate, setActivateOrDeactivate] = useState<null | 'activate' | 'deactivate'>(null);
  const [showUpdateConnection, setShowUpdateConnection] = useState(false);

  const [updateDataStore, { isLoading: isUpdating }] = useUpdateConnectionMutation();
  const [deleteDataStore] = useDeleteConnectionMutation();

  const selectedConfigIsActive = dataStoreConfig.status === AppConstants.CONNECTOR_STATUS.ACTIVE;

  return (
    <>
      <HStack>
        {!isSyncariDataStore && (
          <Tooltip title={selectedConfigIsActive ? tn('cant_delete_active_connection') : ''}>
            <Button
              disabled={selectedConfigIsActive}
              onClick={() =>
                showDeleteModal({
                  title: tn('delete_connection_check'),
                  content: (
                    <TranslatedText
                      namespace="Settings.DataStore"
                      text={'delete_confirm_description'}
                      beDangerous
                      args={{ connectionName: dataStoreConfig.name }}
                    />
                  ),
                  onOk: () => {
                    deleteDataStore(dataStoreConfig.id).then((res) => {
                      message.success(`${dataStoreConfig.name} successfully deleted.`);
                    });
                  },
                })
              }>
              {tCommon('delete')}
            </Button>
          </Tooltip>
        )}
        {!isSyncariDataStore && (
          <Button
            onClick={() => {
              setShowUpdateConnection(true);
            }}>
            {tCommon('update')}
          </Button>
        )}
        {selectedConfigIsActive ? (
          <Button
            type="primary"
            onClick={() => {
              setActivateOrDeactivate('deactivate');
            }}>
            {tn('deactivate_connection')}
          </Button>
        ) : (
          <Button
            type="primary"
            onClick={() => {
              setActivateOrDeactivate('activate');
            }}>
            {tn('activate_connection')}
          </Button>
        )}
      </HStack>
      <Modal
        visible={showUpdateConnection}
        onCancel={() => !isUpdating && setShowUpdateConnection(false)}
        title={tn('update_connection_check')}
        destroyOnClose
        footer={
          <>
            {!isUpdating && (
              <Button key="cancel" onClick={() => setShowUpdateConnection(false)}>
                {tCommon('cancel')}
              </Button>
            )}
            <Button
              key="ok"
              type="primary"
              loading={isUpdating}
              onClick={() => {
                updateDataStore(omit(dataStoreConfig, ['authenticationConfig']) as DataStoreConfig).then((res) => {
                  setShowUpdateConnection(false);
                  if ('data' in res) {
                    message.success(
                      tn(
                        res.data?.status === AppConstants.CONNECTOR_STATUS.ACTIVE
                          ? 'update_and_test_success'
                          : 'update_success'
                      )
                    );
                  } else {
                    message.error(getRtkQueryErrorMessage(res.error));
                  }
                });
              }}>
              {tCommon('update')}
            </Button>
          </>
        }>
        <TranslatedText
          namespace="Settings.DataStore"
          text="update_confirm_description"
          beDangerous
          args={{ connectionName: dataStoreConfig.name }}
        />
      </Modal>

      <ConfirmActivateDeactivateDataStore
        onClose={() => setActivateOrDeactivate(null)}
        activateOrDeactivate={activateOrDeactivate}
        dataStoreConfigId={dataStoreConfig.id}
        activeConnectionName={activeDataStore?.name}
        connectionName={dataStoreConfig.name}
        activeStoreName={activeDataStore?.displayName}
        newStoreName={dataStoreConfig?.displayName}
      />
    </>
  );
};

export default DataStoreActions;
