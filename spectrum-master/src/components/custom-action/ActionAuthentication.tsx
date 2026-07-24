//
// Copyright (c) 2019-Present Syncari All rights reserved.
//

import cx from 'classnames';
import { find } from 'lodash';
import { useEffect, useMemo, useState } from 'react';

import Button from 'components/Button';
import { useI18nContext, withI18n } from 'components/I18nProvider';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { HStack, Stack } from 'components/layout';
import { useEnhancedSelector as useSelector } from 'hooks/redux';
import { Connector } from 'reducers/connectorReducer';
import { getDervConnectors } from 'selectors/connectorSelectors';
import { useGetCredentialListQuery, useGetCredentialMetadataListQuery } from 'store/credential/api';
import AppConstants from 'utils/AppConstants';

import AuthModal from './AuthModal';

import './ActionAuthentication.less';

const SYNAPSE_AUTH = 'SYNAPSE_AUTH';

export interface ActionAuthenticationValue {
  metadataId?: string;
  credentialId?: string;
}

export interface ActionAuthenticationProps {
  className?: string;
  onChange?: (value: ActionAuthenticationValue) => void;
  defaultValue?: ActionAuthenticationValue;
}

export const ActionAuthentication = ({ className, onChange, defaultValue }: ActionAuthenticationProps) => {
  const { data: credentials } = useGetCredentialListQuery();
  const { data: credentialMetadatum } = useGetCredentialMetadataListQuery();
  const [metadataId, setMetadataId] = useState(defaultValue?.metadataId || '');
  const [credentialId, setCredentialId] = useState(defaultValue?.credentialId || '');
  const [isSynapseAuth, setIsSynapseAuth] = useState(false);
  const connectors = useSelector(getDervConnectors);
  const { tn } = useI18nContext();
  const [authVisible, setAuthVisible] = useState(false);

  const authTypes = useMemo(() => {
    const credentialTypes = (credentialMetadatum || [])?.map((cred) => {
      return {
        value: cred.id,
        label: cred.displayName,
      };
    });
    credentialTypes.push({
      value: SYNAPSE_AUTH,
      label: tn('synapse_credentials'),
    });

    // Automatically select the first credetial metadata
    if (metadataId) {
      if (credentialTypes?.length > 1 && !find(credentialTypes, { value: metadataId })) {
        // Default to synapse after we get all the available credential types from server
        // and the credential type is not one of the known custom action credential type
        setIsSynapseAuth(true);
      }
    } else if (credentialMetadatum?.length) {
      // Pick the first item in the list
      setIsSynapseAuth(false);
      setMetadataId(credentialMetadatum[0].id);
    }

    return credentialTypes;
  }, [credentialMetadatum, metadataId, tn]);

  const auths = useMemo(() => {
    // Filter the list of credentials
    if (isSynapseAuth) {
      return connectors
        ?.filter((connector: Connector) => connector.status === AppConstants.CONNECTOR_STATUS.ACTIVE)
        .map((connector: Connector) => {
          return {
            value: connector.id,
            label: connector.name,
          };
        });
    } else {
      return credentials
        ?.filter((credential) => credential.metadataId === metadataId)
        .map((credential) => {
          return {
            value: credential.id,
            label: credential.name,
          };
        });
    }
  }, [connectors, credentials, metadataId, isSynapseAuth]);

  useEffect(() => {
    onChange?.({
      metadataId,
      credentialId,
    });
  }, [onChange, metadataId, credentialId]);

  return (
    <div className={cx('synri-action-auth', className)}>
      <HStack className="synri-action-auth-container" spacing="z">
        <Stack className="synri-action-auth-type">
          <InputWithLabel
            value={isSynapseAuth ? SYNAPSE_AUTH : metadataId}
            datatype={AppConstants.INPUT_TYPE.PICKLIST}
            label={tn('type')}
            optionData={authTypes}
            onChange={(value: string) => {
              setMetadataId(value);
              setCredentialId('');
              setIsSynapseAuth(value === SYNAPSE_AUTH ? true : false);
            }}
          />
        </Stack>
        <Stack className="synri-action-auth-value">
          <InputWithLabel
            value={credentialId}
            datatype={AppConstants.INPUT_TYPE.PICKLIST}
            label={tn('credential')}
            optionData={auths}
            onChange={(value: string) => {
              setCredentialId(value);
              if (isSynapseAuth) {
                setMetadataId(connectors.find((connector: Connector) => connector.id === value)?.metadataId || '');
              }
            }}
          />
          {!isSynapseAuth && (
            <Button type="primary" onClick={() => setAuthVisible(true)}>
              {tn('new_credential')}
            </Button>
          )}
        </Stack>
      </HStack>
      <AuthModal visible={authVisible} onClose={() => setAuthVisible(false)} credentialType={metadataId} />
    </div>
  );
};

export default withI18n(ActionAuthentication, 'ActionSetup');
