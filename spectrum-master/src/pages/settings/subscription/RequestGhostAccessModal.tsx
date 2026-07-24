//
// Copyright (c) 2019-Present Syncari All rights reserved.
//

import React, { useCallback, useEffect, useMemo, useState } from 'react';

import Button from 'components/Button';
import DrawerPanel from 'components/DrawerPanel';
import { useI18nContext, withI18n } from 'components/I18nProvider';
import InlineMessage, { Types as InlineMessageTypes } from 'components/InlineMessage';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { HStack } from 'components/layout';
import { useEnhancedDispatch as useDispatch, useEnhancedSelector as useSelector } from 'hooks/redux';
import { Instance } from 'store/instances/slice';
import { useRequestGhostAccessMutation } from 'store/user/api';
import { getAllRoles, getProfile, getUserInstances } from 'store/user/thunks';
import { RequestGhost } from 'store/user/types';
import AppConstants from 'utils/AppConstants';

export interface RequestGhostAccessProps {
  visible: boolean;
  setVisible: (visible: boolean) => void;
  instance: Instance | null;
}

type DurationValue = '8 hours' | '1 day' | '2 days' | '30 days';

type CategoryValue =
  | 'Professional Services'
  | 'Synapse Approval'
  | 'Troubleshooting'
  | 'Status Check'
  | 'Data Related Activities'
  | 'Other';

const RequestGhostAccessModal = ({ visible, setVisible, instance }: RequestGhostAccessProps) => {
  const [requestGhostAccess] = useRequestGhostAccessMutation();
  const [formData, setFormData] = useState<Partial<RequestGhost>>({});
  const allRoles = useSelector((state) => state.user.allRoles);
  const dispatch = useDispatch();
  const [errorMessage, setErrorMessage] = useState('');
  const { tc, tn } = useI18nContext();

  const durations: { label: string; value: DurationValue }[] = useMemo(
    () => [
      {
        label: tn('8_hours'),
        value: '8 hours',
      },
      {
        label: tn('1_day'),
        value: '1 day',
      },
      {
        label: tn('2_days'),
        value: '2 days',
      },
      {
        label: tn('30_days'),
        value: '30 days',
      },
    ],
    [tn]
  );

  const categories: { label: string; value: CategoryValue }[] = useMemo(
    () => [
      {
        label: tn('professional_services'),
        value: 'Professional Services',
      },
      {
        label: tn('synapse_approval'),
        value: 'Synapse Approval',
      },
      {
        label: tn('troubleshooting'),
        value: 'Troubleshooting',
      },
      {
        label: tn('status_check'),
        value: 'Status Check',
      },
      {
        label: tn('data_related_activities'),
        value: 'Data Related Activities',
      },
      {
        label: tn('other'),
        value: 'Other',
      },
    ],
    [tn]
  );

  useEffect(() => {
    if (!allRoles?.length) {
      dispatch(getAllRoles());
    }
  }, [allRoles, dispatch]);

  useEffect(() => {
    setFormData({ duration: '8 Hours' });
    setErrorMessage('');
  }, [tn, visible]);

  const roles = useMemo(
    () =>
      allRoles
        .map((role) => ({ value: role.id, label: role.name }))
        .sort((a, b) => a.label.toLowerCase().localeCompare(b.label.toLowerCase())),
    [allRoles]
  );

  const close = useCallback(() => setVisible(false), [setVisible]);

  const submit = useCallback(() => {
    const { category, roleId, duration, reason } = formData;
    // TODO: Validations
    if (category && roleId && duration && instance?.syncariId) {
      requestGhostAccess({ syncariId: instance.syncariId, category, roleId, duration, reason })
        .unwrap()
        .then(() => {
          dispatch(getUserInstances());
          dispatch(getProfile());
          close();
        })
        .catch((resp) => setErrorMessage(resp.data.message || resp.data.error));
    }
  }, [close, dispatch, formData, instance?.syncariId, requestGhostAccess]);

  const onSelectChange = useCallback(({ name, value }: any) => setFormData({ ...formData, [name]: value }), [formData]);

  return (
    <DrawerPanel
      title={tn('title')}
      onClose={close}
      visible={visible}
      footer={
        <HStack justify="space-between">
          <Button onClick={close} type="danger" ghost>
            {tc('cancel')}
          </Button>
          <Button onClick={submit} type="primary">
            {tn('request')}
          </Button>
        </HStack>
      }>
      <div>
        <InlineMessage type={InlineMessageTypes.ERROR} title={errorMessage}>
          {errorMessage}
        </InlineMessage>
        <InputWithLabel
          datatype="text"
          label={tn('instance')}
          value={instance?.displayName}
          name="syncariId"
          displayMode={AppConstants.INPUT_DISPLAY_MODE.READONLY}
        />
        <InputWithLabel
          datatype="picklist"
          label={tn('role')}
          value={formData.roleId}
          name="roleId"
          data-testid="roleId"
          values={roles}
          defaultValue={formData.roleId}
          onChange={(value: string) => onSelectChange({ name: 'roleId', value })}
        />
        <InputWithLabel
          datatype="picklist"
          label={tn('access_reason')}
          value={formData.category}
          name="category"
          data-testid="accessReason"
          values={categories}
          defaultValue={formData.category}
          onChange={(value: string) => onSelectChange({ name: 'category', value })}
        />
        {formData.category === 'Other' && (
          <InputWithLabel
            datatype="textarea"
            label={tn('reason')}
            value={formData.reason}
            name="reason"
            values={categories}
            defaultValue={formData.reason}
            onChange={(evt: React.ChangeEvent<HTMLInputElement>) =>
              setFormData({ ...formData, reason: evt.target.value })
            }
          />
        )}
        <InputWithLabel
          datatype="picklist"
          label={tn('access_duration')}
          value={formData.duration}
          name="duration"
          values={durations}
          defaultValue={formData.duration}
          onChange={(value: string) => onSelectChange({ name: 'duration', value })}
        />
      </div>
    </DrawerPanel>
  );
};

export default withI18n(RequestGhostAccessModal, 'RequestGhostAccessModal');
