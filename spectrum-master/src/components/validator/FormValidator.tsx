import {
  Children,
  cloneElement,
  CSSProperties,
  FormEvent,
  FormEventHandler,
  isValidElement,
  ReactNode,
  useEffect,
  useState,
} from 'react';

import { FieldValidator, FieldValidatorProps } from './FieldValidator';

export type FormValidatorEventHandler = (e?: FormEvent<Element>) => void;

export interface FormValidatorProps {
  children: ReactNode;
  className?: string;
  id: string;
  onSubmit: FormValidatorEventHandler;
  style?: CSSProperties;
}

export const FormValidator = ({ children, id, onSubmit, className, style }: FormValidatorProps) => {
  const [fieldValidStatus, setFieldValidStatus] = useState<Record<string, boolean>>({});
  const [submitAttempted, setSubmitAttempted] = useState(false);

  const setFieldValidity = (name: string, isValid: boolean) => {
    if (fieldValidStatus[name] !== isValid) {
      setFieldValidStatus({ ...fieldValidStatus, [name]: isValid });
    }
    if (submitAttempted) {
      // reset submission status when user changes a value
      setSubmitAttempted(false);
    }
  };

  const handleSubmit: FormEventHandler = (e) => {
    e.preventDefault();
    if (Object.values(fieldValidStatus).includes(false)) {
      setSubmitAttempted(true);
    } else {
      onSubmit(e);
    }
  };

  useEffect(() => {
    // When submitAttempted is true and all fields are valid, automatically trigger the submit
    // so user does not have to click twice to submit a pre-filled form
    if (submitAttempted && Object.values(fieldValidStatus).every((value) => !!value)) {
      setSubmitAttempted(false);
      onSubmit();
    }
  }, [submitAttempted, fieldValidStatus, onSubmit]);

  return (
    <form id={id} onSubmit={handleSubmit} className={className} style={style}>
      {Children.map(children, (child) => {
        if (isValidElement(child)) {
          if (child.type === FieldValidator) {
            return cloneElement(child as React.ReactElement<FieldValidatorProps>, {
              setFieldValidity,
              submitAttempted,
            });
          } else {
            return child;
          }
        }
      })}
    </form>
  );
};
