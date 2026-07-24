//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Button, Input, Select, Upload, message } from 'antd';
import { RcFile } from 'antd/lib/upload';
import * as React from 'react';
import { useCallback, useEffect, useState } from 'react';

import DrawerPanel from 'components/DrawerPanel';
import { useI18nContext, withI18n } from 'components/I18nProvider';
import InlineMessage, { Types as InlineMessageTypes } from 'components/InlineMessage';
import { Stack } from 'components/layout';
import { Text, TranslatedText } from 'components/typography';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import useEffectForValue from 'hooks/useEffectForValue';
import {
  ReferenceDataRecord,
  upsertReferenceData,
  useReferenceDataList,
  useReferenceDataPreview,
} from 'store/reference-data';
import AppConstants from 'utils/AppConstants';
import { ValuesOf } from 'utils/TypeUtils';

import './ReferenceDataUpsertModal.less';

const Option = Select.Option;
const fullWidthStyle = { width: '100%' };

const { REFDATAMETA_CONST } = AppConstants;

interface InputFieldProps {
  children: React.ReactNode;
  label: string;
  htmlFor: string;
  error?: string;
}

const InputField = ({ htmlFor, label, error, children }: InputFieldProps) => {
  return (
    <Stack spacing="z">
      <label className="synri-label" htmlFor={htmlFor}>
        {label}
      </label>
      {children}
      {error && <Text color="red-500">{error}</Text>}
    </Stack>
  );
};

type ReferenceDataFileType = ValuesOf<typeof REFDATAMETA_CONST.SOURCE_TYPE>;

type FormValues = {
  id: string | undefined;
  name: string;
  type: ReferenceDataFileType;
  csvFile: RcFile | null;
  secretKey: string | null;
  accessKey: string | null;
};

type FormErrors = Partial<Record<keyof FormValues, string>>;

export interface ReferenceDataModalProps {
  onRequestClose: () => void;
  referenceData?: ReferenceDataRecord;
  visible: boolean;
}

const blankFormState: FormValues = {
  id: undefined,
  name: '',
  type: REFDATAMETA_CONST.SOURCE_TYPE.CSV_UPLOAD,
  csvFile: null,
  secretKey: null,
  accessKey: null,
};

const ReferenceDataModal = ({ onRequestClose, referenceData, visible }: ReferenceDataModalProps) => {
  const { tn } = useI18nContext();
  const dispatch = useEnhancedDispatch();
  const stateReferenceData = useEnhancedSelector((state) => state.referenceData);
  const { error, status } = {
    error: stateReferenceData.upsertError[referenceData?.id || 'new'],
    status: stateReferenceData.upsertStatus[referenceData?.id || 'new'],
  };

  const [formValues, setFormValues] = useState<FormValues>(blankFormState);
  const [formErrors, setFormErrors] = useState<FormErrors>({});

  const isUpdating = !!referenceData?.id;

  const { refetch: refetchDataPreview } = useReferenceDataPreview(referenceData?.id || '');
  const { refetch: refetchSidebarSection } = useReferenceDataList();

  useEffect(() => {
    if (isUpdating && visible) {
      setFormValues({
        ...blankFormState,
        id: referenceData.id,
        name: referenceData.name || '',
      });
    }
  }, [referenceData, isUpdating, visible]);

  const closePanel = useCallback(() => {
    setFormErrors({});
    setFormValues(blankFormState);
    onRequestClose();
  }, [onRequestClose]);

  const onSuccessfulSave = useCallback(() => {
    message.success(isUpdating ? tn('update_success') : tn('create_success'));
    closePanel();
  }, [closePanel, isUpdating, tn]);

  const loading = status === AppConstants.FETCH_STATUS.LOADING;
  useEffectForValue(status, AppConstants.FETCH_STATUS.SUCCESS, onSuccessfulSave);

  const handleInputChange = (evt: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = evt.target;

    setFormValues((prev) => ({
      ...prev,
      [name]: value,
    }));
    setFormErrors((prev) => {
      const newErrors = { ...prev };
      delete newErrors[name as keyof FormErrors];
      return newErrors;
    });
  };

  const handleTypeChange = (value: ReferenceDataFileType) => {
    setFormValues((prev) => ({
      ...prev,
      type: value,
    }));
  };

  const validateInputs = () => {
    const errors: FormErrors = {};

    if (!formValues.name) {
      errors.name = tn('name_required');
    }

    if (!formValues.type) {
      errors.type = tn('type_required');
    } else if (formValues.type === REFDATAMETA_CONST.SOURCE_TYPE.CSV_UPLOAD && !formValues.csvFile) {
      errors.csvFile = tn('file_required');
    }

    setFormErrors(errors);
    return !Boolean(Object.keys(errors).length);
  };

  const save = async () => {
    if (validateInputs()) {
      await dispatch(
        upsertReferenceData({
          id: formValues.id,
          type: formValues.type,
          name: formValues.name,
          csvFile: formValues.csvFile,
        })
      );
      await refetchDataPreview();
      await refetchSidebarSection();
    }
  };

  const getActionName = () => {
    if (isUpdating) {
      return <TranslatedText namespace="Common" text="update" />;
    }

    if (formValues.type === REFDATAMETA_CONST.SOURCE_TYPE.CSV_UPLOAD) {
      return <TranslatedText text="import" />;
    }

    return <TranslatedText namespace="Common" text="add" />;
  };

  const getSourceInputs = () => {
    if ([REFDATAMETA_CONST.SOURCE_TYPE.CSV_UPLOAD].includes(formValues.type)) {
      return [
        <InputField htmlFor="csv-upload" label={tn('csvFile')} error={formErrors.csvFile} key="csv-upload-input">
          <div>
            <Upload
              id="csv-upload"
              accept=".csv"
              multiple={false}
              beforeUpload={(csvFile) => {
                setFormValues((prev) => ({
                  ...prev,
                  csvFile,
                }));
                return false;
              }}>
              <Button icon="upload">{tn('upload')}</Button>
            </Upload>
          </div>
        </InputField>,
      ];
    }

    return null;
  };

  return (
    <DrawerPanel
      title={isUpdating ? tn('update_title') : tn('create_title')}
      onClose={closePanel}
      absolutePositioning
      destroyOnClose
      mask
      maskClosable
      visible={visible}
      footer={
        <>
          <Button key="cancel" onClick={closePanel}>
            {tn('cancel')}
          </Button>
          <Button key="ok" type="primary" onClick={save} loading={loading} disabled={loading}>
            {getActionName()}
          </Button>
        </>
      }>
      <Stack>
        {error && (
          <InlineMessage type={InlineMessageTypes.ERROR} title={error}>
            {error}
          </InlineMessage>
        )}
        <InputField htmlFor="dataset-name" label={tn('name')} error={formErrors.name}>
          <Input
            id="dataset-name"
            name="name"
            onChange={handleInputChange}
            value={formValues.name}
            disabled={isUpdating}
            required
          />
        </InputField>

        <InputField htmlFor="type" label={tn('type')} error={formErrors.type}>
          <Select
            id="type"
            style={fullWidthStyle}
            value={formValues.type}
            onChange={handleTypeChange}
            disabled={isUpdating}>
            <Option value={REFDATAMETA_CONST.SOURCE_TYPE.CSV_UPLOAD}>{tn('csvFile')}</Option>
          </Select>
        </InputField>

        {getSourceInputs()}
      </Stack>
    </DrawerPanel>
  );
};

export default withI18n(ReferenceDataModal, 'ReferenceDataModal');
