import { navigate, RouteComponentProps, useLocation } from '@reach/router';
import { Button, message, Radio, Select, Spin } from 'antd';
import { capitalize, isEqual } from 'lodash';
import { ChangeEvent, useEffect, useMemo } from 'react';

import InputWithLabel from 'components/inputs/InputWithLabel';
import Switch from 'components/Switch';
import Validator from 'components/validator';
import { useShowModalOnNavigateAway } from 'hooks/useShowModalOnNavigateAway';
import {
  useCreateErrorNotificationsConfigMutation,
  useGetErrorNotificationCadencesQuery,
  useGetErrorNotificationConfigItemQuery,
  useGetErrorNotificationTypesQuery,
  useUpdateErrorNotificationsConfigMutation,
} from 'store/error-notifications-v2/api';
import { ErrorNotificationConfig } from 'store/error-notifications-v2/types';
import { getRtkQueryErrorMessage } from 'utils/getRtkQueryErrorMessage';
import { tc, tNamespaced } from 'utils/i18nUtil';
import { HTTP_URL } from 'utils/RegexUtil';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

import { initialErrorNotificationState, useErrorNotificationContext } from '../context/ErrorNotificationFormContext';
import { NotificationTypes } from '../utils';
import { ConfigureEmail } from './ConfigureEmail';
import { ConfigureWebhook } from './ConfigureWebhook';
import { TestEmail } from './TestEmail';
import { TestWebhook } from './TestWebhook';

const NotificationConfigTypeSpecificForms = {
  webhook: <ConfigureWebhook />,
  email: <ConfigureEmail />,
};

interface ErrorNotificationFormProps extends RouteComponentProps {
  id?: string;
  type?: NotificationTypes;
}

const tn = tNamespaced('Settings.ErrorNotifications');

