//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Alert, Button, Col, Input, message, Row, Select } from 'antd';
import { values } from 'lodash';
import { Fragment, useState, useCallback, useMemo } from 'react';
import * as React from 'react';

import { createSubscription, showSubscriptionModal } from 'actions/subscriptionActions';
import { Stack } from 'components/layout';
import Modal from 'components/Modal';
import { useEnhancedDispatch } from 'hooks/redux';
import AppConstants from 'utils/AppConstants';
import { t, tc, tNamespaced } from 'utils/i18nUtil';
import useSetState from 'utils/useSetState';

const InputGroup = Input.Group;
const Option = Select.Option;

const tn = tNamespaced('SubscriptionModal');

const inputSpan = 16;
const labelSpan = 7;

export const PARTNER_MAX_INSTANCE = '75';

const initialState = {
  adminUserName: '',
  adminFirstName: '',
  adminLastName: '',
  instanceName: '',
  orgType: 'standard',
  maxInstance: '',
  instanceType: 'production',
  organizationName: '',
  planName: 'default',
};

type SubscriptionModalState = typeof initialState;

const SubscriptionModal = () => {
  const [formValues, setFormValues] = useSetState(initialState);
  const [validationMessage, setValidationMessage] = useState('');
  const [loading, setLoading] = useState(false);

  const dispatch = useEnhancedDispatch();

  const close = () => {
    setLoading(false);
    setValidationMessage('');
    dispatch(showSubscriptionModal(false));
  };

  const buildUpdateInputHandler = (key: keyof SubscriptionModalState) => (evt: React.ChangeEvent<HTMLInputElement>) =>
    setFormValues({ [key]: evt.target.value });

  const save = () => {
    const { organizationName } = formValues;
    setLoading(true);
    setValidationMessage('');

    dispatch(createSubscription(formValues)).then((response) => {
      setLoading(false);
      if (response.success) {
        close();
        message.success(tn('created', { orgName: organizationName }));
      } else {
        setValidationMessage((response as any).error.response.data.message);
      }
    });
  };

  const isPartnerType = useCallback((type: string) => type === AppConstants.SUBSCRIPTION_TYPES.PARTNER, []);

  const maxInstanceVisible = useMemo(() => isPartnerType(formValues.orgType), [formValues.orgType, isPartnerType]);

  return (
    <Modal
      title={tn('create_title')}
      centered
      visible
      onOk={close}
      onCancel={close}
      footer={
        <Fragment>
          <Button key="cancel" onClick={close}>
            {tc('cancel')}
          </Button>
          <Button key="ok" type="primary" loading={loading} disabled={loading} onClick={save}>
            {tc('create')}
          </Button>
        </Fragment>
      }>
      <Stack>
        {validationMessage && <Alert type="error" message={validationMessage} />}
        <InputGroup className="synri-input-group">
          <Row gutter={8}>
            <Col span={labelSpan}>
              <span className="synri-label">{tn('admin_email')}</span>
            </Col>
            <Col span={inputSpan}>
              <Input
                name="adminUserName"
                onChange={buildUpdateInputHandler('adminUserName')}
                value={formValues.adminUserName}
              />
            </Col>
          </Row>
        </InputGroup>
        <InputGroup className="synri-input-group">
          <Row gutter={8}>
            <Col span={labelSpan}>
              <span className="synri-label">{tn('admin_name')}</span>
            </Col>
            <Col span={inputSpan / 2}>
              <Input
                name="adminFirstName"
                placeholder={tc('first')}
                onChange={buildUpdateInputHandler('adminFirstName')}
                value={formValues.adminFirstName}
              />
            </Col>
            <Col span={inputSpan / 2}>
              <Input
                name="adminLastName"
                placeholder={tc('last')}
                onChange={buildUpdateInputHandler('adminLastName')}
                value={formValues.adminLastName}
              />
            </Col>
          </Row>
        </InputGroup>
        <InputGroup className="synri-input-group">
          <Row gutter={8}>
            <Col span={labelSpan}>
              <span className="synri-label">{tn('instance')}</span>
            </Col>
            <Col span={inputSpan}>
              <Input
                name="instanceName"
                onChange={buildUpdateInputHandler('instanceName')}
                value={formValues.instanceName}
              />
            </Col>
          </Row>
        </InputGroup>

        <InputGroup className="synri-input-group">
          <Row gutter={8}>
            <Col span={labelSpan}>
              <span className="synri-label">{tn('instance_type')}</span>
            </Col>
            <Col span={inputSpan}>
              <Select
                className="full-width"
                defaultValue={formValues.instanceType}
                onChange={(instanceType: string) => setFormValues({ instanceType })}>
                <Option value="production">{tc('production')}</Option>
                <Option value="sandbox">{tc('sandbox')}</Option>
                <Option value="demo">{tc('demo')}</Option>
                <Option value="internal">{tc('internal')}</Option>
              </Select>
            </Col>
          </Row>
        </InputGroup>

        <InputGroup className="synri-input-group">
          <Row gutter={8}>
            <Col span={labelSpan}>
              <span className="synri-label">{tn('organization')}</span>
            </Col>
            <Col span={inputSpan}>
              <Input
                name="organizationName"
                onChange={buildUpdateInputHandler('organizationName')}
                value={formValues.organizationName}
              />
            </Col>
          </Row>
        </InputGroup>
        <InputGroup className="synri-input-group">
          <Row gutter={8}>
            <Col span={labelSpan}>
              <span className="synri-label">{tn('org_type')}</span>
            </Col>
            <Col span={inputSpan}>
              <Select
                className="full-width"
                defaultValue={formValues.orgType}
                onChange={(orgType: string) => {
                  setFormValues({
                    orgType,
                    maxInstance: isPartnerType(orgType) ? PARTNER_MAX_INSTANCE : undefined,
                  });
                }}>
                {values(AppConstants.SUBSCRIPTION_TYPES).map((type) => {
                  return (
                    <Option key={type} value={type}>
                      {tn(type)}
                    </Option>
                  );
                })}
              </Select>
            </Col>
          </Row>
        </InputGroup>
        {maxInstanceVisible && (
          <InputGroup className="synri-input-group">
            <Row gutter={8}>
              <Col span={labelSpan}>
                <span className="synri-label">{t('Settings.SubProfile.maximum_instance')}</span>
              </Col>
              <Col span={inputSpan}>
                <Input
                  name="maxInstance"
                  onChange={buildUpdateInputHandler('maxInstance')}
                  value={formValues.maxInstance}
                />
              </Col>
            </Row>
          </InputGroup>
        )}
        <InputGroup className="synri-input-group">
          <Row gutter={8}>
            <Col span={labelSpan}>
              <span className="synri-label">{tn('plan')}</span>
            </Col>
            <Col span={inputSpan}>
              <Select
                className="full-width"
                defaultValue={formValues.planName}
                onChange={(planName: string) => setFormValues({ planName })}>
                <Option value="default">{tc('default')}</Option>
              </Select>
            </Col>
          </Row>
        </InputGroup>
      </Stack>
    </Modal>
  );
};

export default SubscriptionModal;
