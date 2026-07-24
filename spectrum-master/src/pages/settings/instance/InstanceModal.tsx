//
// Copyright (c) 2019 Syncari All rights reserved.
//
import { Button, Col, Icon, Input, Modal, Row, Select } from 'antd';
import cx from 'classnames';
import { Fragment, useState, useEffect, useCallback, useMemo } from 'react';

import { Stack } from 'components/layout';
import Spinner from 'components/Spinner';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import {
  createInstance,
  Instance,
  InstanceType,
  resetInstanceModalState,
  showInstanceModal,
  updateInstance,
} from 'store/instances/slice';
import { useUserRolesForCurrentInstance } from 'store/user/hooks';
import AppConstants from 'utils/AppConstants';
import CapConstants from 'utils/CapConstants';
import { tc, tNamespaced } from 'utils/i18nUtil';

import './InstanceModal.scss';

const tn = tNamespaced('InstanceModal');

const InputGroup = Input.Group;
const Option = Select.Option;
const labelSpan = 5;
const inputSpan = 18;
const fullWidthStyle = { width: '100%' };
const { FETCH_STATUS } = AppConstants;

interface InputFieldProps {
  children: React.ReactNode;
  label: string;
  name: string;
}

const InputField = ({ name, label, children }: InputFieldProps) => {
  return (
    <InputGroup className="sycr-input-group">
      <Row gutter={8}>
        <Col span={labelSpan}>
          <label className="synri-label" htmlFor={name}>
            {label}
          </label>
        </Col>
        <Col span={inputSpan}>{children}</Col>
      </Row>
    </InputGroup>
  );
};

interface FormValues {
  instanceName: string;
  displayName: string;
  orgId: string;
  planName: string;
  type: InstanceType;
}

const defaultState: FormValues = {
  instanceName: '',
  displayName: '',
  orgId: '',
  planName: 'default',
  type: 'production',
};

export interface InstanceModalProps {
  instance?: Instance;
}

const InstanceModal = ({ instance }: InstanceModalProps) => {
  const dispatch = useEnhancedDispatch();
  const instanceCreatingStatus = useEnhancedSelector((state) => state.instance.instanceCreatingStatus);
  const instanceUpdatingStatus = useEnhancedSelector((state) => state.instance.instanceUpdatingStatus);

  const isEditing = Boolean(instance);

  const { userCan } = useUserRolesForCurrentInstance();
  const isSuperAdmin = userCan([CapConstants.SUPER_ADMIN]);

  const [isSubmitting, setIsSubmitting] = useState(false);
  const [formValues, setFormValues] = useState<FormValues>(() =>
    instance
      ? {
          type: instance.type,
          displayName: instance.displayName,
          name: instance.name,
          syncariId: instance.syncariId,
          instanceName: instance.name,
          orgId: instance.orgId || '',
          planName: instance.planName || '',
        }
      : defaultState
  );

  const _onInputChange = (evt: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = evt.target;
    setFormValues((prev) => ({
      ...prev,
      [name]: value,
    }));
  };

  const handleTypeChange = (value: InstanceType) => {
    setFormValues((prev) => ({ ...prev, type: value }));
  };

  const handlePlanChange = (value: string) => {
    setFormValues((prev) => ({
      ...prev,
      planName: value,
    }));
  };

  const close = useCallback(() => {
    dispatch(resetInstanceModalState());
    dispatch(showInstanceModal(false));
  }, [dispatch]);

  const create = () => {
    dispatch(createInstance(formValues));
  };

  const save = () => {
    dispatch(updateInstance(formValues));
  };

  const buttonText = useMemo(() => {
    if (isEditing) {
      return isSubmitting ? tn('saving') : tc('save');
    }

    return isSubmitting ? tn('creating') : tc('create');
  }, [isEditing, isSubmitting]);

  const footer = (
    <>
      <Button key="cancel" onClick={close}>
        {tc('cancel')}
      </Button>
      <Button
        className={cx('instance-modal__button', isSubmitting && 'instance-modal__button--submitting')}
        disabled={isSubmitting}
        key="ok"
        type="primary"
        onClick={() => (isEditing ? save() : create())}>
        {isSubmitting && <Spinner />}
        {buttonText}
      </Button>
    </>
  );

  // Set sumbitting state for the modal
  useEffect(() => {
    const submitting =
      instanceCreatingStatus === AppConstants.FETCH_STATUS.LOADING ||
      instanceUpdatingStatus === AppConstants.FETCH_STATUS.LOADING;
    setIsSubmitting(submitting);
  }, [instanceCreatingStatus, instanceUpdatingStatus]);

  // Close the modal if the update / creation was successful
  useEffect(() => {
    if (instanceCreatingStatus === FETCH_STATUS.SUCCESS || instanceUpdatingStatus === FETCH_STATUS.SUCCESS) {
      close();
    }
  }, [close, instanceCreatingStatus, instanceUpdatingStatus]);

  return (
    <Modal
      title={tn(isEditing ? 'editing_title' : 'title')}
      centered
      visible
      className="instance-modal"
      footer={footer}
      onOk={() => close()}
      onCancel={() => close()}>
      <Stack>
        <InputField name="instanceName" label={tn('name')}>
          <Input
            id="instanceName"
            disabled={isEditing}
            name="instanceName"
            onChange={_onInputChange}
            value={isEditing ? instance?.name : formValues.instanceName}
          />
        </InputField>
        <InputField name="displayName" label={tn('display_name')}>
          <Input id="displayName" name="displayName" onChange={_onInputChange} value={formValues.displayName} />
        </InputField>
        {!isEditing && (
          <InputField name="plan" label={tn('plan')}>
            <Select
              id="plan"
              suffixIcon={<Icon type="search" />}
              style={fullWidthStyle}
              value={formValues.planName}
              onChange={handlePlanChange}>
              <Option value="default">{tc('default')}</Option>
            </Select>
          </InputField>
        )}
        <InputField name="type" label={tn('type')}>
          <Select id="type" style={fullWidthStyle} value={formValues.type} onChange={handleTypeChange}>
            <Option value="production">{tc('production')}</Option>
            <Option
              value="sandbox"
              disabled={isSuperAdmin ? false : instance && !['internal', 'demo', 'sandbox'].includes(instance.type)}>
              {tc('sandbox')}
            </Option>
            <Option
              value="demo"
              disabled={isSuperAdmin ? false : instance && !['internal', 'demo'].includes(instance.type)}>
              {tc('demo')}
            </Option>
            <Option
              value="internal"
              disabled={isSuperAdmin ? false : instance && !['internal'].includes(instance.type)}>
              {tc('internal')}
            </Option>
            {/* Trial instances are created from the website. We should only show this option if the instance type is already trial. */}
            {instance?.type === 'trial' && <Option value="trial">{tc('trial')}</Option>}
          </Select>
        </InputField>
      </Stack>
    </Modal>
  );
};

export default InstanceModal;
