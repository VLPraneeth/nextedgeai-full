import { Children, cloneElement, isValidElement, useCallback, useMemo, useRef, useState } from 'react';

import { useI18nContext, withI18n } from 'components/I18nProvider';
import NodeTokenSelector from 'components/NodeTokenSelector';
import Token from 'components/Token';
import { useTokensForSelectedNode } from 'store/tokens/hooks';
import AppConstants from 'utils/AppConstants';

import { InsertTokenHandler, TokenTextAreaRef } from '../inputs/tokens/TokenTextArea';
import FieldGroup, { FieldGroupProps } from './FieldGroup';
import { isSingleTokenEligible } from './InputProxy/utils';
import { isValidSingleTokenValue } from './tokens/utils';

import './TokenizableFieldGroup.less';

export type PartialTokenTextAreaRef = TokenTextAreaRef;
export type InsertTokenCompatibleRef = Pick<TokenTextAreaRef, 'insertToken'>;
export type TokenizableFieldRef = TokenTextAreaRef | InsertTokenCompatibleRef;

const editableSingleTokenFields = [AppConstants.INPUT_TYPE.INTEGER, AppConstants.INPUT_TYPE.DOUBLE];

export type TokenizableFieldGroupProps = {
  disableTokens?: boolean;
  /* used to catch token selections for fields that don't have an exposed insertToken method */
  fallbackOnTokenSelect?: InsertTokenHandler;
  hideTokenPicker?: boolean;
} & FieldGroupProps;

const TokenizableFieldGroup = ({
  children,
  disableTokens = false,
  labelSiblings,
  fallbackOnTokenSelect,
  helpText,
  hideTokenPicker,
  ...props
}: TokenizableFieldGroupProps) => {
  const { tn } = useI18nContext();
  const { getToken } = useTokensForSelectedNode();
  const field = useRef<TokenizableFieldRef | undefined>(undefined);

  const [forceShowEditableToken, setForceShowEditableToken] = useState(false);

  const child = Children.only(children);
  const { datatype, value } = (child.props as unknown) as any;

  const onTokenSelect: InsertTokenHandler = (token) => {
    if (field.current?.insertToken && !isSingleTokenEligible(datatype)) {
      field.current.insertToken(token);
    } else {
      fallbackOnTokenSelect?.(token);
    }
  };

  const registerField = useCallback((node: any) => {
    if (node) {
      field.current = node;
    }
  }, []);

  const labelSiblingsNode =
    disableTokens || hideTokenPicker ? (
      labelSiblings
    ) : (
      <>
        <NodeTokenSelector onTokenSelect={onTokenSelect} />
        {labelSiblings}
      </>
    );
  const isTokenValue = useMemo(() => isValidSingleTokenValue(value) && isSingleTokenEligible(datatype), [
    datatype,
    value,
  ]);

  const getInputNode = () => {
    if (!(child && isValidElement(child)) || typeof child === 'string' || disableTokens) {
      return child;
    }

    const { id, name, onChange, value } = (child.props as unknown) as any;

    if (isTokenValue && !forceShowEditableToken) {
      const token = getToken(value);
      const handleRemove = () => onChange?.('', name, id);

      return (
        <div className="synri-field-group-token-wrapper">
          <Token
            onRequestEdit={() => {
              if (editableSingleTokenFields.includes(datatype)) {
                setForceShowEditableToken(true);
              }
            }}
            onRequestRemove={handleRemove}
            token={token}
          />
        </div>
      );
    }

    return cloneElement(child, {
      ref: registerField,
    } as any);
  };

  const helpTextOrTokenInfo = !disableTokens && isTokenValue ? tn('this_field_is_using_data_token') : helpText;

  return (
    <FieldGroup helpText={helpTextOrTokenInfo} labelSiblings={labelSiblingsNode} {...props}>
      {getInputNode()}
    </FieldGroup>
  );
};

export default withI18n(TokenizableFieldGroup, 'TokenizableFieldGroup');
