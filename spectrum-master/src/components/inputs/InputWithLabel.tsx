//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Form, Icon, Tooltip } from 'antd';
import cx from 'classnames';
import { kebabCase } from 'lodash';
import { forwardRef, useMemo } from 'react';
import { ReactElement } from 'react';

import FieldTypeBadge from 'components/FieldTypeBadge';
import InputContainer from 'components/inputs/InputContainer';
import AppConstants from 'utils/AppConstants';

import './InputWithLabel.less';

// TODO: Refactor to include InputContainerProps
export interface InputWithLabelProps {
  className?: string;
  style?: { [k: string]: any };
  showFieldTypeBadge?: boolean;
  label?: string | ReactElement;
  tooltip?: string;
  input?: ReactElement | ReactElement[];
  validateStatus?: any;
  help?: string;
  helpUrl?: string;
  displayContext?: string;
  id?: string;
  required?: boolean;
  dependantType?: string;
  dependantId?: string;
  [k: string]: any;
}

const InputWithLabel = forwardRef<HTMLElement, InputWithLabelProps>(
  (
    {
      children,
      className,
      style,
      label,
      tooltip,
      tooltipClassName,
      tooltipIcon = 'question-circle',
      helpUrl,
      input,
      validateStatus,
      help,
      displayContext,
      showFieldTypeBadge,
      id,
      required,
      ...rest
    }: InputWithLabelProps,
    ref
  ) => {
    const { COMPOSITE } = AppConstants.INPUT_TYPE;

    const previewMode = rest.displayMode === AppConstants.INPUT_DISPLAY_MODE.READONLY;

    const helpTip = useMemo(() => {
      if (tooltip) {
        return (
          <Tooltip title={tooltip}>
            <div className={cx('synri-tooltip', tooltipClassName)}>
              <Icon type={tooltipIcon} theme="filled" />
            </div>
          </Tooltip>
        );
      } else if (helpUrl) {
        return (
          <div className={cx('synri-tooltip', tooltipClassName)} title={helpUrl}>
            <a href={helpUrl} target="_blank" rel="noreferrer">
              <Icon type={tooltipIcon} theme="filled" />
            </a>
          </div>
        );
      }
    }, [helpUrl, tooltip, tooltipClassName, tooltipIcon]);

    return (
      <div
        className={cx(
          'synri-container',
          previewMode && 'synri-input-preview',
          className,
          rest.datatype ? `synri-${kebabCase(rest.datatype)}` : '',
          {
            'synri-display-contex-composite': displayContext === COMPOSITE,
          }
        )}
        style={style}
        onKeyPress={(e) => {
          if (e.key === 'Enter' && rest.datatype === 'tag') {
            // Prevent enter key submitting form in this input
            e.preventDefault();
          }
        }}>
        {displayContext !== COMPOSITE ? (
          <div className="synri-label-with-help">
            {showFieldTypeBadge && rest.datatype && (
              <FieldTypeBadge dataType={rest.datatype} description={rest.datatype} />
            )}
            {label ? (
              <label className="synri-label" htmlFor={id}>
                {label}
                {required && <span className="synri-required">*</span>}
              </label>
            ) : (
              ''
            )}
            {helpTip}
          </div>
        ) : (
          <>
            {label && (
              <label className="synri-label" htmlFor={id}>
                {label}
                {required && <span className="synri-required">*</span>}:{' '}
              </label>
            )}
          </>
        )}
        {input ? (
          input
        ) : (
          <>
            {displayContext !== COMPOSITE ? (
              <Form.Item validateStatus={validateStatus} help={help}>
                <InputContainer ref={ref} id={id} {...rest} />
              </Form.Item>
            ) : (
              <InputContainer ref={ref} id={id} className={cx(label ? 'synri-with-label' : '')} {...rest} />
            )}
          </>
        )}
      </div>
    );
  }
);

export default InputWithLabel;
