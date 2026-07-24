import { Form, Icon, Tooltip } from 'antd';
import Modal from 'antd/lib/modal';
import cx from 'classnames';
import { useCallback } from 'react';

import { ReactComponent as TrashIcon } from 'assets/icons/Trash.svg';
import Fieldset from 'components/Fieldset';
import Input from 'components/inputs/Input';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { Divider, Stack } from 'components/layout';
import SelectInput from 'components/SelectInput';
import { VizerDisplayFormat } from 'components/vizer/types';
import { getDisplayFormatOptions } from 'components/vizer/utils/VizerDisplayFormatter';
import { DataCardVizConfig, FieldConfig, VizType } from 'store/insights-studio/types';
import { tc, tNamespaced } from 'utils/i18nUtil';

import { VizConfigColumnOption } from './DataCardConfigStep';

import './AxisConfigInput.scss';

const tn = tNamespaced('InsightsStudio');

export interface AxisConfigInputProps {
  title: string;
  axisConfig: FieldConfig[];
  onChange: (config: FieldConfig[]) => void;
  options: VizConfigColumnOption[];
  disabled?: boolean;
  repeatable?: boolean;
  showDisplayFormat?: boolean;
  vizConfig?: DataCardVizConfig;
  vizType: VizType | undefined;
}

export const AxisConfigInput = ({
  title,
  axisConfig,
  onChange,
  options,
  disabled,
  repeatable = false,
  vizType,
  showDisplayFormat = true,
  vizConfig,
}: AxisConfigInputProps) => {
  const setColumn = (column: string, index: Number) => {
    const selectedOption = options.find((opt) => opt.value === column);

    onChange(
      axisConfig.map((config, configIndex) =>
        configIndex === index
          ? {
              ...config,
              ...{
                column,
                name: column,
                displayFormat: (selectedOption?.dataType === 'integer' || selectedOption?.dataType === 'double'
                  ? 'number'
                  : 'text') as FieldConfig['displayFormat'],
              },
            }
          : config
      )
    );
  };

  const setDisplayFormat = (displayFormat: string, index: Number) => {
    onChange(
      axisConfig.map((config, configIndex) =>
        configIndex === index ? { ...config, displayFormat: displayFormat as VizerDisplayFormat } : config
      )
    );
  };

  const onDelete = (index: Number) => onChange(axisConfig.filter((_, configIndex) => index !== configIndex));

  const setDisplayName = (displayName: string, index: Number) => {
    onChange(axisConfig.map((config, configIndex) => (configIndex === index ? { ...config, displayName } : config)));
  };

  const addBlank = useCallback(() => {
    onChange([
      ...axisConfig,
      {
        column: '',
        displayName: '',
        name: '',
      },
    ]);
  }, [axisConfig, onChange]);

  const addMetric = useCallback(
    (evt: any) => {
      evt.preventDefault();
      if (vizConfig?.series?.length && repeatable && axisConfig.length) {
        Modal.confirm({
          title: tn('data_card_confirm_mutli_metric_series'),
          cancelText: tc('cancel'),
          okText: tc('yes'),
          onOk: addBlank,
        });
      } else {
        addBlank();
      }
    },
    [addBlank, axisConfig?.length, repeatable, vizConfig?.series?.length]
  );

  return (
    <Fieldset
      collapsible
      title={title}
      className={cx('axis-config-input', repeatable ? 'axis-config-input--repeatable' : null)}>
      {axisConfig.map((config, index) => {
        return (
          <>
            <div
              className="data-card-config-step__input-group data-card-config-step__input-group--fieldset axis-config-input__fieldset"
              key={index}>
              <Stack className="axis-config-input__fieldset__fields">
                <InputWithLabel
                  label={tc('column')}
                  tooltip={tn('Tooltips.axis_column', { title: title.toLowerCase() })}
                  input={
                    <Form.Item>
                      <SelectInput
                        allowClear
                        disabled={disabled}
                        value={config.column}
                        options={options}
                        onChange={(column) => setColumn(column, index)}
                      />
                    </Form.Item>
                  }
                />

                {showDisplayFormat && (
                  <InputWithLabel
                    label={tc('display_format')}
                    tooltip={tn('Tooltips.display_format')}
                    input={
                      <Form.Item>
                        <SelectInput
                          allowClear
                          disabled={disabled}
                          value={config.displayFormat}
                          options={getDisplayFormatOptions(vizType)}
                          onChange={(displayFormat) => setDisplayFormat(displayFormat, index)}
                        />
                      </Form.Item>
                    }
                  />
                )}

                <InputWithLabel
                  label={tc('title')}
                  tooltip={tn('Tooltips.axis_title', { title: title.toLowerCase() })}
                  input={
                    <Form.Item>
                      <Input
                        disabled={disabled}
                        value={config.displayName}
                        placeholder={config.column}
                        onChange={(e) => setDisplayName(e.target.value, index)}
                      />
                    </Form.Item>
                  }
                />
              </Stack>
              {repeatable && (
                <div className="axis-config-input__fieldset__delete">
                  <TrashIcon className="trash-icon" onClick={() => onDelete(index)} />
                </div>
              )}
            </div>
            {index < axisConfig.length - 1 && <Divider className="axis-config-input__fieldset__divider" />}
          </>
        );
      })}
      {repeatable && (
        <div className="axis-config-input__add-metric">
          <a onClick={addMetric}>{tn('add_metric')}</a>
          <Tooltip title={tn('add_metric_series_tooltip')}>
            <div className="axis-config-input__add-metric__question synri-tooltip">
              <Icon type="question-circle" theme="filled" />
            </div>
          </Tooltip>
        </div>
      )}
    </Fieldset>
  );
};
