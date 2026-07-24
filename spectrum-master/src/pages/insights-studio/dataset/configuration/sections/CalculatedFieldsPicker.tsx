//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { Alert, Button, Icon, Tooltip } from 'antd';
import Popover from 'antd/lib/popover';
import ASelect from 'antd/lib/select';
import { isEmpty, keyBy, trim } from 'lodash';
import { ChangeEvent, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { findDOMNode } from 'react-dom';

import { useI18nContext } from 'components/I18nProvider';
import InputContainer from 'components/inputs/InputContainer';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { Divider, HStack, Stack } from 'components/layout';
import { FieldDataType } from 'components/types';
import {
  createIdWithAlias,
  flatDataSourceFields,
  lookupByDatasetIdAndApiNameAndAlias,
} from 'pages/insights-studio/utils/UnifiedDataCard.util';
import { useDatasetConfig } from 'pages/insights-studio/utils/useDatasetConfig';
import { useDataSourceFields } from 'pages/insights-studio/utils/useDataSourceFields';
import { useGetDatasetFunctionsQuery } from 'store/insights-studio';
import AppConstants from 'utils/AppConstants';
import { tNamespaced } from 'utils/i18nUtil';

import { CalculatedField, CalculatedFieldParam } from './CalculatedFields.types';

const { Option } = ASelect;

const tn = tNamespaced('Dataset.FunctionPicker');

export interface CalculatedFieldsPickerProps {
  onSave: (value: CalculatedField) => void;
  onClose: () => void;
  editField?: CalculatedField;
}

export interface DatasetFunctionPicklistValue {
  label: string;
  value: string;
  dataType: FieldDataType;
  functionInputDataTypes: FieldDataType[];
}

export interface AggFunction {
  name: string;
  dataType: FieldDataType;
  functionInputDataTypes?: FieldDataType[];
}

function focusFirstDropdownOption() {
  requestAnimationFrame(() => {
    const dropdownOptions = document.querySelector('.ant-select-dropdown:not(.ant-select-dropdown-hidden)');
    if (dropdownOptions) {
      const firstOption: any = dropdownOptions.querySelector(
        '.ant-select-dropdown-menu-item:not(.ant-select-dropdown-menu-item-disabled)'
      );

      if (firstOption) {
        const mouseoverEvent = new MouseEvent('mouseover', { bubbles: true });
        firstOption.dispatchEvent(mouseoverEvent);
      }
    }
  });
}

export const CalculatedFieldsPicker = ({ onSave, onClose, editField }: CalculatedFieldsPickerProps) => {
  const { tc } = useI18nContext();
  const { dataSourceFields } = useDatasetConfig();

  // TODO: If a dataset gets removed then the existing calculated fields
  // will not be valid. Show an alert to the user.
  const { data: datasetFunctions } = useGetDatasetFunctionsQuery();

  const [params, setParams] = useState<string[]>([]);
  const [fieldName, setFieldName] = useState(editField?.aliasName || '');
  const [aggFunction, setAggFunction] = useState<AggFunction>({
    name: editField?.aggFunctions || '',
    dataType: editField?.dataType || 'string',
  });
  const [validationMessage, setValidationMessage] = useState<string | null>(null);

  useEffect(() => {
    const flatDsFields = flatDataSourceFields(dataSourceFields);
    let seen: Record<string, boolean> = {};
    let uniqueFields = editField?.datasetFields.filter((entry) => {
      let key = `${entry.datasetId}${entry.datasourceAlias}|${entry.apiName}`;
      if (seen[key]) {
        return false;
      }
      seen[key] = true;
      return true;
    });
    setParams(
      uniqueFields
        ?.map((field) => {
          if (field.datasetId && field.apiName) {
            return createIdWithAlias(
              lookupByDatasetIdAndApiNameAndAlias(field.datasetId, field.apiName, flatDsFields, field.datasourceAlias)
                ?.fieldId,
              field.datasourceAlias
            );
          }
          return createIdWithAlias(field.apiName, field.datasourceAlias);
        })
        ?.filter((param) => param?.length > 0) || []
    );
    setDuplicateSelectValue(
      editField?.datasetFields
        ?.map((field, index) => {
          if (field.datasetId && field.apiName) {
            return {
              value: createIdWithAlias(
                lookupByDatasetIdAndApiNameAndAlias(field.datasetId, field.apiName, flatDsFields, field.datasourceAlias)
                  ?.fieldId,
                field.datasourceAlias
              ),
              id: Date.now() + index,
            };
          }
          return { value: createIdWithAlias(field.apiName, field.datasourceAlias), id: Date.now() + index };
        })
        ?.filter((param) => param.value?.length > 0) || []
    );
    setFieldName(editField?.aliasName || '');
    setAggFunction({
      name: editField?.aggFunctions || '',
      dataType: editField?.dataType || 'string',
      functionInputDataTypes: datasetFunctions?.find((func) => func.name === editField?.aggFunctions)
        ?.functionInputDataTypes,
    });
  }, [dataSourceFields, editField, datasetFunctions]);

  const [searchText, setSearchText] = useState('');
  const [selectKey, setSelectKey] = useState(0);
  const [dropdownVisible, setDropdownVisible] = useState(false);
  const [duplicateSelectValue, setDuplicateSelectValue] = useState<{ value: string; id: number }[]>([]);

  const selectRef = useRef(null);
  const [highlightedIndex, setHighlightedIndex] = useState(0);

  const handleSelect = useCallback(
    (value: string) => {
      if (params.includes(value)) {
        setDuplicateSelectValue((dupSelect) => {
          return [...dupSelect, { value, id: Date.now() }];
        });
      } else {
        setParams((params) => [...params, value]);
        setDuplicateSelectValue((dupSelect) => {
          return [...dupSelect, { value, id: Date.now() }];
        });
      }
      if (searchText) {
        setSelectKey((prevKey) => prevKey + 1);
        setSearchText('');
      }
      setHighlightedIndex(0);
      focusFirstDropdownOption();
    },
    [params, searchText]
  );

  const onSelect = useCallback(
    (value: string) => {
      handleSelect(value);
    },
    [handleSelect]
  );

  const onMouseEnter = useCallback((index: number) => {
    setHighlightedIndex(index);
  }, []);

  const { availableSelectOptions, availableDataSourceFields } = useDataSourceFields({
    searchText,
    dataTypes: aggFunction?.functionInputDataTypes,
    onSelect: handleSelect,
    onMouseEnter,
  });

  const onDeselect = useCallback(
    (param: any, event: any, idx: any) => {
      const count = duplicateSelectValue.reduce((count, current) => count + (current.value === param ? 1 : 0), 0);
      if (count === 1) {
        setParams((params) => {
          const index = params.lastIndexOf(param);
          return index >= 0 ? [...params.slice(0, index), ...params.slice(index + 1)] : params;
        });
      }
      setDuplicateSelectValue((params) => {
        return params.filter((p) => {
          if (p.value === param && p.id === idx) {
            return false;
          }
          return true;
        });
      });
      setSelectKey((prevKey) => prevKey + 1);
      setHighlightedIndex(0);
      focusFirstDropdownOption();
    },
    [duplicateSelectValue]
  );

  const selectOptions = useMemo(() => {
    return [
      ...(searchText
        ? [
            <Option value={searchText} className="data-source-picker__option-fields">
              <div
                className="data-source-picker__option-fields-label"
                onClick={(event) => {
                  // Prevent the default select/unselect behaviour and handle manually to achieve multi select of the same item
                  event.stopPropagation();
                  onSelect(searchText);
                }}
                onMouseOver={() => {
                  setHighlightedIndex(0);
                }}>
                {searchText}
              </div>
            </Option>,
          ]
        : []),
      ...availableSelectOptions,
    ];
  }, [searchText, availableSelectOptions, onSelect]);

  useEffect(() => {
    const selectNode = findDOMNode(selectRef.current);
    const filteredSelectOptions = selectOptions.filter((option) => !option.props?.disabled) || [];
    if (selectNode && selectNode.addEventListener) {
      const handleKeyDown = (e: any) => {
        if (e.key === 'Enter' && highlightedIndex >= 0) {
          e.stopPropagation();
          e.preventDefault();

          const optionToSelect = filteredSelectOptions?.[highlightedIndex];
          if (optionToSelect) {
            onSelect(optionToSelect.props.value);
          }

          return false;
        } else if (e.key === 'ArrowUp' || e.key === 'ArrowDown') {
          e.preventDefault();
          setHighlightedIndex((prevIndex) => {
            if (e.key === 'ArrowUp') {
              return prevIndex === 0 ? filteredSelectOptions.length - 1 : Math.max(1, prevIndex - 1);
            } else {
              return prevIndex === filteredSelectOptions.length - 1 ? 0 : prevIndex + 1;
            }
          });
        }
      };

      selectNode.addEventListener('keydown', handleKeyDown);

      return () => {
        if (selectNode && selectNode.removeEventListener) {
          selectNode.removeEventListener('keydown', handleKeyDown);
        }
      };
    }
  }, [highlightedIndex, selectOptions, onSelect, searchText]);

  const functionPicklistValues: DatasetFunctionPicklistValue[] = useMemo(() => {
    return (
      datasetFunctions?.map((datasetFunction) => {
        return {
          label: datasetFunction.displayName,
          value: datasetFunction.name,
          dataType: datasetFunction.dataType,
          functionInputDataTypes: datasetFunction.functionInputDataTypes,
        };
      }) ?? []
    );
  }, [datasetFunctions]);

  const functionTooltip = useMemo(() => {
    const func = datasetFunctions?.find((dsFunction) => dsFunction.name === aggFunction.name);
    return func?.description ? func.description : '';
  }, [datasetFunctions, aggFunction.name]);

  const validate = () => {
    if (!aggFunction.name) {
      setValidationMessage('Function is required.');
      return false;
    }
    if (isEmpty(params)) {
      setValidationMessage('At least one param is required.');
      return false;
    }
    if (!fieldName) {
      setValidationMessage('Field name is required.');
      return false;
    }
    setValidationMessage(null);
    return true;
  };

  const save = () => {
    const valid = validate();
    if (!valid) {
      return;
    }

    const availableDataSourceFieldsMap = keyBy(availableDataSourceFields, 'value');

    const builtParams = duplicateSelectValue.map<CalculatedFieldParam>(({ value }) => {
      // See if the param matches one of the availableDataSourceFields keys
      const matchingField = availableDataSourceFieldsMap[value];

      if (matchingField) {
        const datasetField = flatDataSourceFields(dataSourceFields).find((field) => field.fieldId === matchingField.id);

        return {
          apiName: matchingField.apiName,
          dataType: matchingField.dataType,
          datasetId: datasetField?.datasetId,
          datasetType: datasetField?.datasetType,
          displayName: matchingField.displayName || matchingField.apiName,
          type: 'variable',
          fieldId: matchingField.id,
          datasourceAlias: matchingField.datasourceAlias,
        };
      } else {
        return {
          apiName: value,
          dataType: 'string',
          datasetType: 'LITERAL',
          type: 'variable',
          displayName: value,
        };
      }
    });

    onSave({
      aliasName: fieldName,
      apiName: fieldName,
      datasetFields: builtParams,
      aggFunctions: aggFunction.name,
      dataType: aggFunction.dataType,
    });

    onClose();
  };

  return (
    <Popover
      placement="bottomLeft"
      trigger="click"
      overlayClassName="calculated-fields-picker--pop-over"
      visible
      content={
        <Stack className="calculated-fields-picker--inputs">
          {validationMessage && <Alert message={validationMessage} type="error" />}
          <HStack>
            <InputWithLabel
              name="function"
              label="Function"
              required
              input={
                <InputContainer
                  name="function"
                  values={functionPicklistValues}
                  value={aggFunction.name}
                  datatype={AppConstants.INPUT_TYPE.PICKLIST}
                  onChange={(value: string) => {
                    const fFunction = functionPicklistValues?.find((func) => func.value === value);
                    if (fFunction) {
                      setAggFunction({
                        name: fFunction.value,
                        dataType: fFunction.dataType,
                        functionInputDataTypes: fFunction.functionInputDataTypes,
                      });
                    }
                  }}
                />
              }
            />

            <Tooltip title={functionTooltip || tn('pick_function')}>
              <Icon style={{ marginTop: 24 }} type="question-circle" theme="filled" />
            </Tooltip>
          </HStack>

          <InputWithLabel
            label="Params"
            required
            input={
              <ASelect
                className="data-source-picker__select"
                mode="tags"
                onChange={() => setSearchText('')}
                onBlur={() => {
                  setSearchText('');
                  setHighlightedIndex(0);
                }}
                //@ts-ignore
                duplicatesAllowed
                //@ts-ignore
                duplicateSelectValue={duplicateSelectValue}
                //@ts-ignore
                searchText={searchText}
                ref={selectRef}
                onSelect={onSelect}
                //@ts-ignore
                onDeselect={onDeselect}
                value={params}
                onSearch={(text) => setSearchText(text?.trim())}
                key={selectKey}
                open={dropdownVisible}
                onDropdownVisibleChange={(visible) => setDropdownVisible(visible)}
                // Alway perform our own search
                filterOption={() => true}>
                {selectOptions}
              </ASelect>
            }
          />

          <InputWithLabel
            name="fieldName"
            label="Field Name"
            value={fieldName}
            required
            onChange={(e: ChangeEvent<HTMLInputElement>) => setFieldName(trim(e.target.value))}
          />

          <Divider y="z" />
          <div className="calculated-fields-picker--footer">
            <Button size="small" onClick={onClose}>
              {tc('close')}
            </Button>
            <Button size="small" type="primary" onClick={save}>
              {editField ? tc('update') : tc('add')}
            </Button>
          </div>
        </Stack>
      }
    />
  );
};
