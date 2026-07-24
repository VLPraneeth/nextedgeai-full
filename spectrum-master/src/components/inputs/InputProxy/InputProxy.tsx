import { forwardRef } from 'react';

import { CONFIG_MULTI_VALUES } from 'pages/sync-studio/node-config/constants';
import { Token } from 'store/tokens/types';
import AppConstants from 'utils/AppConstants';

import { TokenizableFieldRef } from '../TokenizableFieldGroup';
import { InputDataType } from '../types';
import AntInputProxy, { AntInputProxyProps } from './AntInputProxy';
import AutoCompleteProxy, { AutoCompleteProxyProps } from './AutoCompleteProxy';
import CheckboxProxy, { CheckboxProxyProps } from './CheckboxProxy';
import PasswordInputProxy, { PasswordInputProxyProps } from './PasswordInputProxy';
import ScheduleInputProxy, { ScheduleInputProxyProps } from './ScheduleInputProxy';
import SelectProxy, { SelectProxyProps } from './SelectProxy';
import SwitchProxy, { SwitchProxyProps } from './SwitchProxy';
import TextInputProxy, { TextInputProxyProps } from './TextInputProxy';
import TokensTextAreaProxy, { TokensTextAreaProxyProps } from './TokensTextAreaProxy';
import {
  AutoCompleteInputType,
  SelectInputType,
  TextInputType,
  TokensTextAreaInputType,
  TokensTextInputType,
  selectInputType,
  autoCompleteInputType,
  textInputType,
  tokensTextAreaInputType,
  tokensTextInputType,
} from './types';

const { INPUT_TYPE } = AppConstants;

export const SUPPORTED_DATATYPES = [
  AppConstants.INPUT_TYPE.BOOLEAN,
  AppConstants.INPUT_TYPE.CHECKBOX,
  AppConstants.INPUT_TYPE.DATE,
  AppConstants.INPUT_TYPE.DATETIME,
  ...textInputType,
  ...selectInputType,
  ...autoCompleteInputType,
  ...tokensTextAreaInputType,
  ...tokensTextInputType,
] as const;

export const isSupportedDatatype = (variableToCheck: any): variableToCheck is InputProxyProps['datatype'] =>
  SUPPORTED_DATATYPES.includes(variableToCheck);

export const isMultivalueDatatype = (variableToCheck: any): variableToCheck is InputProxyProps['datatype'] =>
  CONFIG_MULTI_VALUES.includes(variableToCheck);

type ProxyComponentProps<
  Datatype extends InputDataType,
  Props extends unknown = {},
  RenderType extends unknown = undefined
> = {
  datatype: Datatype;
  renderType?: RenderType;
  token?: Token;
} & Props;

type DefaultInputType = typeof AppConstants.INPUT_TYPE.DATE | typeof AppConstants.INPUT_TYPE.DATETIME;

export type InputProxyProps =
  | ProxyComponentProps<DefaultInputType, AntInputProxyProps>
  | ProxyComponentProps<SelectInputType, SelectProxyProps>
  | ProxyComponentProps<AutoCompleteInputType, AutoCompleteProxyProps>
  | ProxyComponentProps<TextInputType, ScheduleInputProxyProps, typeof AppConstants.INPUT_RENDER_TYPE.SCHEDULE>
  | ProxyComponentProps<TextInputType, TextInputProxyProps>
  | ProxyComponentProps<TokensTextAreaInputType, TokensTextAreaProxyProps>
  | ProxyComponentProps<TokensTextInputType, TokensTextAreaProxyProps>
  | ProxyComponentProps<typeof AppConstants.INPUT_TYPE.BOOLEAN, SwitchProxyProps>
  | ProxyComponentProps<typeof AppConstants.INPUT_TYPE.CHECKBOX, CheckboxProxyProps>
  | ProxyComponentProps<typeof AppConstants.INPUT_TYPE.PASSWORD, PasswordInputProxyProps>;

const InputProxy = forwardRef<TokenizableFieldRef, InputProxyProps>((props, ref) => {
  if (!isSupportedDatatype(props.datatype)) {
    throw new Error(`Unsupported Datatype: ${props.datatype}`);
  }

  // TODO: maybe schedule should be a datatype?
  if (props.renderType === AppConstants.INPUT_RENDER_TYPE.SCHEDULE) {
    const { datatype, token, renderType, ...rest } = props;
    return <ScheduleInputProxy {...rest} />;
  }

  switch (props.datatype) {
    case INPUT_TYPE.BOOLEAN: {
      const { datatype, ...rest } = props;
      return <SwitchProxy {...rest} />;
    }
    case INPUT_TYPE.CHECKBOX: {
      const { datatype, ...rest } = props;
      return <CheckboxProxy {...rest} />;
    }
    case INPUT_TYPE.MULTISELECT:
    case INPUT_TYPE.PICKLIST:
    case INPUT_TYPE.MULTISELECT_FIELD:
    case INPUT_TYPE.REFERENCE: {
      const { datatype, renderType, ...rest } = props;
      if (isMultivalueDatatype(datatype)) {
        rest.mode = 'multiple';
      }
      return <SelectProxy {...rest} />;
    }
    case AppConstants.INPUT_TYPE.AUTOCOMPLETE:
    case AppConstants.INPUT_TYPE.PICKLIST_COMBO: {
      const { datatype, ...rest } = props;
      return <AutoCompleteProxy {...rest} />;
    }
    case INPUT_TYPE.PASSWORD: {
      const { datatype, ...rest } = props;
      return <PasswordInputProxy {...rest} />;
    }
    case INPUT_TYPE.TEXTAREA: {
      const { datatype, ...rest } = props;
      return <TokensTextAreaProxy ref={ref as any} {...rest} />;
    }
    case INPUT_TYPE.STRING:
    case INPUT_TYPE.TEXT: {
      const { datatype, ...rest } = props;
      return <TokensTextAreaProxy ref={ref as any} rows={1} {...rest} />;
    }
    case INPUT_TYPE.DOUBLE:
    case INPUT_TYPE.INTEGER:
    case INPUT_TYPE.URL: {
      const { datatype, ...rest } = props;
      return <TextInputProxy {...(rest as TextInputProxyProps)} />;
    }
    case INPUT_TYPE.DATE:
    case INPUT_TYPE.DATETIME: {
      const { datatype, ...rest } = props as any;

      if (props.renderType === AppConstants.INPUT_RENDER_TYPE.TOKENS) {
        const rows = datatype === INPUT_TYPE.TEXTAREA ? 4 : 1;
        return <TokensTextAreaProxy ref={ref as any} rows={rows} {...rest} />;
      } else {
        return <AntInputProxy {...rest} />;
      }
    }
  }
});

export default InputProxy;
