import { Icon } from 'antd';
import ObjectID from 'bson-objectid';
import { useCallback, useMemo } from 'react';

import Button from 'components/Button';
import { withI18n } from 'components/I18nProvider';
import InfoBox from 'components/InfoBox';
import { HStack, Stack } from 'components/layout';
import { TranslatedText } from 'components/typography';
import {
  fillJoinId,
  flatDataSourceFields,
  lookupByDatasetIdAndApiNameAndAlias,
} from 'pages/insights-studio/utils/UnifiedDataCard.util';
import { useDatasetConfig } from 'pages/insights-studio/utils/useDatasetConfig';
import { useUnifiedDataCardAuthoring } from 'pages/insights-studio/utils/useUnifiedDataCardAuthoring';
import { DatasetFields, DataSourceFields, Joins } from 'store/insights-studio/types';

import { DataSourceFieldPicker } from '../DataSourceFieldPicker';

import './BlendDataPicker.less';

export const BlendDataPickerComponent = () => {
  const { selectedDataSources, blendedData, setBlendedData } = useUnifiedDataCardAuthoring();
  const { dataSourceFields } = useDatasetConfig();

  const onChange = useCallback(
    (id: string, blendedDataId: string, key: string, datasourceAlias?: string) => {
      let foundField: DataSourceFields | undefined;
      Object.keys(dataSourceFields).some((key) => {
        if (!foundField) {
          foundField = dataSourceFields[key].find(
            (field) => field.fieldId === id && field.datasourceAlias === datasourceAlias
          );
          if (foundField) {
            return true;
          }
        }
        return false;
      });

      if (foundField) {
        const newB =
          blendedData?.map((blendedDatum) => {
            if (blendedDatum.joinId && blendedDataId === blendedDatum.joinId) {
              return {
                ...blendedDatum,
                [key]: foundField as DatasetFields,
              };
            }
            return blendedDatum;
          }) || [];

        setBlendedData(newB);
      }
    },
    [blendedData, dataSourceFields, setBlendedData]
  );

  const deleteJoin = useCallback(
    (id?: string) => {
      if (blendedData) {
        id && setBlendedData(fillJoinId(blendedData).filter((blendedDatum) => blendedDatum?.joinId !== id) || []);
      }
    },
    [blendedData, setBlendedData]
  );

  const fixedBlendedData = useMemo(() => {
    return fillJoinId(blendedData || []).map((blendedDatum) => fixJoinFields(blendedDatum, dataSourceFields));
  }, [blendedData, dataSourceFields]);

  if (selectedDataSources?.length < 2) {
    return <InfoBox message="You need at least two data source to blend data." type="info" showIcon />;
  }

  return (
    <Stack className="blend-data-picker">
      {fixedBlendedData.map((blendedDatum) => {
        return (
          <HStack key={blendedDatum.joinId} className="blend-data-picker_join">
            <span>Match</span>
            <DataSourceFieldPicker
              value={{
                id: blendedDatum?.field1?.fieldId || '',
                value: blendedDatum?.field1?.fieldId || '',
                datasourceAlias: blendedDatum?.field1?.datasourceAlias,
              }}
              onChange={(value) => {
                blendedDatum.joinId && onChange(value.id, blendedDatum.joinId, 'field1', value.datasourceAlias);
              }}
            />
            <span>with</span>
            <DataSourceFieldPicker
              value={{
                id: blendedDatum?.field2?.fieldId || '',
                value: blendedDatum?.field2?.fieldId || '',
                datasourceAlias: blendedDatum?.field2?.datasourceAlias,
              }}
              onChange={(value) =>
                blendedDatum.joinId && onChange(value.id, blendedDatum.joinId, 'field2', value.datasourceAlias)
              }
            />
            <Icon
              type="delete"
              theme="filled"
              className="blend-data-picker__delete"
              onClick={() => deleteJoin(blendedDatum.joinId)}
            />
          </HStack>
        );
      })}
      <Button
        className="blend-data-picker__add-variable"
        style={{ paddingLeft: 0 }}
        type="link"
        icon="plus"
        onClick={() => {
          setBlendedData([...(blendedData || []), { joinId: ObjectID.generate(), joinType: 'Inner' }]);
        }}>
        <TranslatedText text="add_another_match" />
      </Button>
    </Stack>
  );
};

export const BlendDataPicker = withI18n(BlendDataPickerComponent, 'Dataset');

// Fill in the fieldId for all the fields in the join
const fixJoinFields = (joins: Joins, dataSourceFields: Record<string, DataSourceFields[]>) => {
  return {
    ...joins,
    field1: fixDatasetFieldId(joins.field1, dataSourceFields),
    field2: fixDatasetFieldId(joins.field2, dataSourceFields),
  };
};

// Set the fieldId if empty of a dataset field
const fixDatasetFieldId = (datasetField?: DatasetFields, dataSourceFields?: Record<string, DataSourceFields[]>) => {
  if (!datasetField) {
    return;
  }
  if (!dataSourceFields) {
    return datasetField;
  }

  let { fieldId, datasetId, apiName, datasourceAlias } = datasetField;
  if (!fieldId && datasetId && apiName) {
    const fields = flatDataSourceFields(dataSourceFields);

    const datasetField = lookupByDatasetIdAndApiNameAndAlias(datasetId, apiName, fields, datasourceAlias);
    if (datasetField) {
      return {
        ...datasetField,
        fieldId: datasetField.fieldId,
      };
    }
  }
  return datasetField;
};
