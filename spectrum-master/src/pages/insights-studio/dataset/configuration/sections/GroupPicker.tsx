import { Checkbox } from 'antd';
import ObjectID from 'bson-objectid';
import { useMemo } from 'react';

import { useI18nContext, withI18n } from 'components/I18nProvider';
import InfoBox from 'components/InfoBox';
import InputWithLabel from 'components/inputs/InputWithLabel';
import {
  flatDataSourceFields,
  lookupByDatasetIdAndApiNameAndAlias,
} from 'pages/insights-studio/utils/UnifiedDataCard.util';
import { useDatasetConfig } from 'pages/insights-studio/utils/useDatasetConfig';
import { useDatasetGroup } from 'pages/insights-studio/utils/useDatasetGroup';
import { useDataSourceFields } from 'pages/insights-studio/utils/useDataSourceFields';
import { useUnifiedDataCardAuthoring } from 'pages/insights-studio/utils/useUnifiedDataCardAuthoring';
import { DataSourceFields, Group } from 'store/insights-studio/types';
import AppConstants from 'utils/AppConstants';

import './GroupPicker.less';

const { INPUT_TYPE } = AppConstants;

export interface CompositeInputValue {
  name: string;
  value?: string;
  id?: string;
  datasourceAlias?: string;
}

export interface GroupInputValue {
  field?: DataSourceFields;
  name: string;
}

export interface GroupCompositeValue<TValue = GroupInputValue> {
  repeatId?: string;
  group?: TValue;
}

export interface GroupCompositeValues<TCompositeValue = GroupCompositeValue> {
  name: string;
  compositeValues?: TCompositeValue[];
}

export const GroupPickerComponent = () => {
  const { selectedDataSources, group, setGroup, groupBy, setGroupBy } = useUnifiedDataCardAuthoring();
  const { dataSourceFields } = useDatasetConfig();
  const { tn } = useI18nContext();
  const { syncGroups } = useDatasetGroup();
  const { sortDataSourceFields: groupDataSourceFields } = useDataSourceFields({ searchText: '' });

  const groupFields = useMemo(() => {
    const fields = flatDataSourceFields(dataSourceFields);

    const groupDSFields: DataSourceFields[] = groupDataSourceFields.map((field) => {
      const datasetField = lookupByDatasetIdAndApiNameAndAlias(
        field.datasetId,
        field.apiName,
        fields,
        field.datasourceAlias
      );
      return {
        apiName: field.apiName,
        dataType: field.dataType,
        datasetId: field.datasetId,
        displayName: field.displayName,
        datasetType: field.datasetType,
        alias: field.apiName,
        fieldId: datasetField?.fieldId || field.apiName,
        datasourceAlias: datasetField?.datasourceAlias,
        type: 'variable',
      };
    });
    return groupDSFields;
  }, [dataSourceFields, groupDataSourceFields]);

  const groupDefaultValue = useMemo(() => fromGroup(groupBy || [], groupFields), [groupBy, groupFields]);

  if (selectedDataSources?.length < 1) {
    return <InfoBox message="You need to select a data source to add groups." type="info" showIcon />;
  }

  return (
    <div className="group-picker">
      <Checkbox
        onChange={(e) => {
          if (e.target.checked) {
            syncGroups();
          }
          setGroup(e.target.checked);
        }}
        checked={group}>
        Group data
      </Checkbox>
      {group && (
        <InputWithLabel
          name="group"
          id="group"
          hideOrderNumber
          hideDelete
          addText={tn('select_group_add_text')}
          datatype={INPUT_TYPE.COMPOSITE}
          configuration={[
            {
              name: 'group',
              renderType: AppConstants.INPUT_RENDER_TYPE.DATASET_GROUP_FIELD_PICKER,
            },
          ]}
          defaultValue={groupDefaultValue}
          onChange={(groupCompositeValues: GroupCompositeValues) => {
            setGroupBy(toGroup(groupCompositeValues, groupFields));
          }}
        />
      )}
    </div>
  );
};

export const GroupPicker = withI18n(GroupPickerComponent, 'Dataset');

const toGroup = (groupByValues?: GroupCompositeValues, dataSourceFields?: DataSourceFields[]) => {
  let groupByValue: Group[] = [];
  if (!dataSourceFields) {
    return [];
  }

  groupByValues?.compositeValues?.forEach((groupBy) => {
    const datasetField = dataSourceFields.find(
      (datasetField) => datasetField.fieldId === groupBy.group?.field?.fieldId
    );
    if (datasetField) {
      groupByValue.push({
        groupId: groupBy.repeatId,
        datasetField,
      });
    } else if (groupBy.repeatId || groupBy.group?.field?.apiName) {
      groupByValue.push({
        groupId: groupBy.repeatId,
      });
    }
  });

  return groupByValue;
};

const fromGroup = (groups: Group[], dataSourceFields: DataSourceFields[]) => {
  const compositeValues: GroupCompositeValue[] =
    groups?.map((group) => {
      let fieldId = group.datasetField?.fieldId || '';
      if (!fieldId && group.datasetField?.apiName) {
        if (group.datasetField?.datasetId) {
          const datasetField = lookupByDatasetIdAndApiNameAndAlias(
            group.datasetField.datasetId,
            group.datasetField.apiName,
            dataSourceFields,
            group.datasetField?.datasourceAlias
          );
          if (datasetField) {
            fieldId = datasetField.fieldId;
          }
        } else if (!group.datasetField?.datasetId) {
          // Calculated field doesn't have a datasetid, use the apiName
          fieldId = group.datasetField.apiName;
        }
      }
      return {
        group: {
          field: {
            type: 'variable',
            datasetType: group.datasetField?.datasetType || 'ENTITY',
            datasetId: group.datasetField?.datasetId || '',
            dataType: group.datasetField?.dataType,
            apiName: group.datasetField?.apiName || '',
            fieldId,
            datasourceAlias: group.datasetField?.datasourceAlias,
          },
          name: 'group',
        },
        repeatId: group.groupId || ObjectID.generate(),
      };
    }) || [];

  return { name: 'group', compositeValues };
};
