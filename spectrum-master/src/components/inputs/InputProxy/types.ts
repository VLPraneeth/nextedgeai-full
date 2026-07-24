import AppConstants from 'utils/AppConstants';
import { ArrayToUnion } from 'utils/TypeUtils';
const { INPUT_TYPE } = AppConstants;

type NullableIdentifier = null | string | undefined;

export type InputProxyBase<Value extends unknown = string> = {
  id?: string;
  name?: string;
  defaultValue?: Value;
  value?: Value;
};

export type InputProxyOnChangeEvent<
  Value extends unknown = string,
  Event extends unknown = React.ChangeEvent<HTMLInputElement>
> = [Event] extends [never]
  ? // we always accept string because all datatypes can be a token, which will be sent as a string
    (value: Value | string, fieldName: NullableIdentifier, fieldId: NullableIdentifier) => void
  : (value: Value | string, fieldName: NullableIdentifier, fieldId: NullableIdentifier, evt: Event) => void;

export const selectInputType = [
  AppConstants.INPUT_TYPE.MULTISELECT,
  AppConstants.INPUT_TYPE.PICKLIST,
  AppConstants.INPUT_TYPE.REFERENCE,
  AppConstants.INPUT_TYPE.MULTISELECT_FIELD,
] as const;

export type SelectInputType = ArrayToUnion<typeof selectInputType>;

export const textInputType = [
  AppConstants.INPUT_TYPE.URL,
  AppConstants.INPUT_TYPE.INTEGER,
  AppConstants.INPUT_TYPE.DOUBLE,
  AppConstants.INPUT_TYPE.PASSWORD,
] as const;

export const autoCompleteInputType = [
  AppConstants.INPUT_TYPE.PICKLIST_COMBO,
  AppConstants.INPUT_TYPE.AUTOCOMPLETE,
] as const;

export type AutoCompleteInputType = ArrayToUnion<typeof autoCompleteInputType>;

export type TextInputType = ArrayToUnion<typeof textInputType>;

export const tokensTextInputType = [AppConstants.INPUT_TYPE.STRING, AppConstants.INPUT_TYPE.TEXT] as const;
export type TokensTextInputType = ArrayToUnion<typeof tokensTextInputType>;

export const tokensTextAreaInputType = [AppConstants.INPUT_TYPE.TEXTAREA] as const;
export type TokensTextAreaInputType = ArrayToUnion<typeof tokensTextAreaInputType>;

export const singleTokenEligible = [
  INPUT_TYPE.BOOLEAN,
  INPUT_TYPE.CHECKBOX,
  ...selectInputType,
  ...textInputType,
] as const;
export type SingleTokenInputType = ArrayToUnion<typeof singleTokenEligible>;
