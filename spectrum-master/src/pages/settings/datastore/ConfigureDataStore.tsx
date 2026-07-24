import { RouteComponentProps } from '@reach/router';
import { Alert } from 'antd';
import cx from 'classnames';
import { isEmpty } from 'lodash';
import { useEffect, useMemo, useState } from 'react';

import Button from 'components/Button';
import Fieldset from 'components/Fieldset';
import { getIconFromPath } from 'components/icons/Icons';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { Option } from 'components/inputs/Select';
import { HStack, Spacer, Stack } from 'components/layout';
import Spinner from 'components/Spinner';
import { TextTag } from 'components/text-tag';
import { TextTagColorOptions } from 'components/text-tag/TextTag';
import { Text, TranslatedText } from 'components/typography';
import usePreviousValue from 'hooks/usePreviousValue';
import { useGetDataStoreDescribeQuery, useGetDataStoresListQuery } from 'store/datastore/api';
import AppConstants from 'utils/AppConstants';
import { tNamespaced } from 'utils/i18nUtil';

import CreateNewDSConnectionModal from './CreateNewDSConnectionModal';
import ProvisionSyncariDataStoreModal from './ProvisionSyncariDataStoreModal';
import SyncariDataStoreConfig from './SyncariDataStoreConfig';
import UpdateCustomDataStore from './UpdateCustomDataStore';

import './ConfigureDataStore.scss';

const tn = tNamespaced('Settings.DataStore');

const SYNCARI_DATA_STORE_NAME = 'Syncari Datastore';

const STATUS_TEXT_COLOR: Record<string, TextTagColorOptions> = {
  [AppConstants.CONNECTOR_STATUS.ACTIVE]: 'green',
  [AppConstants.CONNECTOR_STATUS.ERROR]: 'red',
};

export interface ConfigureDataStoreProps extends RouteComponentProps {}

