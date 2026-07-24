import { ChangeEventHandler, FocusEventHandler, useEffect, useState } from 'react';

import { tNamespaced } from 'utils/i18nUtil';

import { hasNoValue, hasSpaces, hasSpecialCharacters } from './validationFunctions';

const tn = tNamespaced('FieldValidator');

export interface ValidationOptions {
  required?: boolean;
  noSpecialChars?: boolean;
  noSpaces?: boolean;
}

export interface FieldValidatorProps {
  name: string;
  onBlur?: FocusEventHandler<HTMLInputElement>;
  onChange?: ChangeEventHandler<HTMLInputElement>;
  render: (props: FieldValidatorRenderProps) => JSX.Element;
  validationOptions: ValidationOptions;
  value?: string;

  // Props passed from parent Form Validator
  setFieldValidity?: (name: string, isValid: boolean) => void;
  submitAttempted?: boolean;
}

export interface FieldValidatorRenderProps {
  errorMessage: string;
  isValid: boolean;
  onBlur: FocusEventHandler<HTMLInputElement>;
  onChange: ChangeEventHandler<HTMLInputElement>;
  value: string | undefined;
}

export const FieldValidator = ({
  name,
  onBlur,
  onChange,
  render,
  setFieldValidity,
  submitAttempted,
  validationOptions,
  value,
}: FieldValidatorProps) => {
  const [isValid, setValid] = useState(true);
  const [isTouched, setTouched] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');

  useEffect(() => {
    if (setFieldValidity) {
      setFieldValidity(name, validationOptions.required ? isValid && isTouched : isValid);
    }
  }, [isValid, isTouched, name, setFieldValidity, validationOptions.required]);

  useEffect(() => {
    if (submitAttempted && !isTouched) {
      // Force field validation when user submits form
      validate(value);
    }
    // omitting validate() from dep array, doesn't need to trigger the effect
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [submitAttempted, isTouched, value]);

  useEffect(() => {
    if (value) {
      // Force field validation if there is a pre-filled value
      validate(value);
    }
    // omitting validate() from dep array, doesn't need to trigger the effect
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value]);

  const validate = (value: string = '') => {
    let valid = true;
    let error = '';

    if (validationOptions.required && hasNoValue(value)) {
      valid = false;
      error = tn('required');
    }

    if (valid && validationOptions.noSpecialChars && hasSpecialCharacters(value)) {
      valid = false;
      error = tn('noSpecialCharacters');
    }

    if (valid && validationOptions.noSpaces && hasSpaces(value)) {
      valid = false;
      error = tn('noSpaces');
    }

    setValid(valid);
    setErrorMessage(error);
    setTouched(true);
  };

  const handleChange: ChangeEventHandler<HTMLInputElement> = (e) => {
    // don't validate until user has entered and left the field once
    // to prevent showing errors while a user is still typing.
    // isTouched will be true after first validation triggered on blur
    if (isTouched) {
      validate(e.target.value);
    }
    if (onChange) {
      onChange(e);
    }
  };

  const handleBlur: FocusEventHandler<HTMLInputElement> = (e) => {
    validate(e.target.value);
    if (onBlur) {
      onBlur(e);
    }
  };

  return render({ isValid, errorMessage, onChange: handleChange, onBlur: handleBlur, value });
};
