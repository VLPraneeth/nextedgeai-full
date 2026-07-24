//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Alert, Button, Col, Input, Row, Select } from 'antd';
import { has, map } from 'lodash';
import { Fragment, useCallback, useEffect, useMemo } from 'react';

import { Stack } from 'components/layout';
import Modal from 'components/Modal';
import { useEnhancedDispatch } from 'hooks/redux';
import useToastForFetchStatusChange from 'hooks/useToastForFetchStatusChange';
import { useUpsertCredential } from 'store/credentials/hooks';
import { useCredentialModalData } from 'store/credentials/selectors';
import { showCredentialModal } from 'store/credentials/slice';
import {
  ClearBitServiceCredential,
  InsideViewCredential,
  OAuthCredential,
  ServiceCredential,
  ServiceCredentialTypeOptionsEnum,
  ZoomInfoServiceCredential,
} from 'store/credentials/types';
import { getCredentialType } from 'store/credentials/utils';
import AppConstants from 'utils/AppConstants';
import { tc, tNamespaced } from 'utils/i18nUtil';
import { UnreachableCaseError } from 'utils/TypeUtils';
import useSetState from 'utils/useSetState';

const tn = tNamespaced('Settings.ServiceCredentials');

const inputSpan = 18;
const fullWidthStyle = { width: '100%' };

const InputGroup = Input.Group;
const Option = Select.Option;

type CredentialModalState = Partial<ServiceCredential>;

