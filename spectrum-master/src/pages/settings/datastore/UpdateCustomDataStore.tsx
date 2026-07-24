import produce from 'immer';
import { get } from 'lodash';
import { ChangeEvent, useEffect, useState } from 'react';

import InputWithLabel from 'components/inputs/InputWithLabel';
import { Stack } from 'components/layout';
import { AuthConfig, AuthTypeConfigFields, ConfigureFields, ConnectorMetadata } from 'reducers/connectorReducer';
import { DataStoreConfig } from 'store/datastore/types';
import AppConstants from 'utils/AppConstants';
import { tCommon } from 'utils/i18nUtil';

import DataStoreActions from './DataStoreActions';

export interface UpdateCustomDataStoreProps {
  dataStoreConfig: DataStoreConfig;
  activeDataStore?: DataStoreConfig;
  dataStore?: ConnectorMetadata;
}

const UpdateCustomDataStore = ({ dataStoreConfig, dataStore, activeDataStore }: UpdateCustomDataStoreProps) => {
  const [formState, setFormState] = useState<DataStoreConfig>(dataStoreConfig);

  useEffect(() => {
    setFormState(dataStoreConfig);
  }, [dataStoreConfig]);

  const getInput = (field: ConfigureFields, namespace: string) => {
    const { name, label, dataType, helpSummary } = field;

    const value = get(formState, [namespace, name].filter(Boolean).join('.'));

    const isCheckbox = dataType === AppConstants.INPUT_TYPE.CHECKBOX;

    const optionData = [];
    const authtype = dataStore?.supportedAuthTypes?.[0];
    if (authtype) {
      optionData.push({ label: authtype.label, value: authtype.authType });
    }

    return (
      <InputWithLabel
        key={name}
        name={name}
        label={label}
        value={value}
        checked={isCheckbox ? value : undefined}
        // Users can change their credentials but no other configuration details.
        disabled={namespace !== 'authConfig'}
        onChange={(evt: ChangeEvent<HTMLInputElement>) => {
          let newValue: string | number | boolean = typeof evt !== 'object' ? evt : evt.target.value;

          if (isCheckbox) {
            newValue = evt.target.checked;
          }

          setFormState((currentState) =>
            produce(currentState, (draft: any) => {
              if (namespace) {
                if (draft[namespace]) {
                  draft[namespace][name] = newValue;
                } else {
                  draft[namespace] = { [name]: newValue };
                }
              } else {
                draft[name] = newValue;
              }
            })
          );
        }}
        required={field.required}
        tooltip={helpSummary || ''}
        datatype={dataType}
        optionData={optionData}
      />
    );
  };

  let authFields: AuthTypeConfigFields[] = [];
  const authType = dataStore?.supportedAuthTypes?.find(
    (authConfig) => authConfig.authType === formState.metaConfig?.authType
  );

  if (authType) {
    authFields = authType.fields;
  }

  if (!dataStore) {
    return null;
  }

  return (
    <Stack spacing="xxs">
      <InputWithLabel
        label={tCommon('name')}
        value={formState.name}
        placeholder="Connection name"
        datatype="string"
        onChange={(event: ChangeEvent<HTMLInputElement>) => {
          setFormState((currentState) =>
            produce(currentState, (draft) => {
              draft.name = event?.target.value;
            })
          );
        }}
      />

      {dataStore.configureFields.map((field) => getInput(field, 'metaConfig'))}

      {authFields?.map((field) => getInput(field, 'authConfig'))}

      <DataStoreActions dataStoreConfig={formState} activeDataStore={activeDataStore} />
    </Stack>
  );
};

export default UpdateCustomDataStore;
