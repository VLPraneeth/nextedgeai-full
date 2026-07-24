import { Tooltip } from 'antd';
import Icon from 'antd/lib/icon';
import Popover from 'antd/lib/popover';
import Spin from 'antd/lib/spin';
import { TooltipAlignConfig } from 'antd/lib/tooltip';
import cx from 'classnames';
import { useCombobox } from 'downshift';
import { matchSorter } from 'match-sorter';
import { useCallback, useEffect, useRef, useState } from 'react';

import { useI18nContext, withI18n } from 'components/I18nProvider';
import { HStack } from 'components/layout';
import Spinner from 'components/Spinner';
import TokenItem from 'components/Token';
import { Token, TokenError } from 'store/tokens/types';
import { tc } from 'utils/i18nUtil';

import Input, { InputRef } from '../Input';

import './TokenSelector.less';
const tokenToString = (token: Token | null) => (token ? token.shortLabel : '');

export interface TokensError {
  status: number;
  data: Record<string, any>;
}

type TokenSelectorMenuProps = {
  onRequestClose: () => void;
  onTokenSelect: (token: string) => void;
  tokens: Record<string, Token[]>;
};

const TokenSelectorMenu = ({ tokens, onRequestClose, onTokenSelect }: TokenSelectorMenuProps) => {
  const { tn } = useI18nContext();
  const inputRef = useRef<InputRef | null>(null);
  const [inputValue, setInputValue] = useState('');

  const isTokenListEmpty = Object.keys(tokens).length === 0;

  const dataSource: Record<string, Token[]> = Object.fromEntries(
    Object.entries(tokens).map(([groupName, tokens = []]) => [
      groupName,
      matchSorter(tokens, inputValue, { keys: ['shortLabel', 'label'] }),
    ])
  );

  const categories = Object.keys(dataSource);
  const emptyCategories = Object.entries(dataSource)
    .filter(([_, tokens]) => tokens.length < 1)
    .map(([key]) => key);

  const [currentCategory, setCurrentCategory] = useState(() => categories[0]);

  useEffect(() => {
    if (!isTokenListEmpty) {
      // if we don't have multiple categories, or all categories are empty, skip
      if (categories.length < 2 || emptyCategories.length === categories.length) {
        return;
      }

      if (emptyCategories.includes(currentCategory)) {
        const categoryCandidates = categories.filter((c) => !emptyCategories.includes(c));

        if (categoryCandidates.length) {
          setCurrentCategory(categoryCandidates[0]);
        }
      }
    }
  }, [categories, currentCategory, emptyCategories, isTokenListEmpty]);

  const filteredTokens = dataSource[currentCategory] || [];

  const handleTokenSelect = useCallback(
    (token: Token) => {
      setInputValue('');
      onRequestClose();
      onTokenSelect(token.token);
    },
    [onRequestClose, onTokenSelect]
  );

  const { getComboboxProps, getMenuProps, getInputProps, getItemProps, highlightedIndex, selectedItem } = useCombobox({
    items: filteredTokens,
    inputValue,
    onInputValueChange: ({ inputValue }) => setInputValue(inputValue ?? ''),
    itemToString: tokenToString,
    onSelectedItemChange: ({ selectedItem }) => {
      selectedItem && handleTokenSelect(selectedItem);
    },
  });

  if (isTokenListEmpty) {
    return <div className="empty-token-popover">{tn('no_tokens_available')}</div>;
  }

  return (
    <div className={cx('synri-token-selector-popover', categories.length > 1 && 'show-categories')}>
      <div className="synri-token-selector-search-input-container" {...getComboboxProps()}>
        <Input
          autoFocus
          allowClear
          onClear={() => {
            setInputValue('');
            inputRef.current?.focus();
          }}
          {...getInputProps({
            placeholder: tn('search_placeholder'),
            // @ts-expect-error: error because of the antd input
            ref: inputRef,
          })}
        />
      </div>
      {categories.length > 1 && (
        <ul className="synri-token-selector-categories">
          {categories.map((category, idx) => {
            const isActive = category === currentCategory;
            const isEmpty = emptyCategories.includes(category);

            return (
              <li
                key={`${category}-${idx}`}
                className={cx('synri-token-category-item', {
                  active: isActive,
                  disabled: !isActive && isEmpty,
                })}
                role="button"
                onClick={() => {
                  if (isEmpty) {
                    return;
                  }
                  setCurrentCategory(category);
                }}>
                {category}
              </li>
            );
          })}
        </ul>
      )}
      <ul className="synri-token-selector-tokens-list" {...getMenuProps()}>
        {filteredTokens.map((token, idx) => (
          <li
            key={token.value}
            className="synri-token-selector-item"
            role="button"
            {...getItemProps({ item: token, index: idx })}>
            <TokenItem
              token={token}
              className={cx({
                highlighted: highlightedIndex === idx,
                'synri-token-selector-highlighted': highlightedIndex === idx,
                active: token.value === selectedItem?.value,
              })}
            />
          </li>
        ))}
      </ul>
    </div>
  );
};

const selectorPopoverAlignment: TooltipAlignConfig = { offset: [-15, -15] };

export interface TokenSelectorProps {
  onTokenSelect: TokenSelectorMenuProps['onTokenSelect'];
  tokens: TokenSelectorMenuProps['tokens'];
  tokensError?: TokenError;
  tokensLoading?: boolean;
  label?: string;
}

const TokenSelector = ({ onTokenSelect, tokens, tokensLoading = false, tokensError, label }: TokenSelectorProps) => {
  const { tn } = useI18nContext();
  const [showing, setShowing] = useState(false);

  const toggleShowing = () => setShowing((prev) => !prev);
  const close = () => {
    setShowing(false);
  };

  return (
    <Popover
      trigger="click"
      placement="topRight"
      align={selectorPopoverAlignment}
      visible={showing}
      onVisibleChange={setShowing}
      overlayClassName="token-selector-popover-overlay"
      content={
        showing &&
        (tokensLoading ? (
          <Spin spinning />
        ) : (
          <TokenSelectorMenu tokens={tokens} onTokenSelect={onTokenSelect} onRequestClose={close} />
        ))
      }>
      {tokensError ? (
        <div className="token-selector-info-container token-selector-info-container--unselectable">
          <HStack align="center" spacing="xxs">
            <Tooltip title={tokensError.message ?? tn('error')}>
              <span>{tokensError.description ?? tn('error')}</span>
            </Tooltip>
          </HStack>
        </div>
      ) : tokensLoading ? (
        <div className="token-selector-info-container token-selector-info-container--unselectable token-selector-trigger">
          <HStack align="center" spacing="xxs">
            <Spinner />
            <span>{tc('loading')}</span>
          </HStack>
        </div>
      ) : (
        <div
          role="button"
          aria-expanded={showing}
          onClick={toggleShowing}
          className={cx('token-selector-info-container token-selector-trigger', { active: showing })}>
          <HStack align="baseline" spacing="xxs">
            <Icon type="plus" />
            <span>{label || tn('insert_token_trigger_label')}</span>
          </HStack>
        </div>
      )}
    </Popover>
  );
};

export default withI18n(TokenSelector, 'Tokens');
