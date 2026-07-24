import { difference, uniq } from 'lodash';
import { useCallback, useEffect, useMemo, useState } from 'react';

import { fetchPicklistValues } from 'actions/picklistActions';
import { useI18nContext, withI18n } from 'components/I18nProvider';
import InputContainer from 'components/inputs/InputContainer';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { OperatorValue, LeftValue } from 'components/inputs/types';
import { HStack, Stack } from 'components/layout';
import { SkullItem } from 'components/skull';
import { useEnhancedDispatch as useDispatch } from 'hooks/redux';
import useMountUnmountEffect from 'hooks/useMountUnmountEffect';
import { usePicklistValues } from 'store/picklists/hooks';
import AppConstants from 'utils/AppConstants';
import { makePicklistKey } from 'utils/NodeConfigUtil';

import { useFieldMergePolicyContext } from './FieldMergePolicyContext';

import './FieldMergePolicyRetainField.scss';

const { READONLY } = AppConstants.INPUT_DISPLAY_MODE;

type FieldMergePolicyValue = Record<string, string | string[]>;

export interface FieldMergePolicyRetainFieldProps {
  operatorValue?: OperatorValue;
  leftValue?: LeftValue;
  displayMode?: string;
  onChange?: (value: FieldMergePolicyValue) => void;
  defaultValue: FieldMergePolicyValue;
  value: FieldMergePolicyValue;
}

