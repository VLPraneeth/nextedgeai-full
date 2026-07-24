//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import cx from 'classnames';
import { cloneDeep, delay, find } from 'lodash';

import InputWithLabel from 'components/inputs/InputWithLabel';
import AppConstants from 'utils/AppConstants';
import { getDefaultValue } from 'utils/InputUtil';
import { findConfigurationByGraphKey } from 'utils/NodeConfigUtil';

import { RENDER_TYPE_WITHOUT_DEFAULT_VALUE } from './CompositeGroup';

import './CompositeGroupReadOnly.less';

interface CompositeGroupReadOnlyProps {
  configuration: any[];
  order?: number;
  layout?: string;
  disabled?: boolean;
  fetchPicklistValues?: any;
  picklistValues?: any;
  value: {
    [key: string]: any;
  };
}

const CompositeGroupReadOnly = ({
  configuration,
  layout,
  order,
  fetchPicklistValues,
  picklistValues,
  value,
  disabled,
}: CompositeGroupReadOnlyProps) => {
  const verifyTemplateLiteralInputs = (lRestConfig: any, fieldKey: string) => {
    const val = value?.[fieldKey]?.value;
    if (typeof val !== 'string' || !val.startsWith('{{') || !val.endsWith('}}')) return;

    lRestConfig.values ??= [];
    if (lRestConfig.values.some((v: any) => v.value === val)) return (lRestConfig.value = val);

    lRestConfig.values.push({
      datatype: 'text',
      label: val,
      picklistGroup: 'Temporary Variables',
      type: 'variable',
      value: val,
    });
  };

  const getInputConfig = (inputConfig: any) => {
    let lRestConfig = cloneDeep(inputConfig);
    if (lRestConfig.dependsOn) {
      const graphConfig = findConfigurationByGraphKey(lRestConfig.dependsOn.dependantField, { configuration });
      if (graphConfig) {
        const graphValue = find(value, (val: any) => {
          return val.name === graphConfig.name;
        });
        if (graphValue && graphValue.value) {
          const id = `${graphValue.value}/${lRestConfig.name}${lRestConfig.dependsOn.dependantType}`;
          if (picklistValues?.[id]) {
            lRestConfig.values = picklistValues[id];
          } else if (fetchPicklistValues) {
            // TODO: Move this at a higher level so the readonly and edit mode resolve the values at the same layer
            // as oppose to different locations
            delay(() => fetchPicklistValues({ ...lRestConfig.dependsOn, dependantId: graphValue.value, id }), 0);
          }
        }
      }
    } else if (RENDER_TYPE_WITHOUT_DEFAULT_VALUE.includes(inputConfig.renderType)) {
      // Force the default value to value for inputs that does not support default value
      lRestConfig.value = getDefaultValue(value[lRestConfig.name]);
    }
    verifyTemplateLiteralInputs(lRestConfig, lRestConfig.name);
    return lRestConfig;
  };

  return (
    <div
      className={cx('synri-composite-group-readonly', {
        'synri-composite-row-readonly': layout === 'row',
        'synri-composite-column-readonly': layout === 'column',
      })}>
      <div className="synri-composite-order">{order}</div>
      <div className="synri-composite-group-values">
        {/* configuration should not have default value, ignoring it here */}
        {configuration.map(({ className, defaultValue: ignored, ...restConfig }) => {
          let width = `${layout === 'row' ? 100 / configuration.length : 100}%`;
          const lRestConfig = getInputConfig(restConfig);
          const { name } = lRestConfig;
          return (
            <InputWithLabel
              key={`composite-group-input-${name}`}
              className={cx('synri-composite-input', className)}
              style={{ width }}
              value={getDefaultValue(value[name])}
              disabled={disabled}
              displayMode={AppConstants.INPUT_DISPLAY_MODE.READONLY}
              displayContext={AppConstants.INPUT_TYPE.COMPOSITE}
              tooltip={lRestConfig.helpSummary}
              fetchPicklistValues={fetchPicklistValues}
              picklistValues={picklistValues}
              {...lRestConfig}
            />
          );
        })}
      </div>
    </div>
  );
};

export default CompositeGroupReadOnly;
