//
// Copyright (c) 2019-Present Syncari All rights reserved.
//

import { Icon, Tooltip } from 'antd';
import { CheckboxChangeEvent } from 'antd/lib/checkbox';
import cx from 'classnames';
import { ChangeEvent, useEffect, useMemo, useState } from 'react';
import { Controlled as CodeMirror } from 'react-codemirror2';

import Checkbox from 'components/Checkbox';
import { useI18nContext, withI18n } from 'components/I18nProvider';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { Stack } from 'components/layout';
import AppConstants from 'utils/AppConstants';
import 'codemirror/lib/codemirror.css';
import 'codemirror/mode/twig/twig';
import './ActionBody.less';
import { integerRegEx } from 'utils/RegexUtil';

export interface ActionBodyValue {
  batchSize?: string;
  bodyValue?: string;
  isBatch?: string;
}

export interface ActionBodyProps {
  className?: string;
  defaultValue?: ActionBodyValue;
  displayMode?: string;
  showBatchingInput?: boolean;
  onChange?: (value: ActionBodyValue) => void;
}

export const ActionBody = withI18n(
  ({ className, onChange, displayMode, defaultValue, showBatchingInput = false }: ActionBodyProps) => {
    const readOnly = useMemo(() => displayMode === AppConstants.INPUT_DISPLAY_MODE.READONLY, [displayMode]);
    const [bodyValue, setBodyValue] = useState(defaultValue?.bodyValue || '');
    const [isBatch, setIsBatch] = useState(defaultValue?.isBatch || '');
    const [batchSize, setBatchSize] = useState(defaultValue?.batchSize || '');
    const { tn } = useI18nContext();

    const options = useMemo(
      () => ({
        matchBrackets: true,
        lineWrapping: true,
        autoCloseBrackets: true,
        mode: 'twig',
        readOnly,
        lineNumbers: true,
      }),
      [readOnly]
    );

    useEffect(() => {
      if (!isBatch) {
        setBatchSize('');
      }
    }, [isBatch]);

    useEffect(() => {
      onChange?.({ bodyValue, isBatch, batchSize });
    }, [batchSize, bodyValue, isBatch, onChange]);

    return (
      <Stack className={cx('action-body', className)} spacing="md">
        {showBatchingInput && (
          <div className="action-body__options">
            <Checkbox
              className="action-body__checkbox"
              checked={`${isBatch}` === AppConstants.TRUE ? true : false}
              onChange={(event: CheckboxChangeEvent) => {
                setIsBatch(event.target.checked ? AppConstants.TRUE : AppConstants.FALSE);
              }}>
              {tn('enable_batching')}
            </Checkbox>
            <Tooltip title={tn('batch_enable_tooltip')}>
              <Icon theme="filled" type="question-circle" />
            </Tooltip>
            {isBatch === AppConstants.TRUE && (
              <div className="action-body__batch-size">
                <label htmlFor="batchSize">{tn('batch_size')}</label>
                <InputWithLabel
                  id="batchSize"
                  name="batchSize"
                  value={batchSize}
                  datatype={AppConstants.INPUT_TYPE.INTEGER}
                  onChange={(event: ChangeEvent<HTMLInputElement>) => {
                    const value = event.target.value;
                    const isInteger = integerRegEx.test(value);

                    if (isInteger) {
                      setBatchSize(event.target.value);
                    }
                  }}
                />
              </div>
            )}
          </div>
        )}
        <CodeMirror value={bodyValue} options={options} onBeforeChange={(editor, data, value) => setBodyValue(value)} />
      </Stack>
    );
  },
  'ActionSetup'
);

export default ActionBody;
