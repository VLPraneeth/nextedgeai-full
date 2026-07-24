import { Form, Input, InputNumber, Tooltip } from 'antd';
import cx from 'classnames';
import { cloneDeep, isEqual } from 'lodash';
import { useMemo } from 'react';

import { ReactComponent as TrashIcon } from 'assets/icons/Trash.svg';
import Button from 'components/Button';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { HStack } from 'components/layout';
import { DataCardVizConfig } from 'store/insights-studio/types';
import { tNamespaced, tc } from 'utils/i18nUtil';

import './RangeInput.scss';
import { ColorPickerGrid } from './ColorPickerGrid';
import { findOverlappingRanges } from './SingleValueConfigForm';

interface RangeInputProps {
  rangeIndex: number;
  vizConfig: DataCardVizConfig;
  updateConfig: (newConfig: DataCardVizConfig) => void;
  isNameRequired: boolean;
}

const tn = tNamespaced('InsightsStudio');

export const RangeInput = ({ rangeIndex, vizConfig, updateConfig, isNameRequired = false }: RangeInputProps) => {
  const localVizConfig = useMemo(() => {
    return cloneDeep(vizConfig);
  }, [vizConfig]);

  const range = localVizConfig.ranges?.[rangeIndex];
  const rangeCount = localVizConfig.ranges?.length;

  const maxError =
    range?.minimumValue !== undefined && range?.maximumValue !== undefined
      ? range?.maximumValue <= range?.minimumValue
      : false;

  const overlapErrorMessage = useMemo(() => {
    const overlappingRanges = findOverlappingRanges(vizConfig.ranges);
    if (overlappingRanges === null) {
      return '';
    }
    if (isEqual(overlappingRanges[1], range)) {
      return tn('range_error', { max: overlappingRanges[0].maximumValue });
    }
    return '';
    // range is a local variable and is calculated based on vizConfig.ranges so no need to add to the dependency list
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [vizConfig.ranges]);

  if (!range) {
    return null;
  }

  return (
    <div className={cx('range-input', { first: rangeIndex === 0 })}>
      <HStack className="data-card-config-step__input-group">
        <div className="range-input__container">
          <InputWithLabel
            label={`${tn('range_name')} ${isNameRequired ? '' : tc('optional')}`}
            tooltip={tn('Tooltips.range_name')}
            input={
              <Form.Item>
                <Input
                  value={range.name}
                  onChange={(e) => {
                    range.name = e.target.value;
                    updateConfig(localVizConfig);
                  }}
                />
              </Form.Item>
            }
          />

          <ColorPickerGrid
            onChange={(color) => {
              range.color = color;
              updateConfig(localVizConfig);
            }}
            color={range.color}
            label={tn('range_color')}
          />
          <InputWithLabel
            label={tc('min')}
            tooltip={tn('Tooltips.min_range')}
            input={
              <Form.Item>
                <InputNumber
                  value={range.minimumValue}
                  onChange={(value) => {
                    const min = value || 0;
                    range.minimumValue = min;
                    updateConfig(localVizConfig);
                  }}
                />
              </Form.Item>
            }
          />
          <InputWithLabel
            label={tc('max')}
            tooltip={tn('Tooltips.max_range')}
            input={
              <Form.Item validateStatus={maxError ? 'error' : ''} help={maxError && tn('max_error')}>
                <InputNumber
                  value={range.maximumValue}
                  onChange={(value) => {
                    range.maximumValue = value || 0;
                    updateConfig(localVizConfig);
                  }}
                />
              </Form.Item>
            }
          />
          {overlapErrorMessage && !maxError && <div className="range-input__error-message">{overlapErrorMessage}</div>}
        </div>

        <Tooltip title={tn('data_card_remove_column')}>
          <Button
            type="link"
            onClick={() => {
              localVizConfig.ranges?.splice(rangeIndex, 1);
              updateConfig(localVizConfig);
            }}>
            <TrashIcon className="trash-icon" />
          </Button>
        </Tooltip>
      </HStack>
      <hr className={cx({ last: rangeCount && rangeIndex === rangeCount - 1 })} />
    </div>
  );
};
