//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { useCallback, useEffect, useMemo, useState } from 'react';

import Button from 'components/Button';
import { useI18nContext, withI18n } from 'components/I18nProvider';
import InlineMessage, { Types as InlineMessageTypes } from 'components/InlineMessage';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { Stack } from 'components/layout';
import Modal from 'components/Modal';
import { useGetCredentialMetadataListQuery, useSaveCredentialMutation } from 'store/credential/api';
import { CredentialRequest } from 'store/credential/types';

import './AuthModal.less';

export interface AuthModalProps {
  visible: boolean;
  onClose?: () => void;
  credentialType?: string;
}

export const AuthModal = ({ visible = false, onClose, credentialType }: AuthModalProps) => {
  const { data: credentialMetadatum } = useGetCredentialMetadataListQuery();
  const [saveCredential] = useSaveCredentialMutation();
  const [title, setTitle] = useState('');
  const [formValue, setFormValue] = useState<Record<string, string>>({});
  const { tc, tn } = useI18nContext();
  const [saveCredentialError, setSaveCredentialError] = useState('');

  useEffect(() => {
    if (!visible) {
      setTitle('');
      setSaveCredentialError('');
      setFormValue({});
    }
  }, [visible]);

  const inputs = useMemo(() => {
    const metadata = credentialMetadatum?.find((credMeta) => credMeta.id === credentialType);
    if (metadata) {
      setTitle(tn('title', { name: metadata.displayName }));
      return metadata.supportedAuthTypes?.[0]?.fields.map((field) => {
        const { dataType: datatype, ...input } = field;
        return {
          ...input,
          datatype,
        };
      });
    }
    return [];
  }, [credentialMetadatum, credentialType, tn]);

  const save = useCallback(() => {
    setSaveCredentialError('');
    const metadata = credentialMetadatum?.find((credMeta) => credMeta.id === credentialType);
    if (metadata?.supportedAuthTypes) {
      const { name, ...values } = formValue;
      const supportedAuth = metadata.supportedAuthTypes[0];
      saveCredential({
        name,
        metadataId: metadata.id,
        authType: supportedAuth.authType,
        authConfig: values,
      } as CredentialRequest)
        .unwrap()
        .then((resp) => onClose?.())
        .catch((resp) => setSaveCredentialError(resp.data.message || resp.data.error));
    }
  }, [credentialMetadatum, credentialType, formValue, onClose, saveCredential]);

  return (
    <Modal
      title={title}
      centered
      width={600}
      className="synri-auth-modal"
      visible={visible}
      footer={
        <>
          <Button key="cancel" onClick={onClose}>
            {tc('cancel')}
          </Button>
          <Button loading={false} key="ok" type="primary" onClick={save}>
            {false ? tc('update') : tc('create')}
          </Button>
        </>
      }
      onOk={() => {}}
      onCancel={onClose}>
      <Stack spacing="xxxs">
        <InlineMessage type={InlineMessageTypes.ERROR} title={saveCredentialError}>
          {saveCredentialError}
        </InlineMessage>
        <InputWithLabel
          datatype="string"
          name="name"
          label={tc('name')}
          value={formValue['name']}
          onChange={(evt: React.FormEvent<HTMLInputElement>) => {
            setSaveCredentialError('');
            setFormValue({
              ...formValue,
              name: evt.currentTarget.value,
            });
          }}
        />
        {inputs.map(({ name, datatype, label }: any) => {
          return (
            <InputWithLabel
              key={name}
              datatype={datatype}
              name={name}
              label={label}
              value={formValue[name]}
              onChange={(evt: React.FormEvent<HTMLInputElement>) => {
                setSaveCredentialError('');
                setFormValue({
                  ...formValue,
                  [name]: evt.currentTarget.value,
                });
              }}
            />
          );
        })}
      </Stack>
    </Modal>
  );
};

export default withI18n(AuthModal, 'ActionSetup');