const ConfigureDataStore = ({ path }: ConfigureDataStoreProps) => {
  const [provisionSyncariModalOpen, setProvisionSyncariModalOpen] = useState(false);
  const [createNewDSConnectionModalOpen, setCreateNewDSConnectionModalOpen] = useState(false);

  const { data: dsList } = useGetDataStoreDescribeQuery();

  const { data: list, isLoading } = useGetDataStoresListQuery();

  const previousIds = usePreviousValue(list?.map((item) => item.id));
  const newListItems = list?.filter((item) => !previousIds?.includes(item.id));

  // When a new item is found in the list, change the selected item to select it
  useEffect(() => {
    if (newListItems?.length === 1) {
      setSelectedDataStoreConfigId(newListItems[0]?.id);
    }
  }, [newListItems]);

  const activeDataStore = useMemo(() => {
    return list?.find((dStore) => dStore.status === 'ACTIVE');
  }, [list]);

  const [selectedDataStoreConfigId, setSelectedDataStoreConfigId] = useState(activeDataStore?.id);

  const selectedDataStoreConfig = useMemo(() => {
    return list?.find((dStore) => dStore.id === selectedDataStoreConfigId);
  }, [list, selectedDataStoreConfigId]);

  const selectedDataStore = useMemo(() => {
    return dsList?.find((dStore) => dStore.id === selectedDataStoreConfig?.metadataId);
  }, [dsList, selectedDataStoreConfig?.metadataId]);

  // Automatically select a data store on load or after delete
  useEffect(() => {
    if (!selectedDataStoreConfig) {
      if (activeDataStore?.id) {
        // Select the active data store on mount and if it changes
        setSelectedDataStoreConfigId(activeDataStore.id);
      } else if (list?.[0]?.id) {
        // Select the first data store in the list if there is no active data store
        setSelectedDataStoreConfigId(list?.[0]?.id);
      }
    }
  }, [activeDataStore?.id, list, selectedDataStoreConfig]);

  if (isLoading) {
    return <Spinner />;
  }

  const modals = (
    <>
      <CreateNewDSConnectionModal
        open={createNewDSConnectionModalOpen}
        onClose={() => setCreateNewDSConnectionModalOpen(false)}
      />
      <ProvisionSyncariDataStoreModal
        open={provisionSyncariModalOpen}
        onClose={() => setProvisionSyncariModalOpen(false)}
      />
    </>
  );

  if (isEmpty(list)) {
    return (
      <div>
        <Fieldset title={tn('data_store_configuration')}>
          <HStack>
            <Button
              type="primary"
              onClick={() => {
                setProvisionSyncariModalOpen(true);
              }}>
              {tn('provision_text')}
            </Button>
            <Button
              type="default"
              onClick={() => {
                setCreateNewDSConnectionModalOpen(true);
              }}>
              {tn('create_new_connection')}
            </Button>
          </HStack>
        </Fieldset>
        {modals}
      </div>
    );
  }

  if (selectedDataStoreConfig?.authConfig) {
    const connectorOptions = list?.map((item) => {
      return (
        <Option key={item.id} value={item.id}>
          <span className="synri-config-data-store-container__connection-item-span">{item.name}</span>
          {item.status === AppConstants.CONNECTOR_STATUS.ACTIVE && <TextTag text={item.status} color="green" />}
        </Option>
      );
    });

    connectorOptions?.unshift(
      <Option key="new_connection" value="new_connection">
        <TranslatedText namespace="Settings.DataStore" text="new_connection" color="syncari-blue" />
      </Option>
    );

    const syncariIsProvisioned = list?.some((config) => config?.name === SYNCARI_DATA_STORE_NAME);

    if (!syncariIsProvisioned) {
      connectorOptions?.unshift(
        <Option key="provision_syncari" value="provision_syncari">
          <TranslatedText namespace="Settings.DataStore" text="provision_syncari" color="syncari-blue" />
        </Option>
      );
    }

    return (
      <Stack className="synri-config-data-store-container">
        <div className="synri-config-data-store-container__active-summary">
          <div>
            <TranslatedText namespace="Settings.DataStore" text="using" color="gray-900" size="lg" weight="bold" />
            <div className="synri-config-data-store-container__active-connector-name">
              <div
                className={cx(
                  'synri-config-data-store-container__active-connector-name--svg-container',
                  selectedDataStoreConfig.iconUri?.includes('syncari') && 'scale-large'
                )}>
                {getIconFromPath(selectedDataStoreConfig.iconUri)}
              </div>
              <Text color="gray-900" size="lg">
                {selectedDataStoreConfig.name}
              </Text>
            </div>
          </div>
          <div>
            <TranslatedText
              namespace="Settings.DataStore"
              text="status_colon"
              color="gray-900"
              size="md"
              weight="bold"
              className="synri-config-data-store-container__active-summary--label-left"
            />
            <TextTag
              text={selectedDataStoreConfig.status}
              color={
                STATUS_TEXT_COLOR[selectedDataStoreConfig.status]
                  ? STATUS_TEXT_COLOR[selectedDataStoreConfig.status]
                  : 'gray'
              }
            />
          </div>
        </div>
        <Fieldset title={tn('data_store_configuration')}>
          {selectedDataStoreConfig?.errorMessage && (
            <>
              <Alert type="error" message={selectedDataStoreConfig?.errorMessage} showIcon />
              <Spacer y="sm" />
            </>
          )}
          <InputWithLabel
            label={tn('connection')}
            onChange={(value: string) => {
              if (value === 'new_connection') {
                setCreateNewDSConnectionModalOpen(true);
              } else if (value === 'provision_syncari') {
                setProvisionSyncariModalOpen(true);
              } else {
                setSelectedDataStoreConfigId(value);
              }
            }}
            value={selectedDataStoreConfig?.id}
            datatype={AppConstants.INPUT_TYPE.PICKLIST}
            options={connectorOptions}
          />

          {selectedDataStoreConfig?.name === SYNCARI_DATA_STORE_NAME ? (
            <SyncariDataStoreConfig syncariDataStore={selectedDataStoreConfig} activeDataStore={activeDataStore} />
          ) : (
            <UpdateCustomDataStore
              dataStore={selectedDataStore}
              dataStoreConfig={selectedDataStoreConfig}
              activeDataStore={activeDataStore}
            />
          )}
        </Fieldset>
        {modals}
      </Stack>
    );
  }

  return null;
};

export default ConfigureDataStore;
