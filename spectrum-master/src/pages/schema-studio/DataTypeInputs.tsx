//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { useEffect, useState } from 'react';
import { connect } from 'react-redux';
import { bindActionCreators, Dispatch } from 'redux';

import InputWithLabel from 'components/inputs/InputWithLabel';
import MultiValues from 'components/inputs/MultiValues';
import { getDervEntitiesWithFieldDraftSummary } from 'selectors/entitySelectors';
import { RootState } from 'store/types';
import AppConstants from 'utils/AppConstants';
import { tNamespaced } from 'utils/i18nUtil';

import { FieldValues } from './FieldSchemaModal';
import { FieldModel } from './types';

const tn = tNamespaced('DataTypeInputs');

const { REFERENCE, PICKLIST } = AppConstants.INPUT_TYPE;

interface DataTypeInputProps {
  datatype?: string;
  syncariSchema?: SyncariEntitySchema[];
  onChange?: (name: string, value: string | string[]) => void;
  values?: FieldValues | FieldModel;
  disabled?: boolean;
  entityId?: string;
  children?: React.ReactNode;
}

interface SyncariEntitySchema {
  id: string;
  displayName: string;
  apiName: string;
  fields: SyncariFieldSchema[];
}

interface SyncariFieldSchema {
  displayName: string;
  apiName: string;
}

interface FieldListSelectValues {
  label: string;
  value: string;
  title: string;
}

interface EntitySelectValues extends FieldListSelectValues {
  fields?: SyncariFieldSchema[];
}

const DataTypeInputs = ({
  datatype,
  syncariSchema,
  children,
  onChange,
  values: fieldValues,
  disabled,
  entityId,
  ...rest
}: DataTypeInputProps) => {
  const [selectedEntity, setSelectedEntity] = useState<string | null>();
  const [entityList, setEntityList] = useState<EntitySelectValues[]>([]);
  const [fieldList, setFieldList] = useState<FieldListSelectValues[]>();
  const [values, setValues] = useState<FieldValues>();

  useEffect(() => {
    setValues({
      ...values,
      picklistValues: fieldValues?.picklistValues,
      referenceTo: fieldValues?.referenceTo,
      referenceTargetField: fieldValues?.referenceTargetField,
    });

    if (fieldValues?.referenceTo) {
      setSelectedEntity(fieldValues.referenceTo);
    }
    // We are only updating our local values when the external values changes.
    // like when the user select a different field
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fieldValues]);

  useEffect(() => {
    if (syncariSchema) {
      setEntityList(
        syncariSchema.map((entity: SyncariEntitySchema) => {
          return {
            title: entity.displayName,
            label: entity.displayName,
            value: entity.apiName,
            fields: entity.fields,
          };
        })
      );
    }
  }, [syncariSchema]);

  useEffect(() => {
    if (selectedEntity && entityList) {
      const foundEntity = entityList.find((entity) => entity.value === selectedEntity);
      if (foundEntity) {
        const myFieldList = foundEntity?.fields?.map((field) => {
          return {
            title: field.displayName,
            label: field.displayName,
            value: field.apiName,
          };
        });
        if (myFieldList) {
          setFieldList(myFieldList);
        }
      }
    }
  }, [selectedEntity, entityList]);

  const onPicklistChange = (name: string, value: string | string[]) => {
    setValues({
      ...values,
      [name]: value,
    });
    if (typeof value === 'string' && name === 'referenceTo') {
      setSelectedEntity(value);
    }
    onChange && onChange(name, value);
  };

  return (
    <>
      {datatype === REFERENCE && (
        <>
          <InputWithLabel
            label={tn('entity')}
            datatype={PICKLIST}
            value={fieldValues?.referenceTo}
            onChange={onPicklistChange.bind(null, 'referenceTo')}
            optionData={entityList}
            disabled={disabled}
          />
          <InputWithLabel
            label={tn('field')}
            datatype={PICKLIST}
            value={fieldValues?.referenceTargetField}
            onChange={onPicklistChange.bind(null, 'referenceTargetField')}
            optionData={fieldList}
            disabled={disabled}
          />
        </>
      )}
      {datatype === PICKLIST && (
        <>
          <MultiValues
            label={tn('picklist_values') as string}
            name="values"
            disabled={disabled}
            vals={fieldValues?.picklistValues as string[]}
            onChange={(values: string[]) => {
              onPicklistChange('values', values);
            }}
          />
        </>
      )}
    </>
  );
};

export default connect<{}, {}, DataTypeInputProps, RootState>(
  (state, props) => ({
    // We are using the approved version of the syncari schema.
    // Easier to grok the list for this purpose from the schema api
    syncariSchema: getDervEntitiesWithFieldDraftSummary(state),
  }),
  (dispatch: Dispatch) => {
    return bindActionCreators({}, dispatch);
  }
)(DataTypeInputs);
