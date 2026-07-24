import { Button, Form, Modal } from 'antd';
import { ChangeEvent, useCallback, useEffect, useMemo, useState } from 'react';

import EmailInput from 'components/EmailInput';
import { useI18nContext, withI18n } from 'components/I18nProvider';
import InputWithLabel from 'components/inputs/InputWithLabel';
import Select from 'components/inputs/Select';
import { ValidateStatuses } from 'components/inputs/types';
import { Stack } from 'components/layout';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { EMPTY_ARRAY } from 'store/constants';
import { copyInstance, Instance, InstanceCopyModalType, showInstanceCopyModal } from 'store/instances/slice';
import { ALPHA_NUMERIC_REGEX, LOOSE_EMAIL_REGEX } from 'utils/RegexUtil';
import './InstanceCopyModal.less';

interface InstanceCopyModalValidation {
  sourceIdValidationStatus?: ValidateStatuses;
  sourceIdHelp?: string;
  destinationIdValidationStatus?: ValidateStatuses;
  destinationIdHelp?: string;
  recipientsValidationStatus?: ValidateStatuses;
  recipientsHelp?: string;
}

export const InstanceCopyModal = withI18n(() => {
  const { tn } = useI18nContext();
  const dispatch = useEnhancedDispatch();
  const { instanceCopyModal: modalState, instances } = useEnhancedSelector((state) => state.instance);

  const { visible, modalType, syncariId } = modalState;

  const validationIntialState: InstanceCopyModalValidation = useMemo(
    () => ({
      recipientsHelp: tn('recipients_help'),
    }),
    [tn]
  );

  const getInstances = useCallback(
    (instances: Instance[]) => {
      return instances
        .filter((instance) => instance.syncariId !== syncariId)
        .map((instance) => ({ label: instance.syncariId, value: instance.syncariId }));
    },
    [syncariId]
  );

  const [dropdownOptions, setDropdownOptions] = useState(getInstances(instances));
  const [sourceInstanceId, setSourceInstanceId] = useState(syncariId || '');
  const [destinationInstanceId, setDestinationInstanceId] = useState('');
  const [recipients, setRecipients] = useState<string[]>(EMPTY_ARRAY);
  const [validation, setValidation] = useState(validationIntialState);
  const [isSubmitDisabled, setIsSubmitDisabled] = useState(true);

  const resetFormState = useCallback(() => {
    setSourceInstanceId('');
    setDestinationInstanceId('');
    setRecipients(EMPTY_ARRAY);
    setValidation(validationIntialState);
    setIsSubmitDisabled(true);
  }, [validationIntialState]);

  useEffect(() => {
    resetFormState();
  }, [visible, resetFormState]);

  useEffect(() => {
    if (syncariId) {
      setDropdownOptions(getInstances(instances));
    }
  }, [syncariId, instances, getInstances, visible]);

  useEffect(() => {
    if (syncariId) {
      setSourceInstanceId(syncariId);
    }
  }, [syncariId, visible]);

  useEffect(() => {
    if (syncariId && dropdownOptions.length > 0) {
      setDestinationInstanceId(dropdownOptions[0].value);
    }
  }, [syncariId, dropdownOptions, visible]);

  useEffect(() => {
    const isAnyInputEmpty =
      !sourceInstanceId || !destinationInstanceId || (Array.isArray(recipients) && recipients.length === 0);
    const isAnyInputInvalid =
      !!validation.destinationIdValidationStatus ||
      !!validation.sourceIdValidationStatus ||
      !!validation.recipientsValidationStatus;

    setIsSubmitDisabled(isAnyInputEmpty || isAnyInputInvalid);
  }, [sourceInstanceId, destinationInstanceId, recipients, validation]);

  const onSourceIdChange = useCallback(
    (e: ChangeEvent<HTMLInputElement>) => {
      const value = e.target.value;
      const isValid = ALPHA_NUMERIC_REGEX.test(value);

      setSourceInstanceId(e.target.value);
      setValidation((prev) => ({
        ...prev,
        sourceIdValidationStatus: isValid ? ValidateStatuses.BLANK : ValidateStatuses.ERROR,
        sourceIdHelp: isValid ? '' : tn('instance_input_error'),
      }));
    },
    [tn]
  );

  const onDestinationIdChange = useCallback(
    (e: ChangeEvent<HTMLInputElement>) => {
      const value = e.target.value;
      const isValid = ALPHA_NUMERIC_REGEX.test(value);

      setDestinationInstanceId(e.target.value);
      setValidation((prev) => ({
        ...prev,
        destinationIdValidationStatus: isValid ? ValidateStatuses.BLANK : ValidateStatuses.ERROR,
        destinationIdHelp: isValid ? '' : tn('instance_input_error'),
      }));
    },
    [tn]
  );

  const onEmailChange = useCallback(
    (targetValue: string[]) => {
      const recipients = targetValue.map((recipient) => recipient.trim());

      const areEmailsInvalid = recipients.some((recipient) => !LOOSE_EMAIL_REGEX.test(recipient));

      setRecipients(recipients);
      setValidation((prev) => ({
        ...prev,
        recipientsValidationStatus: areEmailsInvalid ? ValidateStatuses.ERROR : ValidateStatuses.BLANK,
        recipientsHelp: areEmailsInvalid ? tn('recipients_error') : tn('recipients_help'),
      }));
    },
    [tn]
  );

  const close = () => {
    dispatch(showInstanceCopyModal({ visible: false, modalType, syncariId }));
  };

  const submit = () => {
    dispatch(copyInstance({ sourceInstanceId, destinationInstanceId, emailRecipients: recipients }));
    dispatch(showInstanceCopyModal({ visible: false, modalType, syncariId }));
  };

  const footer = (
    <>
      <Button onClick={close}>{tn('cancel_button_label')}</Button>
      <Button type="primary" disabled={isSubmitDisabled} onClick={submit}>
        {tn('submit_button_label')}
      </Button>
    </>
  );

  return (
    <Modal visible={visible} centered title={tn('title')} onCancel={close} onOk={submit} footer={footer}>
      <Stack>
        <InputWithLabel
          label={tn('source_instance_label')}
          name="sourceInstance"
          id="sourceInstance"
          type="text"
          placeholder={tn('source_instance_placeholder')}
          disabled={modalType === InstanceCopyModalType.ORG_ONLY}
          value={sourceInstanceId}
          validateStatus={validation.sourceIdValidationStatus ? 'error' : undefined}
          help={validation.sourceIdHelp}
          onChange={onSourceIdChange}
        />
        {modalType === InstanceCopyModalType.GLOBAL ? (
          <InputWithLabel
            label={tn('destination_instance_label')}
            id="destinationInstance"
            name="destinationInstance"
            type="text"
            placeholder={tn('destination_instance_placeholder')}
            value={destinationInstanceId}
            validateStatus={validation.destinationIdValidationStatus ? 'error' : undefined}
            help={validation.destinationIdHelp}
            onChange={onDestinationIdChange}
          />
        ) : (
          <Stack spacing="xs" className="destination-instance-dropdown-container">
            <label className="synri-label" htmlFor="destinationInstance">
              {tn('destination_instance_label')}
            </label>
            <Select
              className="destination-instance-select"
              id="destinationInstance"
              value={destinationInstanceId}
              dropdownMatchSelectWidth={false}
              optionData={dropdownOptions}
              onChange={(value) => {
                setDestinationInstanceId(value.toString());
              }}
            />
          </Stack>
        )}
        <Form.Item
          help={validation.recipientsHelp}
          validateStatus={validation.recipientsValidationStatus ? 'error' : undefined}
          className="recipients-container">
          <label className="synri-label" htmlFor="destinationInstance">
            {tn('recipients_label')}
          </label>
          <EmailInput value={recipients} onChange={onEmailChange} />
        </Form.Item>
      </Stack>
    </Modal>
  );
}, 'Settings.InstanceCopyModal');