const CredentialModal = () => {
  const dispatch = useEnhancedDispatch();
  const { upsertCredential, status: updateStatus, loading, error: updateError } = useUpsertCredential();
  const { credentialData } = useCredentialModalData();

  const serviceCredentialTypeOptionsMap: Record<ServiceCredentialTypeOptionsEnum, string> = useMemo(
    () => ({
      // SYN-4829: Salesintel should be the top due to partnership
      salesintel: tn(ServiceCredentialTypeOptionsEnum.SALESINTEL),
      aidentified: tn(ServiceCredentialTypeOptionsEnum.AIDENTIFIED),
      apexanalytix: tn(ServiceCredentialTypeOptionsEnum.APEXANALYTIX),
      genericApiKey: tn(ServiceCredentialTypeOptionsEnum.APIKEY),
      genericBearerToken: tn(ServiceCredentialTypeOptionsEnum.BEARERTOKEN),
      clearbit: tn(ServiceCredentialTypeOptionsEnum.CLEARBIT),
      insideview: tn(ServiceCredentialTypeOptionsEnum.INSIDEVIEW),
      msteams: tn(ServiceCredentialTypeOptionsEnum.MSTEAMS),
      similarweb: tn(ServiceCredentialTypeOptionsEnum.SIMILARWEB),
      slack: tn(ServiceCredentialTypeOptionsEnum.SLACK),
      genericSimpleOAuth: tn(ServiceCredentialTypeOptionsEnum.OAUTH),
      zoominfo: tn(ServiceCredentialTypeOptionsEnum.ZOOMINFO),
    }),
    []
  );

  const [state, setState] = useSetState<CredentialModalState>(() => ({
    name: credentialData?.name,
    type: getCredentialType(credentialData?.type) as ServiceCredentialTypeOptionsEnum,
    key: (credentialData as ClearBitServiceCredential)?.key,
    username: (credentialData as ZoomInfoServiceCredential)?.username,
    password: (credentialData as ZoomInfoServiceCredential)?.password,
    clientId: (credentialData as InsideViewCredential)?.clientId,
    clientSecret: (credentialData as InsideViewCredential)?.clientSecret,
    endPoint: (credentialData as OAuthCredential)?.endPoint,
  }));

  const close = useCallback(() => dispatch(showCredentialModal({ visible: false })), [dispatch]);

  const updatingExistingCredential = has(credentialData, 'id');

  useToastForFetchStatusChange(updateStatus, {
    success: updatingExistingCredential ? tn('credential_updated') : tn('credential_created'),
  });

  useEffect(() => {
    if (updateStatus === 'success') {
      close();
    }
  }, [close, updateStatus]);

  const save = () => {
    const update = { ...credentialData, ...state };
    if (updatingExistingCredential) {
      update.type = credentialData?.type;
    }
    // TODO: We have to cast since this may not actually be a full service
    // credential. When we have front-end validation we can remove the cast
    // since we'll verify we have a full credential before sending.
    upsertCredential(update as ServiceCredential);
  };

  const onInputChange = ({ target }: React.ChangeEvent<HTMLInputElement>) => setState({ [target.name]: target.value });

  const getKeyFields = () => {
    if (
      !state.type ||
      state.type === ServiceCredentialTypeOptionsEnum.MSTEAMS ||
      state.type === ServiceCredentialTypeOptionsEnum.SLACK
    ) {
      return null;
    }

    if (state.type === ServiceCredentialTypeOptionsEnum.ZOOMINFO) {
      return (
        <Stack>
          <InputGroup className="sycr-input-group" size="default">
            <Row gutter={8}>
              <Col span={5}>
                <span className="synri-label">{tc('username')}</span>
              </Col>
              <Col span={inputSpan}>
                <Input
                  data-testid="credential-input-username"
                  autoComplete={AppConstants.INPUT_AUTOCOMPLETE_OPTIONS.OFF}
                  name="username"
                  onChange={onInputChange}
                  value={state.username || ''}
                />
              </Col>
            </Row>
          </InputGroup>
          <InputGroup className="sycr-input-group" size="default">
            <Row gutter={8}>
              <Col span={5}>
                <span className="synri-label">{tc('password')}</span>
              </Col>
              <Col span={inputSpan}>
                <Input.Password
                  data-testid="credential-input-password"
                  autoComplete={AppConstants.INPUT_AUTOCOMPLETE_OPTIONS.OFF}
                  name="password"
                  onChange={onInputChange}
                  value={state.password || ''}
                />
              </Col>
            </Row>
          </InputGroup>
        </Stack>
      );
    } else if (
      state.type === ServiceCredentialTypeOptionsEnum.CLEARBIT ||
      state.type === ServiceCredentialTypeOptionsEnum.SIMILARWEB ||
      state.type === ServiceCredentialTypeOptionsEnum.SALESINTEL ||
      state.type === ServiceCredentialTypeOptionsEnum.AIDENTIFIED ||
      state.type === ServiceCredentialTypeOptionsEnum.APIKEY ||
      state.type === ServiceCredentialTypeOptionsEnum.BEARERTOKEN ||
      state.type === ServiceCredentialTypeOptionsEnum.APEXANALYTIX
    ) {
      return (
        <>
          <InputGroup className="sycr-input-group" size="default">
            <Row gutter={8}>
              <Col span={5}>
                <span className="synri-label">{tc('key')}</span>
              </Col>
              <Col span={inputSpan}>
                <Input.Password
                  data-testid="credential-input-key"
                  autoComplete="new-password"
                  name="key"
                  onChange={onInputChange}
                  value={state.key}
                />
              </Col>
            </Row>
          </InputGroup>
        </>
      );
    } else if (
      state.type === ServiceCredentialTypeOptionsEnum.INSIDEVIEW ||
      state.type === ServiceCredentialTypeOptionsEnum.OAUTH
    ) {
      return (
        <Stack>
          <InputGroup className="sycr-input-group" size="default">
            <Row gutter={8}>
              <Col span={5}>
                <span className="synri-label">{tn('client_id')}</span>
              </Col>
              <Col span={inputSpan}>
                <Input.Password
                  data-testid="credential-input-client-id"
                  autoComplete={AppConstants.INPUT_AUTOCOMPLETE_OPTIONS.OFF}
                  name="clientId"
                  onChange={onInputChange}
                  value={state.clientId || ''}
                />
              </Col>
            </Row>
          </InputGroup>
          <InputGroup className="sycr-input-group" size="default">
            <Row gutter={8}>
              <Col span={5}>
                <span className="synri-label">{tn('client_secret')}</span>
              </Col>
              <Col span={inputSpan}>
                <Input.Password
                  data-testid="credential-input-client-secret"
                  autoComplete={AppConstants.INPUT_AUTOCOMPLETE_OPTIONS.OFF}
                  name="clientSecret"
                  onChange={onInputChange}
                  value={state.clientSecret || ''}
                />
              </Col>
            </Row>
          </InputGroup>
          {state.type === ServiceCredentialTypeOptionsEnum.OAUTH && (
            <InputGroup className="sycr-input-group" size="default">
              <Row gutter={8}>
                <Col span={5}>
                  <span className="synri-label">{tn('endpoint')}</span>
                </Col>
                <Col span={inputSpan}>
                  <Input
                    data-testid="credential-input-endpoint"
                    autoComplete={AppConstants.INPUT_AUTOCOMPLETE_OPTIONS.OFF}
                    name="endPoint"
                    onChange={onInputChange}
                    value={state.endPoint || ''}
                  />
                </Col>
              </Row>
            </InputGroup>
          )}
        </Stack>
      );
    }
    throw new UnreachableCaseError(state.type);
  };

  const footer = (
    <Fragment>
      <Button key="cancel" onClick={close}>
        {tc('cancel')}
      </Button>
      <Button loading={loading} key="ok" type="primary" onClick={save}>
        {updatingExistingCredential ? tc('update') : tc('create')}
      </Button>
    </Fragment>
  );

  return (
    <Modal
      title={tn(updatingExistingCredential ? 'update_modal_title' : 'create_modal_title')}
      centered
      visible
      footer={footer}
      onOk={close}
      onCancel={close}>
      <Stack>
        {updateError && <Alert message="" description={updateError} type="error" closable />}
        <InputGroup className="sycr-input-group" size="default">
          <Row gutter={8}>
            <Col span={5}>
              <span className="synri-label">{tc('name')}</span>
            </Col>
            <Col span={inputSpan}>
              <Input name="name" onChange={onInputChange} value={state.name} />
            </Col>
          </Row>
        </InputGroup>
        <InputGroup className="sycr-input-group" size="default">
          <Row gutter={8}>
            <Col span={5}>
              <span className="synri-label">{tc('type')}</span>
            </Col>
            <Col span={inputSpan}>
              <Select<ServiceCredentialTypeOptionsEnum>
                data-testid="credential-type-select"
                showSearch
                style={fullWidthStyle}
                // Disable changing credential type when updating
                disabled={!!credentialData?.type}
                value={state.type}
                onChange={(value) => setState({ type: value })}>
                {map(serviceCredentialTypeOptionsMap, (value, key) => (
                  <Option key={key} value={key}>
                    {value}
                  </Option>
                ))}
              </Select>
            </Col>
          </Row>
        </InputGroup>
        {getKeyFields()}
      </Stack>
    </Modal>
  );
};

export default CredentialModal;
