import { Input } from 'antd';
import { TextAreaProps } from 'antd/lib/input';
import cx from 'classnames';

import Checkbox, { CheckboxChangeEvent } from 'components/Checkbox';
import Tags, { TagValueModel } from 'components/inputs/Tag';
import { HStack, Stack } from 'components/layout';
import SingleFileUploadBox, { SingleFileUploadBoxProps } from 'components/SingleFileUploadBox';
import { Text } from 'components/typography';

import { InputProps } from '../Input';
import Select from '../Select';
import { PicklistValue } from '../types';

import './DrawerInput.less';

interface InputFieldProps {
  children: React.ReactNode;
  label?: string;
  htmlFor?: string;
  error?: string;
  labelActionText?: string;
  onLabelActionClick?: () => void;
  labelActionDisabled?: boolean;
}

const DrawerInputField = ({
  htmlFor,
  label,
  labelActionText,
  onLabelActionClick,
  labelActionDisabled,
  error,
  children,
}: InputFieldProps) => {
  return (
    <Stack spacing="z">
      <label className={cx('synri-label', 'side-drawer__label')} htmlFor={htmlFor}>
        {label}
        {onLabelActionClick && (
          <div
            onClick={labelActionDisabled ? undefined : onLabelActionClick}
            className={cx('side-drawer__label-action', { 'side-drawer__label-action--disabled': labelActionDisabled })}>
            {labelActionText}
          </div>
        )}
      </label>
      {children}
      {error && <Text color="red-500">{error}</Text>}
    </Stack>
  );
};

type DrawerTextInputProps = Pick<InputFieldProps, 'labelActionText' | 'onLabelActionClick'> & {
  label: string;
  error?: string;
  htmlFor?: string;
  name?: string;
  value: string;
  disabled?: boolean;
  textArea?: boolean;
  labelActionDisabled?: boolean;
  onChange?: TextAreaProps['onChange'] | InputProps['onChange'];
};

const { TextArea } = Input;

export const DrawerTextInput = ({
  label,
  labelActionText,
  labelActionDisabled,
  onLabelActionClick,
  error,
  htmlFor,
  name,
  onChange,
  value,
  disabled,
  textArea = false,
}: DrawerTextInputProps) => {
  return (
    <DrawerInputField
      labelActionText={labelActionText}
      onLabelActionClick={onLabelActionClick}
      labelActionDisabled={labelActionDisabled || false}
      htmlFor={htmlFor}
      label={label}
      error={error}>
      {textArea ? (
        <TextArea
          id={htmlFor}
          name={name ?? label}
          onChange={onChange as TextAreaProps['onChange']}
          value={value}
          disabled={disabled}
          required
        />
      ) : (
        <Input
          maxLength={254}
          id={htmlFor}
          name={name ?? label}
          onChange={onChange as InputProps['onChange']}
          value={value}
          disabled={disabled}
          required
        />
      )}
    </DrawerInputField>
  );
};

type DrawerSelectInputProps = Pick<DrawerTextInputProps, 'label' | 'htmlFor' | 'error' | 'disabled'> &
  Pick<InputFieldProps, 'labelActionText' | 'onLabelActionClick'> & {
    options: PicklistValue[];
    onChange?: (option: string) => void;
    value: any;
    showMetaData?: boolean;
  };

export const DrawerSelectInput = ({
  label,
  labelActionText,
  onLabelActionClick,
  error,
  htmlFor,
  onChange,
  value,
  disabled,
  options,
}: DrawerSelectInputProps) => {
  return (
    <DrawerInputField
      labelActionText={labelActionText}
      onLabelActionClick={onLabelActionClick}
      htmlFor={htmlFor}
      label={label}
      error={error}>
      <Select
        optionData={options}
        className="side-drawer__select"
        value={value}
        onChange={onChange}
        disabled={disabled}
      />
    </DrawerInputField>
  );
};

type DrawerFileInputProps = Pick<
  SingleFileUploadBoxProps,
  'file' | 'beforeUpload' | 'fileTypeRestriction' | 'onRemove' | 'helpText'
> & {
  label: string;
  showMetaData?: boolean;
  error?: string;
};

export const DrawerFileInput = ({
  label,
  beforeUpload,
  file,
  fileTypeRestriction,
  showMetaData,
  onRemove,
  helpText,
  error,
}: DrawerFileInputProps) => {
  return (
    <DrawerInputField label="" error={error}>
      <SingleFileUploadBox
        className="side-drawer__upload-box"
        beforeUpload={beforeUpload}
        selectButtonText={label}
        helpText={helpText}
        file={file}
        fileTypeRestriction={fileTypeRestriction}
        showMetaData={showMetaData}
        onRemove={onRemove}
      />
    </DrawerInputField>
  );
};

interface DrawerTagInputProps {
  id: string;
  label?: string;
  disabled?: boolean;
  placeholder?: string;
  onChange: (values: TagValueModel) => void;
  value?: TagValueModel;
  defaultValue?: TagValueModel;
}

export const DrawerTagInput = ({
  id,
  label,
  disabled,
  placeholder,
  onChange,
  value,
  defaultValue,
}: DrawerTagInputProps) => {
  return (
    <DrawerInputField label={label}>
      <Tags
        id={id}
        onChange={onChange}
        defaultValue={defaultValue}
        value={value}
        disabled={disabled || false}
        emptyPlaceholder={placeholder}
      />
    </DrawerInputField>
  );
};

interface DrawerCheckboxInputProps {
  label?: string;
  disabled?: boolean;
  onChange: (e: CheckboxChangeEvent) => void;
  value?: boolean;
}

export const DrawerCheckboxInput = ({ label, disabled, onChange, value }: DrawerCheckboxInputProps) => {
  return (
    <HStack spacing="z">
      <Checkbox
        onChange={onChange}
        checked={value}
        disabled={disabled || false}
        id={label}
        className="side-drawer__checkbox"
      />
      <label htmlFor={label} className={cx('synri-label', 'side-drawer__checkbox-label')}>
        {label}
      </label>
    </HStack>
  );
};
