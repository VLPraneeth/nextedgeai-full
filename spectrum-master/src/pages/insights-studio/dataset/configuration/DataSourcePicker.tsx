//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Tooltip } from 'antd';
import ASelect, { SelectProps as AntSelectProps, SelectValue } from 'antd/lib/select';
import cx from 'classnames';
import { findLast } from 'lodash';
import { ReactElement, useCallback, useEffect, useMemo, useState } from 'react';

import InputWithLabel from 'components/inputs/InputWithLabel';
import { PicklistValue } from 'components/inputs/types';
import { useInsightsViewContext } from 'pages/insights-studio/context/InsightsViewContext';
import {
  ID_ALIAS_DELIMITER,
  removeDataSourceFields,
  removeFiltersWithRemovedEntityFields,
  removeInvalidJoin,
  splitIdAndAlias,
} from 'pages/insights-studio/utils/UnifiedDataCard.util';
import { useDatasetConfig } from 'pages/insights-studio/utils/useDatasetConfig';
import { useUnifiedDataCardAuthoring } from 'pages/insights-studio/utils/useUnifiedDataCardAuthoring';
import { useGetDatasetAndEntityInfoQuery, useLazyGetRecommendedJoinQuery } from 'store/insights-studio';
import { DataSource } from 'store/insights-studio/types';

import './DataSourcePicker.less';

export const { Option, OptGroup } = ASelect;
export interface SelectProps<Option extends SelectValue = SelectValue>
  extends Pick<AntSelectProps<Option>, 'onChange'> {
  options?: ReactElement;
}