export function ErrorNotificationForm({ id, type, uri }: ErrorNotificationFormProps) {
  const { data: notificationConfig, isLoading: notificationConfigsIsLoading } = useGetErrorNotificationConfigItemQuery(
    id
  );
  const { data: cadences, isLoading: cadenceIsLoading } = useGetErrorNotificationCadencesQuery();
  const { data: notificationTypesData, isLoading: notificationTypesIsLoading } = useGetErrorNotificationTypesQuery();
  const [
    saveErrorNotificationsConfig,
    { isLoading: isCreating, status: createStatus },
  ] = useCreateErrorNotificationsConfigMutation();
  const [
    updateErrorNotificationsConfig,
    { isLoading: isUpdating, status: updateStatus },
  ] = useUpdateErrorNotificationsConfigMutation();

  const {
    errorNotificationFormState,
    errorNotificationFormState: { cadence, description, emails, endpoint, name, notificationTypeIds, status, headers },
    errorNotificationServerState,
    setErrorNotificationServerState,
    setErrorNotificationFormState,
    reset,
  } = useErrorNotificationContext();

  const location = useLocation();

  const hasChanged = useMemo(() => {
    if (!id) {
      return !isEqual(initialErrorNotificationState, errorNotificationFormState);
    } else {
      return !isEqual(errorNotificationFormState, errorNotificationServerState);
    }
  }, [errorNotificationFormState, errorNotificationServerState, id]);

  const operation = location.pathname.includes('add') ? 'Create' : 'Edit';
  const title = `${operation} ${type} notification`;

  useShowModalOnNavigateAway(
    uri,
    hasChanged && createStatus !== 'fulfilled' && updateStatus !== 'fulfilled',
    title,
    tn('leave_form_page')
  );

  useEffect(() => {
    return () => {
      reset();
    };
  }, [reset]);

  useEffect(() => {
    if (id && notificationConfig) {
      const headers = notificationConfig.configuration?.headers || {};
      const stateObject = {
        name: notificationConfig.name || '',
        cadence: notificationConfig.cadence,
        description: notificationConfig.description || '',
        status: notificationConfig.status,
        notificationTypeIds: notificationConfig.notificationTypes || [],
        emails: notificationConfig.configuration?.emails?.map((emailObj) => emailObj?.email || '') || [],
        endpoint: {
          selectValue: notificationConfig.configuration?.httpMethod || '',
          textValue: notificationConfig.configuration?.url || '',
        },
        headers: Object.keys(headers).map((key) => ({ key, value: headers[key] })),
      };
      setErrorNotificationFormState(stateObject);
      setErrorNotificationServerState(stateObject);
    }
  }, [id, notificationConfig, setErrorNotificationFormState, setErrorNotificationServerState]);

  const isReadyToSubmit = useMemo(() => {
    return {
      email: name !== '' && !!notificationTypeIds?.length && !!emails?.length,
      webhook: name !== '' && !!notificationTypeIds?.length && HTTP_URL.test(endpoint.textValue),
    };
  }, [emails, endpoint, name, notificationTypeIds]);

  const notificationTypesDropdownOptions = useMemo(() => {
    return notificationTypesData?.map((notification) => (
      <Select.Option value={notification.id} key={notification.id}>
        {notification.title}
      </Select.Option>
    ));
  }, [notificationTypesData]);

  const cadenceRadioOptions = useMemo(() => {
    if (cadenceIsLoading) {
      return <Spin />;
    }
    return cadences?.map((cadence) => {
      return (
        <Radio className="error-notifications__form__cadence-radio" key={cadence.frequency} value={cadence.frequency}>
          {cadence.label}
        </Radio>
      );
    });
  }, [cadences, cadenceIsLoading]);

  function resetAndRedirect() {
    reset();
    navigate(makeUrl(RouteConstants.SETTINGS_NOTIFICATIONS_TYPE, { type }));
  }

  function onSubmit() {
    if (!type) {
      return;
    }

    const getConfigurationObject = {
      email: { emails: emails?.map((email) => ({ email })) },
      webhook: {
        headers: headers?.reduce((acc: Record<string, string>, curr) => {
          const key = curr.key || '';
          acc[key] = curr.value || '';
          return acc;
        }, {}),
        url: endpoint?.textValue,
        httpMethod: endpoint?.selectValue,
      },
    };

    const payload: ErrorNotificationConfig = {
      cadence,
      description,
      name,
      notificationTypes: notificationTypeIds,
      type,
      status,
      configuration: getConfigurationObject[type],
    };

    if (operation === 'Create') {
      saveErrorNotificationsConfig(payload)
        .unwrap()
        .then(() => {
          message.success(tn('crud_success', { type, operation: 'created' }));
          resetAndRedirect();
        })
        .catch((error) =>
          message.error(getRtkQueryErrorMessage(error, tn('crud_error', { type, operation: 'creating' })))
        );
    } else if (operation === 'Edit') {
      updateErrorNotificationsConfig({ ...payload, id })
        .unwrap()
        .then(() => {
          message.success(tn('crud_success', { type, operation: 'updated' }));
          resetAndRedirect();
        })
        .catch((error) =>
          message.error(getRtkQueryErrorMessage(error, tn('crud_error', { type, operation: 'updating' })))
        );
    }
  }

  function renderNotificationTest() {
    if (type === 'email' && operation === 'Edit') {
      return <TestEmail />;
    }
    if (type === 'webhook') {
      return <TestWebhook />;
    }
    return null;
  }

  if (!type) {
    return null;
  }

  if (notificationConfigsIsLoading) {
    return <Spin />;
  }

  return (
    <div className="error-notifications__form">
      <h2>{title}</h2>

      <Validator.Form id="error-notifications-form" onSubmit={onSubmit}>
        <Validator.Field
          name="name"
          validationOptions={{ required: true }}
          onChange={(e: ChangeEvent<HTMLInputElement>) => setErrorNotificationFormState({ name: e.target.value })}
          value={name}
          render={(validatorProps) => (
            <InputWithLabel
              {...validatorProps}
              required
              help={validatorProps.errorMessage}
              label={tc('name')}
              tooltip={tn('notification_name_tooltip', { type: capitalize(type) })}
              id="name"
              validateStatus={validatorProps.isValid ? 'success' : 'error'}
            />
          )}
        />

        <InputWithLabel
          label={tc('description')}
          tooltip={tn('notification_description_tooltip')}
          datatype="textarea"
          value={description}
          id="description"
          onChange={(e: ChangeEvent<HTMLInputElement>) =>
            setErrorNotificationFormState({ description: e.target.value })
          }
        />

        {NotificationConfigTypeSpecificForms[type]}

        <InputWithLabel
          label={tc('notification_type')}
          required
          tooltip={tn('notification_type_tooltip')}
          input={
            <Select
              data-testid="notificationType"
              className="error-notifications__form__notification-type-selection"
              placeholder={tn('notification_types_placeholder')}
              mode="multiple"
              dropdownMatchSelectWidth={false}
              value={notificationTypeIds}
              onChange={(value: string[]) => setErrorNotificationFormState({ notificationTypeIds: value })}
              showArrow
              loading={notificationTypesIsLoading}>
              {notificationTypesDropdownOptions}
            </Select>
          }
        />

        <InputWithLabel
          datatype="boolean"
          label={tc('status')}
          tooltip={tn('notification_status_tooltip', { type })}
          input={
            <Switch
              size="small"
              className="error-notifications__form__status-switch"
              checked={status === 'Active'}
              onChange={(checked) => setErrorNotificationFormState({ status: checked ? 'Active' : 'Inactive' })}
              label={status}
            />
          }
        />

        <InputWithLabel
          label={tc('schedule')}
          tooltip={tn('notification_schedule_tooltip')}
          input={
            <Radio.Group
              value={cadence}
              onChange={(e) => {
                setErrorNotificationFormState({ cadence: e.target.value });
              }}>
              {cadenceRadioOptions}
            </Radio.Group>
          }
        />

        {renderNotificationTest()}

        <div className="error-notifications__form__footer">
          <Button
            className="error-notifications__form__cancel-button"
            onClick={() => navigate(makeUrl(RouteConstants.SETTINGS_NOTIFICATIONS_TYPE, { type }))}>
            {tc('cancel')}
          </Button>

          <Button
            loading={isCreating || isUpdating}
            htmlType="submit"
            type="primary"
            disabled={!isReadyToSubmit[type] || !hasChanged}>
            {tc('save')}
          </Button>
        </div>
      </Validator.Form>
    </div>
  );
}
