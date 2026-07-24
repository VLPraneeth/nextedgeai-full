import { isNotNullOrUndefined } from 'utils/TypeUtils';

export const hasNoValue = (value?: string | boolean | null): boolean => {
  return typeof value === 'string' ? !Boolean(value?.trim()) : !isNotNullOrUndefined(value);
};

const specialCharsRegExp = new RegExp(/[!@#$%^&*(),.?":{}|<>-]/);

export const hasSpecialCharacters = (value?: string | null) => {
  if (!value) {
    return false;
  }
  return specialCharsRegExp.test(value);
};

export const hasSpaces = (value?: string | null) => {
  if (!value) {
    return false;
  }
  return value.includes(' ');
};
