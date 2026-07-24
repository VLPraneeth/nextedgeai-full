import { upperFirst } from 'lodash';
import { ChangeEvent, useCallback, useMemo } from 'react';

import { FieldItem } from 'components/inputs/FieldOptions';
import Select, { Option } from 'components/inputs/Select';
import { HStack, Stack } from 'components/layout';
import { FieldDataType } from 'components/types';
import { Text } from 'components/typography';
import { useEnhancedSelector } from 'hooks/redux';
import useEffectOnValueChange from 'hooks/useEffectOnValueChange';
import { useSelectedNodes } from 'pages/sync-studio/pipeline/PipelineEditor.hooks';
import { selectDynamicConfigValues } from 'selectors/pipelineSelectors';
import { NodeFieldPicklistValue } from 'store/pipeline/types';
import AppConstants from 'utils/AppConstants';
import { tc, tNamespaced } from 'utils/i18nUtil';
import { createApiName, humanize } from 'utils/StringUtil';
import useSetState from 'utils/useSetState';

import Input from './Input';
import InputWithLabel from './InputWithLabel';

import './SetValueField.scss';

const tn = tNamespaced('SetValueField');

export interface SetValueFieldData {
  type: 'temporary' | 'existing';
  newValue: string;
  dataType?: FieldDataType;
  apiName?: string;
  displayName?: string;
  attributeDefinitionId?: string;
  multiValueField?: boolean;
}

export interface SetValueFieldProps {
  id?: string;
  attributeValues: NodeFieldPicklistValue[];
  name: string;
  isField: boolean;
  nodeValue: { isField: boolean; value: null | SetValueFieldData };
  displayMode?: string;
  hideSummary?: boolean;
  value?: string;
  onChange?: (update: { name: string; compositeValues: Partial<SetValueFieldData> }) => void;
  defaultValue?: { compositeValues: SetValueFieldData };
}

