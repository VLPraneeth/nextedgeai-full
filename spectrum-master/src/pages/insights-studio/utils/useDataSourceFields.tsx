import { Tooltip } from 'antd';
import ASelect from 'antd/lib/select';
import { ReactElement, useCallback, useMemo } from 'react';

import FieldTypeBadge from 'components/FieldTypeBadge';
import { PicklistValue } from 'components/inputs/types';
import { HStack } from 'components/layout';
import { FieldDataType } from 'components/types';
import { DatasetFields } from 'store/insights-studio/types';

import { splitIdAndAlias, createIdWithAlias } from './UnifiedDataCard.util';
import { useDatasetConfig } from './useDatasetConfig';
import { useUnifiedDataCardAuthoring } from './useUnifiedDataCardAuthoring';

export const { Option, OptGroup } = ASelect;

export interface GroupPicklistSourceField
  extends Pick<
    DatasetFields,
    'datasetId' | 'datasetType' | 'dataType' | 'apiName' | 'displayName' | 'type' | 'datasourceAlias'
  > {
  id: string;
  value: string;
  label: string;
  picklistGroup?: string;
  alias?: string;
}

export const useDataSourceFields = ({
  searchText,
  dataTypes,
  onSelect,
  onMouseEnter,
}: {
  searchText: string;
  dataTypes?: FieldDataType[];
  onSelect?: (value: string) => void;
  onMouseEnter?: (index: number) => void;
}) => {
  const { selectedDataSources, calculatedFields } = useUnifiedDataCardAuthoring();
  const { dataSourceFields } = useDatasetConfig();

  const availableDataSourceFields = useMemo(() => {
    const values: GroupPicklistSourceField[] = [];
    selectedDataSources.forEach((dataSource) => {
      if (dataSource) {
        const key = createIdWithAlias(splitIdAndAlias(dataSource.datasetId).id, dataSource.alias);
        dataSourceFields[key]?.forEach((field) => {
          const displayName = field.displayName || '';

          values.push({
            id: field.fieldId,
            value: createIdWithAlias(field.fieldId, dataSource.alias),
            apiName: field.apiName,
            displayName,
            label: displayName,
            dataType: field.dataType,
            datasetType: field.datasetType,
            datasetId: field.datasetId,
            datasourceAlias: dataSource.alias,
            picklistGroup: dataSource.alias || dataSource.displayName,
            type: field.type,
            alias: field.alias,
          });
        });
      }
    });
    return values;
  }, [dataSourceFields, selectedDataSources]);

  const getSelectOptions = useCallback(
    (values: GroupPicklistSourceField[]) => {
      const groups: Record<string, PicklistValue[]> = {};
      let options: ReactElement[] = [];

      // Note: Organize the list
      values.forEach((value) => {
        const { picklistGroup } = value;
        if (picklistGroup) {
          if (!groups[picklistGroup]) {
            groups[picklistGroup] = [];
          }
          groups[picklistGroup].push({ ...value });
        }
      });

      Object.keys(groups).forEach((key) => {
        let parentVisible = false;
        const childOptions: ReactElement[] = [];
        groups[key].forEach((groupOption: any, index) => {
          const { value, label, apiName, dataType } = groupOption;
          const searchKey = `${label}${apiName}`;
          if (
            !searchKey?.toLowerCase().includes(searchText?.toLowerCase()) ||
            (dataTypes?.length ? !dataTypes.includes(dataType) : false)
          ) {
            return;
          }
          parentVisible = true;
          const tooltip = (
            <>
              {`Entity: ${key}`}
              <br />
              {`API name: ${apiName}`}
            </>
          );

          childOptions.push(
            <Option
              className="data-source-picker__option-fields"
              value={value}
              key={`${key}${label}${apiName}`}
              data-is-dropdown-option>
              <HStack spacing="xxxs">
                <Tooltip title={tooltip} mouseEnterDelay={0.5} placement="bottomLeft">
                  <div
                    style={{ display: 'flex' }}
                    className="data-source-picker__option-fields-label"
                    onClick={
                      onSelect
                        ? (event) => {
                            // Prevent the default select/unselect behaviour and handle manually to achieve multi select of the same item
                            event.stopPropagation();

                            // This same component is rendered as selected tags in the input.
                            // So we are using the data-is-dropdown-option attribute in the parent to make sure to call the onSelect only while the dropdown option and not the selected tag
                            let parent = event.target as HTMLElement | null;
                            while (parent && !parent.hasAttribute('data-is-dropdown-option')) {
                              parent = parent.parentElement;
                            }
                            if (parent?.hasAttribute('data-is-dropdown-option')) {
                              onSelect(value);
                            }
                          }
                        : undefined
                    }
                    onMouseEnter={onMouseEnter ? () => onMouseEnter(index) : undefined}>
                    <FieldTypeBadge dataType={dataType} description={dataType} disableTooltip size="small" />
                    <span>{label}</span>
                  </div>
                </Tooltip>
              </HStack>
            </Option>
          );
        });

        if (parentVisible) {
          options.push(
            <Option
              className="ant-select-dropdown-menu-item-group-title data-source-picker__group"
              title={key}
              key={key}
              value={key}
              disabled>
              {key}
            </Option>
          );
          options = [...options, ...childOptions];
        }
      });

      return options;
    },
    [searchText, dataTypes, onSelect, onMouseEnter]
  );

  const availableSelectOptions = useMemo(() => {
    return getSelectOptions(availableDataSourceFields);
  }, [availableDataSourceFields, getSelectOptions]);

  const sortDataSourceFields = useMemo(() => {
    const calc: GroupPicklistSourceField[] =
      calculatedFields.map((calculatedField) => {
        return {
          apiName: calculatedField.aliasName,
          datasetId: '',
          datasetType: 'DATASET',
          displayName: calculatedField.aliasName,
          id: calculatedField.aliasName,
          label: calculatedField.aliasName,
          picklistGroup: 'Calculated fields',
          type: 'variable',
          value: calculatedField.aliasName,
        };
      }) || [];
    return [...availableDataSourceFields, ...calc];
  }, [availableDataSourceFields, calculatedFields]);

  const sortSelectOptions = useMemo(() => {
    return getSelectOptions(sortDataSourceFields);
  }, [getSelectOptions, sortDataSourceFields]);

  return {
    availableSelectOptions,
    availableDataSourceFields,
    sortDataSourceFields,
    sortSelectOptions,
    getSelectOptions,
  };
};
