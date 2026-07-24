import cx from 'classnames';
import React from 'react';
import ReactDOM from 'react-dom';

import { Token } from 'store/tokens/types';

import { FieldItem } from '../FieldOptions';

import './TokenDropdownList.scss';

export interface TokenDropdownListProps {
  tokens: Token[];
  selectedTokenIndex?: Number;
}

export const TokenDropdownList = React.forwardRef<HTMLDivElement | null, TokenDropdownListProps>(
  ({ tokens, selectedTokenIndex }, ref) => {
    return tokens?.length ? (
      <Portal>
        <div ref={ref} className="token-dropdown-list">
          {tokens?.map((token, i) => (
            <div
              key={i}
              className={cx(
                'token-dropdown-list__token',
                i === selectedTokenIndex && 'token-dropdown-list__token--selected'
              )}>
              <FieldItem dataType={token.datatype} displayName={token.shortLabel} apiName={token.shortLabel} />
            </div>
          ))}
        </div>
      </Portal>
    ) : null;
  }
);

interface PortalProps {
  children: React.ReactNode;
  container?: HTMLElement;
}

const Portal = ({ children, container = document.body }: PortalProps) => {
  return ReactDOM.createPortal(children, container);
};
