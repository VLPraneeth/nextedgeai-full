import Icon from 'antd/lib/icon';
import { TooltipProps } from 'antd/lib/tooltip';
import cx from 'classnames';
import { forwardRef } from 'react';

import { Token as TokenType } from 'store/tokens/types';

import FieldTypeBadge from './FieldTypeBadge';

import './Token.less';

const TokenTooltipProps: Partial<TooltipProps> = { className: 'synri-token-tooltip', placement: 'right' };

export type TokenProps = JSX.IntrinsicElements['span'] & {
  onRequestRemove?: () => void;
  onRequestEdit?: () => void;
  token: TokenType;
  tokenClassName?: string;
  readOnly?: boolean;
};

const Token = forwardRef<HTMLSpanElement, TokenProps>(
  ({ children, className, token, tokenClassName, onRequestEdit, onRequestRemove, readOnly, ...props }, ref) => {
    return (
      <div className={cx('synri-token-container', className, !readOnly && onRequestRemove && 'can-delete')}>
        <span data-testid="SingleTokenBadge" ref={ref} {...props} onClick={onRequestEdit}>
          <FieldTypeBadge
            className={cx('synri-token-data-type', tokenClassName)}
            dataType={token.datatype}
            description={token.label}
            size="small"
            tooltipProps={TokenTooltipProps}>
            <span contentEditable={false} className="synri-token">
              {token.shortLabel}
            </span>
          </FieldTypeBadge>
          {children}
        </span>
        {!readOnly && onRequestRemove && (
          <span className="synri-token-remove" onClick={onRequestRemove}>
            <Icon type="close" />
          </span>
        )}
      </div>
    );
  }
);

export default Token;