export const DataSourcePicker = <Option extends SelectValue = SelectValue>({ options }: SelectProps<Option>) => {
  const { dataSourcePicklistValues } = useDatasetConfig();
  const [searchText, setSearchText] = useState('');
  const {
    selectedDataSourceFields,
    selectedDataSources,
    setBlendedData,
    setFilter,
    setSelectedDataSourceFields,
    setSelectedDataSources,
    setSort,
    setGroupBy,
    setCalculatedFields,
  } = useUnifiedDataCardAuthoring();
  const [
    fetchRecommendedJoin,
    { data: recommendedJoins, isFetching: recommendedJoinsFetching },
  ] = useLazyGetRecommendedJoinQuery();
  const { isThoughtSpotView } = useInsightsViewContext();
  const { data: allDataSources } = useGetDatasetAndEntityInfoQuery({
    isThoughtspot: isThoughtSpotView,
    withEntityInfo: true,
  });

  const values = useMemo(() => {
    return dataSourcePicklistValues;
  }, [dataSourcePicklistValues]);

  const onDeselect = (dataSourceId: string) => {
    const removedDatasourceSource = findLast(selectedDataSources, (dataSource) =>
      dataSource?.datasetId.includes(dataSourceId)
    );
    const newDataSources = selectedDataSources.filter(
      (dataSource) => dataSource.datasetId !== removedDatasourceSource?.datasetId
    );
    setSelectedDataSources(newDataSources);

    // An entity has been removed. Remove everything using a field from a removed entity
    setBlendedData((current) => removeInvalidJoin(current, newDataSources));
    if (selectedDataSourceFields) {
      setSelectedDataSourceFields(removeDataSourceFields(selectedDataSourceFields, newDataSources));
    }
    setSort((current) => {
      return current?.filter((sort) =>
        newDataSources.find(
          (ds) => splitIdAndAlias(ds.datasetId).id === sort.field?.datasetId && ds.alias === sort.field.datasourceAlias
        )
      );
    });
    setFilter((prevFilter) => removeFiltersWithRemovedEntityFields(prevFilter, newDataSources));
    setGroupBy((prev) =>
      prev?.filter(
        (group) =>
          !!(
            group.datasetField &&
            newDataSources.find(
              (ds) =>
                splitIdAndAlias(ds.datasetId).id === group.datasetField?.datasetId &&
                (group.datasetField?.datasourceAlias ? ds.alias === group.datasetField.datasourceAlias : true)
            )
          )
      )
    );
    setCalculatedFields((prev) => {
      return prev.filter((calcField) => {
        const cond = calcField.datasetFields.every((field) =>
          newDataSources.find(
            (ds) => splitIdAndAlias(ds.datasetId).id === field?.datasetId && ds.alias === field.datasourceAlias
          )
        );
        return cond;
      });
    });
  };

  const onSelect = useCallback(
    (dataSourceId: string) => {
      setSearchText('');
      const existingDataSource = findLast(selectedDataSources, (dataSource) =>
        dataSource?.datasetId.includes(dataSourceId)
      );
      const dataSourceNewlySelected = allDataSources?.find((dataSource) => dataSource.datasetId === dataSourceId);

      let newDataSourceId = '';
      let alias = dataSourceNewlySelected?.displayName || dataSourceNewlySelected?.apiName || '';
      if (!existingDataSource?.datasetId) {
        newDataSourceId = `${dataSourceId}${ID_ALIAS_DELIMITER}1`;
      } else {
        const [, suffix] = existingDataSource.datasetId.split(ID_ALIAS_DELIMITER);
        alias = `${alias}_${suffix}`;
        newDataSourceId = `${dataSourceId}${ID_ALIAS_DELIMITER}${Number(suffix) + 1}`;
      }

      if (dataSourceNewlySelected) {
        setSelectedDataSources((selectedDataSources: DataSource[]) => [
          ...(selectedDataSources || []),
          {
            ...dataSourceNewlySelected,
            datasetId: newDataSourceId,
            alias,
          },
        ]);
      }

      const previouslySelectedDataSources: DataSource[] = [];
      const newlySelectedDataSources: DataSource[] = [];

      selectedDataSources.forEach((dataSource) => {
        if (dataSource) {
          previouslySelectedDataSources.push({ ...dataSource, datasetId: splitIdAndAlias(dataSource.datasetId).id });
        }
      });
      if (dataSourceNewlySelected) {
        newlySelectedDataSources.push({ ...dataSourceNewlySelected, alias });
      }

      // Skip fetch when starting from blank list since we know we're not going
      // to get any result from the fetch recommended join request.
      if (previouslySelectedDataSources.length <= 0) {
        return;
      }

      // Request for recommended joins
      fetchRecommendedJoin(
        {
          existingDataSources: previouslySelectedDataSources,
          newDataSources: newlySelectedDataSources,
        },
        false
      );
    },
    [allDataSources, fetchRecommendedJoin, selectedDataSources, setSelectedDataSources]
  );

  const selectOptions = useMemo(() => {
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
      groups[key].forEach((groupOption: any) => {
        const { value, label, apiName } = groupOption;
        const searchKey = `${label}${apiName}`;
        if (!searchKey?.toLowerCase().includes(searchText?.toLowerCase())) {
          return;
        }
        parentVisible = true;
        const tooltip = (
          <>
            {`Type: ${key === 'Entities' ? 'Entity' : 'Data set'}`}
            <br />
            {`API name: ${apiName}`}
          </>
        );
        childOptions.push(
          <Option
            className="data-source-picker__option"
            value={value}
            key={`${key}${label}${apiName}`}
            disabled={recommendedJoinsFetching}>
            <Tooltip title={tooltip} mouseEnterDelay={0.5} placement="bottomLeft">
              <span
                onClick={(event) => {
                  if (recommendedJoinsFetching) {
                    return;
                  }
                  // Prevent the default select/unselect behaviour and handle manually to achieve multi select of the same item
                  event.stopPropagation();
                  onSelect(value);
                }}
                className="data-source-picker__option-label">
                {label}
              </span>
            </Tooltip>
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
  }, [searchText, values, onSelect, recommendedJoinsFetching]);

  // Append recommended joins from the server
  useEffect(() => {
    if (recommendedJoins) {
      setBlendedData((current) => [...(current || []), ...recommendedJoins]);
    }
  }, [recommendedJoins, setBlendedData]);

  return (
    <div className="data-source-picker">
      <InputWithLabel
        label="Select data"
        tooltip="Select Entities or Data sets as the source for records"
        input={
          <ASelect
            loading={recommendedJoinsFetching}
            className={cx('data-source-picker__select')}
            mode="multiple"
            onSelect={onSelect}
            onDeselect={onDeselect}
            value={selectedDataSources.map((dataSource) => splitIdAndAlias(dataSource.datasetId).id)}
            onSearch={(text) => setSearchText(text?.trim())}
            onBlur={() => setSearchText('')}
            onChange={() => setSearchText('')}
            autoClearSearchValue
            //@ts-ignore
            searchText={searchText}
            // We perform our own search/filtering
            filterOption={false}>
            {selectOptions}
          </ASelect>
        }
      />
    </div>
  );
};