const SetValueField = ({
  name,
  displayMode,
  attributeValues,
  isField,
  nodeValue,
  onChange,
  defaultValue,
}: SetValueFieldProps) => {
  const { selectedNodeIds } = useSelectedNodes();
  const configValues = useEnhancedSelector(selectDynamicConfigValues);
  const nodeId = selectedNodeIds[0];

  const dataTypeValues = useMemo(() => {
    if (configValues && nodeId) {
      const nodeConfig = configValues[nodeId]?.configuration;

      if (isField) {
        const setValueFieldConfig = nodeConfig?.find((config: any) => config.name === 'setValueField');
        return setValueFieldConfig?.dataTypeValues;
      } else {
        const dataTypeConfig = nodeConfig?.find((config: any) => config.name === 'dataType');
        return dataTypeConfig?.values;
      }
    }
  }, [configValues, isField, nodeId]);

  const dataTypeOptions = useMemo(() => {
    const datatypes = dataTypeValues
      ? dataTypeValues.map((dataType: any) => dataType.value)
      : ['text', 'boolean', 'date', 'datetime', 'double', 'integer', 'phone', 'email'];

    return datatypes.map((dataType: any) => (
      <Option key={dataType} value={dataType}>
        <FieldItem displayName={upperFirst(dataType)} dataType={dataType as FieldDataType} apiName="" />
      </Option>
    ));
  }, [dataTypeValues]);

  const filterOption = useCallback((inputValue: any, option: any) => {
    return (option as any).props.children.props.displayName?.toLowerCase().includes(inputValue.toLowerCase());
  }, []);

  const disabled = displayMode === AppConstants.INPUT_DISPLAY_MODE.READONLY;

  const [formData, setFormData] = useSetState<Partial<SetValueFieldData>>(
    defaultValue?.compositeValues || { type: 'existing' }
  );

  useEffectOnValueChange(() => {
    if (!disabled) {
      // setTimeout ensures that this value is not overridden with other fields
      // calling onChange with a null value for setValueField
      setTimeout(() => {
        onChange?.({ name, compositeValues: formData });
      }, 30);
    }
  }, [formData]);

  if (disabled) {
    const data = nodeValue.value;

    if (!data) {
      return <Text color="light-gray">{tn('no_field_value')}</Text>;
    }

    if (data.type === 'temporary') {
      return (
        <Stack>
          <Text color="gray-1000">{tn('type', { type: humanize(data.type) })}</Text>
          {data.dataType && (
            <Text color="gray-1000">{tn('field_datatype', { datatype: humanize(data.dataType) })}</Text>
          )}
          {data.displayName && (
            <Text color="gray-1000">
              {tn('field_name_api_name', { name: data.displayName, apiName: data.apiName })}
            </Text>
          )}
          <Text color="gray-1000">
            {tc('multi_value_field')}: {Boolean(data?.multiValueField).toString()}
          </Text>
        </Stack>
      );
    }

    if (isField) {
      return (
        <Stack>
          <Text color="gray-1000">{tn('type', { type: humanize(data.type) })}</Text>
          <Text color="gray-1000">{tn('field_datatype', { datatype: humanize(data.dataType || 'string') })}</Text>
          <Text color="gray-1000">
            {tc('multi_value_field')}: {Boolean(data?.multiValueField).toString()}
          </Text>
        </Stack>
      );
    }

    const selectedField = attributeValues.find((attribute) => attribute.value === data.attributeDefinitionId);

    return (
      <Stack>
        <Text color="gray-1000">{tn('type', { type: humanize(data.type) })}</Text>
        <Text color="gray-1000">{tn('field_name', { name: selectedField?.label || '' })}</Text>
      </Stack>
    );
  }

  const isTempVariable = formData.type === 'temporary';
  const isExistingField = formData.type === 'existing';

  let existingFieldOption = null;
  if (isExistingField) {
    if (isField) {
      existingFieldOption = (
        <InputWithLabel
          label={tn('data_type')}
          className="set-value-field-container--data-type-input"
          placeholder={tn('select_data_type')}
          input={
            <Select<FieldDataType>
              onChange={(value) => {
                setFormData({ dataType: value });
              }}
              filterOption={filterOption}
              value={formData.dataType}>
              {dataTypeOptions}
            </Select>
          }
        />
      );
    } else {
      existingFieldOption = (
        <InputWithLabel
          label={tn('field_selection')}
          className="set-value-field-container--field-selection-input"
          input={
            <Select
              placeholder={tn('field_selection_placeholder')}
              onChange={(value) => {
                setFormData({ attributeDefinitionId: value.toString() });
              }}
              value={formData.attributeDefinitionId}
              optionData={attributeValues}
            />
          }
        />
      );
    }
  }

  return (
    <Stack>
      <HStack grow className="set-value-field-container">
        <InputWithLabel
          label={tn('field_type')}
          input={
            <Select<SetValueFieldData['type']>
              className="set-value-field-container--field-type-input"
              placeholder={tn('select_field_type')}
              onChange={(value) => {
                setFormData({ type: value });
              }}
              value={formData.type}
              optionData={[
                { value: 'temporary', label: tn('temporary_variable') },
                { value: 'existing', label: isField ? tn('this_field') : tn('existing_field') },
              ]}
            />
          }
        />

        {isTempVariable && (
          <InputWithLabel
            label={tn('data_type')}
            className="set-value-field-container--data-type-input"
            placeholder={tn('select_data_type')}
            input={
              <Select<FieldDataType>
                onChange={(value) => {
                  setFormData({ dataType: value });
                }}
                filterOption={filterOption}
                value={formData.dataType}>
                {dataTypeOptions}
              </Select>
            }
          />
        )}

        {isTempVariable && (
          <InputWithLabel
            label={tn('field_display_name')}
            className="set-value-field-container--display-name-input"
            input={
              <Input
                value={formData.displayName}
                onChange={(evt) => {
                  setFormData({ displayName: evt.target.value });
                }}
                onBlur={() => {
                  if (formData.displayName && !formData.apiName) {
                    setFormData({ apiName: createApiName(formData.displayName) });
                  }
                }}
              />
            }
          />
        )}
        {isTempVariable && (
          <InputWithLabel
            label={tn('field_api_name')}
            className="set-value-field-container--api-name-input"
            input={
              <Input
                value={formData.apiName}
                onChange={(evt) => {
                  setFormData({ apiName: evt.target.value });
                }}
              />
            }
          />
        )}

        {existingFieldOption}
      </HStack>

      <InputWithLabel
        name="multiValueField"
        label={tc('multi_value_field')}
        datatype={AppConstants.INPUT_TYPE.CHECKBOX}
        onChange={(evt: ChangeEvent<HTMLInputElement>) => {
          setFormData({ multiValueField: evt.target.checked });
        }}
        checked={formData?.multiValueField}
      />
    </Stack>
  );
};

export default SetValueField;