export const FieldMergePolicyRetainField = withI18n(
  ({ leftValue, operatorValue, onChange, defaultValue, displayMode, value }: FieldMergePolicyRetainFieldProps) => {
    const [policyValue, setPolicyValue] = useState(defaultValue);
    const dispatch = useDispatch();
    const [picklistValues] = usePicklistValues();
    const { retainFields, setRetainFields } = useFieldMergePolicyContext();
    const { tn } = useI18nContext();

    const retainFieldConfig: SkullItem | undefined = useMemo(
      () =>
        operatorValue?.configuration?.find(
          (config) => config.renderType === AppConstants.INPUT_RENDER_TYPE.FIELD_MERGE_POLICY_RETAIN_FIELD
        ),
      [operatorValue]
    );

    useMountUnmountEffect(() => {
      if (
        retainFieldConfig?.name &&
        defaultValue?.[retainFieldConfig.name] &&
        typeof defaultValue[retainFieldConfig.name] !== 'string' &&
        defaultValue[retainFieldConfig.name]?.length
      ) {
        setRetainFields((current) => {
          return [...current, ...uniq([...retainFields, ...defaultValue[retainFieldConfig.name]])];
        });
      }
      if (retainFieldConfig?.defaultValue && !defaultValue) {
        onChange?.({
          [retainFieldConfig.name]: retainFieldConfig?.defaultValue,
        });
      }
    });

    const onRetainFieldChange = useCallback(
      (name: string, value: string | string[]) => {
        const val = {
          ...policyValue,
          [name]: value,
        };
        setPolicyValue(val);
        onChange?.(val);

        // Add / remove the selected retain field to the context so it cannot be used on
        // other retain field selectors
        if (name === retainFieldConfig?.name) {
          let removedField: string[] = [];
          if (defaultValue && value?.length < defaultValue[retainFieldConfig.name]?.length) {
            removedField = difference(defaultValue[retainFieldConfig.name], value);
          }
          setRetainFields(uniq([...retainFields, ...value]).filter((val) => val !== removedField[0]));
        }
      },
      [defaultValue, onChange, policyValue, retainFieldConfig?.name, retainFields, setRetainFields]
    );

    useEffect(() => {
      if (leftValue?.value && operatorValue) {
        if (retainFieldConfig) {
          operatorValue.configuration?.forEach((config) => {
            const picklistKey = makePicklistKey(
              {
                dependantType: config.dependantType,
                dependantField: config.name,
              },
              {},
              leftValue.value
            );
            if (!picklistValues[picklistKey] && config.dependantType && typeof leftValue.value === 'string') {
              dispatch(
                fetchPicklistValues({
                  id: picklistKey,
                  dependantType: config.dependantType,
                  dependantId: leftValue.value,
                })
              );
            }
          });
        }
      }
    }, [dispatch, leftValue, operatorValue, picklistValues, retainFieldConfig]);

    const getPicklistValues = useCallback(
      (config?: SkullItem) => {
        if (config && leftValue) {
          const picklistKey = makePicklistKey(
            {
              dependantType: config.dependantType,
              dependantField: config.name,
            },
            {},
            leftValue.value
          );
          return picklistValues[picklistKey]
            ?.filter((field) => {
              // Keep it if the field is already selected in this component
              if (retainFieldConfig && value?.[retainFieldConfig.name]?.includes(field.value)) {
                return true;
              }
              if (config.name === retainFieldConfig?.name) {
                // Do not include the fields that were selected by other retain field component
                return !retainFields.includes(field.value);
              }
              return true;
            })
            .map((field) => ({
              ...field,
              id: field.value,
              displayName: field.label,
              apiName: '',
            }));
        }
      },
      [leftValue, picklistValues, retainFieldConfig, retainFields, value]
    );

    const retainFieldReadOnly = useMemo(() => {
      const retainFieldTextValue =
        operatorValue?.configuration
          ?.filter((config) => config.renderType !== AppConstants.INPUT_RENDER_TYPE.FIELD_MERGE_POLICY_RETAIN_FIELD)
          .map((config) => {
            const configPolicyValue = policyValue?.[config.name];
            if (
              config.datatype === AppConstants.INPUT_TYPE.MULTIVALUETEXT &&
              configPolicyValue &&
              typeof configPolicyValue !== 'string'
            ) {
              return configPolicyValue.join(', ');
            }
            const picklistValues = getPicklistValues(config);
            if (!picklistValues?.length) {
              return '';
            }
            if (config.renderType === AppConstants.INPUT_RENDER_TYPE.FIELD_MERGE_POLICY_RETAIN_FIELD) {
              if (configPolicyValue && typeof configPolicyValue !== 'string' && configPolicyValue.length) {
                const fieldNames = configPolicyValue.map((fieldId: string) => {
                  const val = picklistValues.find((picklistValue) => picklistValue.value === fieldId);
                  return val ? val.label : '';
                });
                return fieldNames.join(', ');
              }
            } else if (config.datatype) {
              if (config.datatype === AppConstants.INPUT_TYPE.PICKLIST) {
                const val = picklistValues.find((picklistValue) => picklistValue.value === policyValue?.[config.name]);
                if (val) {
                  return val.label;
                }
              }
            }

            return '';
          }) || [];

      if (retainFieldConfig) {
        const picklistValues = getPicklistValues(retainFieldConfig);
        const retainConfigValue = policyValue?.[retainFieldConfig.name];
        if (picklistValues?.length && retainConfigValue && typeof retainConfigValue !== 'string') {
          const fieldNames = retainConfigValue.map((fieldId: string) => {
            const val = picklistValues.find((picklistValue) => picklistValue.value === fieldId);
            return val ? val.label : '';
          });
          if (fieldNames?.length) {
            retainFieldTextValue.push(`${tn('also_copy')} ${fieldNames.join(', ')}`);
          }
        }
      }
      return <span>{retainFieldTextValue.join(' ')}</span>;
    }, [getPicklistValues, operatorValue?.configuration, policyValue, retainFieldConfig, tn]);

    if (displayMode === READONLY) {
      return retainFieldReadOnly;
    }

    return (
      <Stack
        className="field-merge-policy-retain-field"
        spacing={operatorValue?.configuration && operatorValue.configuration?.length <= 1 ? 'z' : 'md'}>
        {operatorValue?.configuration && (
          <HStack className="field-merge-policy-retain-field--config-fields">
            {operatorValue.configuration
              .filter((config) => config.renderType !== AppConstants.INPUT_RENDER_TYPE.FIELD_MERGE_POLICY_RETAIN_FIELD)
              .map((config) => {
                return (
                  <InputContainer
                    {...config}
                    onChange={(value: string) => onRetainFieldChange(config.name, value)}
                    defaultValue={policyValue?.[config.name]}
                    values={getPicklistValues(config)}
                    value={policyValue?.[config.name]}
                  />
                );
              })}
          </HStack>
        )}
        <InputWithLabel
          className="field-merge-policy-retain-field--multi-field"
          datatype={AppConstants.INPUT_TYPE.MULTISELECT_FIELD}
          mode="multiple"
          onChange={(value: string) => retainFieldConfig && onRetainFieldChange(retainFieldConfig.name, value)}
          values={getPicklistValues(retainFieldConfig)}
          value={retainFieldConfig ? value?.[retainFieldConfig.name] : undefined}
          label={tn('also_copy')}
          tooltip={tn('also_copy_tooltip')}
        />
      </Stack>
    );
  },
  'FieldMergePolicyRetainField'
);
