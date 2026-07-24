import ObjectID from 'bson-objectid';

import { useI18nContext, withI18n } from 'components/I18nProvider';
import InfoBox from 'components/InfoBox';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { lookupByDatasetIdAndApiNameAndAlias } from 'pages/insights-studio/utils/UnifiedDataCard.util';
import { useDataSourceFields } from 'pages/insights-studio/utils/useDataSourceFields';
import { useUnifiedDataCardAuthoring } from 'pages/insights-studio/utils/useUnifiedDataCardAuthoring';
import { DatasetSort, DataSourceFields } from 'store/insights-studio/types';
import AppConstants from 'utils/AppConstants';

import { CompositeInputValue, GroupCompositeValues } from './GroupPicker';

const SortDirection = {
  ascending: 'ascending',
  descending: 'decending',
};

export interface SortCompositeValue {
  repeatId?: string;
  field?: CompositeInputValue;
  order?: CompositeInputValue;
}

export type SortCompositeValues = GroupCompositeValues<SortCompositeValue>;

const SortPickerComponent = () => {
  const { selectedDataSourceFields, calculatedFields } = useUnifiedDataCardAuthoring();
  const { tn } = useI18nContext();
  const { sortDataSourceFields } = useDataSourceFields({ searchText: '' });
  const { sort, setSort } = useUnifiedDataCardAuthoring();

  // Get all selected fields across all entities
  const selectedFields = selectedDataSourceFields?.entities.flatMap((entity) => entity.fields) ?? [];

  const sortByConfiguration = [
    {
      id: 'field',
      renderType: AppConstants.INPUT_RENDER_TYPE.DATASET_SELECTED_FIELD_PICKER,
      name: 'field',
      optionsKey: 'sort',
    },
    {
      id: 'order',
      datatype: AppConstants.INPUT_TYPE.PICKLIST,
      name: 'order',
      values: [
        {
          label: tn('ascending'),
          value: SortDirection.ascending,
        },
        {
          label: tn('descending'),
          value: SortDirection.descending,
        },
      ],
    },
  ];

  const sortFields: DataSourceFields[] = sortDataSourceFields.map((field) => ({
    apiName: field.apiName,
    dataType: field.dataType,
    datasetId: field.datasetId,
    displayName: field.displayName,
    datasetType: field.datasetType,
    alias: field.apiName,
    fieldId: field.id || field.apiName,
    type: 'variable',
    datasourceAlias: field.datasourceAlias,
  }));

  const sortValue = datasetSortToSortComposite(sort || [], sortFields);

  const noSelectedFields = !selectedFields || selectedFields.length < 1;
  const noCalculatedFields = !calculatedFields || calculatedFields.length < 1;

  if (noSelectedFields && noCalculatedFields) {
    return <InfoBox message={tn('select_field_to_sort')} type="info" showIcon />;
  }

  return (
    <div className="sort-picker">
      <InputWithLabel
        name="sort"
        id="sort"
        addText={tn('select_sort_by_add_text')}
        datatype={AppConstants.INPUT_TYPE.COMPOSITE}
        configuration={sortByConfiguration}
        repeatable
        hideOrderNumber
        defaultValue={sortValue}
        value={sortValue}
        onChange={(sortCompositeValues: SortCompositeValues) => {
          setSort(sortCompositeToDatasetSort(sortCompositeValues, sortFields));
        }}
      />
    </div>
  );
};

export const SortPicker = withI18n(SortPickerComponent, 'Dataset');

const sortCompositeToDatasetSort = (sortValues: SortCompositeValues, dataSourceFields: DataSourceFields[]) => {
  if (!dataSourceFields || !sortValues) {
    return [];
  }

  const sortValue: DatasetSort[] = [];

  sortValues.compositeValues?.forEach((sortComposite) => {
    const fieldId = sortComposite.field?.id;
    const datasourceAlias = sortComposite.field?.datasourceAlias;
    const datasetField = dataSourceFields.find(
      (datasetField) =>
        datasetField.fieldId === fieldId && (datasourceAlias ? datasetField.datasourceAlias === datasourceAlias : true)
    );
    if (datasetField) {
      sortValue.push({
        sortId: sortComposite.repeatId,
        ascending: sortComposite?.order?.value ? sortComposite?.order?.value === SortDirection.ascending : undefined,
        field: datasetField,
      });
    } else if (sortComposite.repeatId || sortComposite.field?.name) {
      sortValue.push({
        sortId: sortComposite.repeatId,
      });
    }
  });

  return sortValue;
};

const datasetSortToSortComposite = (sorts: DatasetSort[], dataSourceFields: DataSourceFields[]) => {
  const compositeValues: SortCompositeValue[] =
    sorts?.map((sort) => {
      const ascending = sort.ascending;
      let fieldId = sort.field?.fieldId;
      const datasourceAlias = sort?.field?.datasourceAlias;
      if (!fieldId && sort.field?.apiName) {
        if (sort.field?.datasetId) {
          const datasetField = lookupByDatasetIdAndApiNameAndAlias(
            sort.field.datasetId,
            sort.field.apiName,
            dataSourceFields,
            datasourceAlias
          );
          if (datasetField) {
            fieldId = datasetField.fieldId;
          }
        } else {
          // Calculated field doesn't have a datasetid, use the apiName
          fieldId = sort.field.apiName;
        }
      }
      return {
        field: {
          name: 'field',
          id: fieldId || '',
          datasourceAlias,
        },
        order: {
          name: 'order',
          value: ascending === false ? SortDirection.descending : SortDirection.ascending,
        },
        repeatId: sort.sortId || ObjectID.generate(),
      };
    }) || [];
  return { name: 'sort', compositeValues };
};
