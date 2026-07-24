//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { Button, Icon, Tooltip } from 'antd';
import cx from 'classnames';
import { useCallback, useMemo } from 'react';

import { useI18nContext, withI18n } from 'components/I18nProvider';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { HStack } from 'components/layout';
import SelectInput, { Option } from 'components/SelectInput';
import { DatasetVariable } from 'store/insights-studio/types';
import { tc } from 'utils/i18nUtil';

import './MultiVariableMapping.scss';
import { makeVariablesCount } from './DashboardVariableSettingsModal';

export interface MultiVariableMappingProps {
  name: string;
  className?: string;
  label?: string;
  onChange?: (values: VariableMapping[]) => void;
  value?: VariableMapping[];
  vals?: string[];
  disabled?: boolean;
  dashboardVariable?: Record<string, DatasetVariable>;
}

export interface VariableMapping {
  apiName?: string;
  mappedApiNames?: string[];
}

export type VariableMappings = Record<string, VariableMapping>;

export type MapToValuesOption = Option<string[]>;

const MultiVariableMapping = withI18n(
  ({ onChange, className, disabled, dashboardVariable, value: mappings }: MultiVariableMappingProps) => {
    const onAdd = () => {
      if (mappings) {
        onChange?.([...mappings, {}]);
      } else {
        onChange?.([{}]);
      }
    };

    const { tn } = useI18nContext();
    const onDelete = (id: string) => {
      mappings && onChange?.(mappings?.filter((mapping, idx) => idx !== parseInt(id)));
    };

    const onPicklistChange = (id: string, apiName: string) => {
      if (mappings) {
        const key = parseInt(id);
        const newValues = [...mappings];
        newValues[key] = {
          apiName,
          mappedApiNames: newValues[key]?.mappedApiNames,
        };
        onChange?.(newValues);
      }
    };

    const onMapToChange = (id: string, apiNames: string[]) => {
      if (mappings) {
        const key = parseInt(id);
        const newValues = [...mappings];
        newValues[key] = {
          apiName: newValues[key]?.apiName,
          mappedApiNames: apiNames || [],
        };
        onChange?.(newValues);
      }
    };

    const getLeftOptions = useCallback(
      (key: string) => {
        return dashboardVariable
          ? Object.values(dashboardVariable).map((variable) => {
              return {
                label: variable.displayName,
                value: variable.apiName || '',
              };
            })
          : [];
      },
      [dashboardVariable]
    );

    const getRightOptions = useCallback(
      (key: string) => {
        if (mappings) {
          const hiddenApiNames = Object.values(mappings).map((val) => val.apiName);
          return dashboardVariable
            ? Object.values(dashboardVariable)
                .filter((variable) => !hiddenApiNames.includes(variable.apiName))
                .map((variable) => {
                  return {
                    label: variable.displayName,
                    value: variable.apiName || '',
                  };
                })
            : [];
        }
        return [];
      },
      [dashboardVariable, mappings]
    );

    const add = useMemo(() => {
      const countSelected = mappings ? Object.keys(makeVariablesCount(mappings)).length : 0;
      return {
        disabled: countSelected >= Object.keys(dashboardVariable || {}).length,
        disabledMessage: tn('no_more_variables'),
      };
    }, [dashboardVariable, mappings, tn]);

    return (
      <div className={cx('multi-variable-mapping', className)}>
        <InputWithLabel
          label={tn('variable_mapping')}
          input={
            mappings &&
            Object.keys(mappings).map((key) => {
              const index = parseInt(key);
              return (
                <HStack key={`multivalue-${key}`} className="multi-variable-mapping__map_variable" grow align="start">
                  <SelectInput
                    disabled={disabled}
                    showSearch
                    filterOption={(input, option) =>
                      Boolean(option.props.children?.toString().toLowerCase().includes(input))
                    }
                    options={getLeftOptions(key)}
                    value={mappings[index]?.apiName}
                    onSelect={(apiName) => {
                      onPicklistChange(key, apiName);
                    }}
                  />
                  <SelectInput
                    className="multi-variable-mapping__map-to"
                    disabled={disabled}
                    options={getRightOptions(key)}
                    mode="multiple"
                    // @ts-ignore
                    value={mappings[index]?.mappedApiNames || []}
                    onChange={(values) => {
                      // @ts-ignore
                      onMapToChange(key, values);
                    }}
                  />
                  {!disabled && (
                    <div className="synri-delete-container">
                      <Icon type="delete" theme="filled" onClick={() => onDelete(key)} />
                    </div>
                  )}
                </HStack>
              );
            })
          }
        />
        {
          <Tooltip title={add.disabledMessage}>
            <Button type="link" onClick={onAdd} disabled={add.disabled}>
              {tc('plus_add')}
            </Button>
          </Tooltip>
        }
      </div>
    );
  },
  'InsightsStudio.Settings'
);

export default MultiVariableMapping;
