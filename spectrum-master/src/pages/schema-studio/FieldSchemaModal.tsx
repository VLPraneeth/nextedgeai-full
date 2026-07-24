//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Button } from 'antd';
import Select, { OptionProps } from 'antd/lib/select';
import * as React from 'react';
import { useEffect, useMemo, useRef, useState } from 'react';
import { connect, ConnectedProps } from 'react-redux';
import { bindActionCreators, Dispatch } from 'redux';

import DrawerPanel from 'components/DrawerPanel';
import FieldTypeBadge, { DataTypeIcons } from 'components/FieldTypeBadge';
import InlineMessage, { Types as InlineMessageTypes } from 'components/InlineMessage';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { ValidateStatuses } from 'components/inputs/types';
import { FieldDataType } from 'components/types';
import { resetFieldModal } from 'store/schema/actions';
import { selectEntitySchema } from 'store/schema/selectors';
import { saveField } from 'store/schema/thunks';
import { Connector } from 'store/schema/types';
import { RootState } from 'store/types';
import AppConstants from 'utils/AppConstants';
import { tc, tNamespaced } from 'utils/i18nUtil';
import { createApiName, schemaApiNameRegex } from 'utils/StringUtil';

import CompositeKeySelector from './CompositeKeySelector';
import DataTypeInputs from './DataTypeInputs';
import { useFieldSchema } from './SchemaStudio.hooks';
import {
  ConnectorSchema,
  FieldModel,
  isComplexDataType,
  isDisallowedDataType,
  isDisallowedSyncariDataType,
  VersionedSchemaData,
} from './types';

import './FieldSchemaPanel.less';

const { Option } = Select;
const { INPUT_TYPE } = AppConstants;

const ALLOWED_WATERMARK_DATATYPE = ['datetime', 'timestamp', 'integer', 'long', 'date'];

export const ALLOWED_ID_DATATYPE = ['id', 'date', 'string', 'datetime', 'timestamp', 'double', 'url', 'integer'];
export const ALLOWED_LENGTH_DATATYPE = ['string', 'reference', 'textarea', 'url', 'picklist'];
const WATERMARK_FIELD_NAME = 'watermarkField';
const DATA_TYPE_FIELD_NAME = 'dataType';
const ID_FIELD = 'idField';

// TODO: Update Arcade so GET/POST use consistent attribute names
//
// extend FieldModel with the field names that have a descrepency with the GET/POST endpoints
interface ArcadeFieldDescrepencies {
  idField?: FieldModel['isIdField'];
  required?: FieldModel['isRequired'];
  unique?: FieldModel['isUnique'];
  readOnly?: FieldModel['isReadonly'];
  watermarkField?: FieldModel['isWatermarkField'];
  multiValueField?: FieldModel['isMultiValueField'];
  parentAttributeId?: FieldModel['parentAttributeId'];
  isSyncariDefined?: FieldModel['isSyncariDefined'];
  dataStoreName?: FieldModel['dataStoreName'];
}

export type FieldValues = Partial<FieldModel> & ArcadeFieldDescrepencies;

interface FieldSchemaModalProps {
  /**
   * Entity id of the field that it belongs to
   */
  entityId: string;

  entity: VersionedSchemaData<ConnectorSchema> | null;
  /**
   * Field that we will be editing
   */
  field?: FieldModel;
  /**
   * Additional classname to be added
   */
  className?: string;
  /**
   * Callback when the modal is closed
   */
  onClose?: () => void;
  /**
   * Handler to clear the redux states of the modal
   */
  resetFieldModal?: () => void;
  /**
   * Make this modal visible or not
   */
  visible?: boolean;
  /**
   * Flag if we are editing or adding a syncari field for a syncari connector
   */
  isSyncariConnector: boolean;
  synapse?: Connector;
}

interface FieldModalValidation {
  /**
   * Status of api name validation
   */
  apiNameValidateStatus?: ValidateStatuses;
  /**
   * Help message to display on the field
   */
  apiNameHelp?: string;
  dataTypeValidateStatus?: ValidateStatuses;
  dataTypeHelp?: string;
}

const tn = tNamespaced('FieldSchemaModal');
const td = tNamespaced('DataTypes');

const connector = connect(
  (state: RootState, props: any) => ({
    saveFieldErrorMessage: state.schema.saveFieldErrorMessage as string | undefined,
    saveFieldStatus: state.schema.saveFieldStatus as string | undefined,
    entitySchema: selectEntitySchema(state, props),
  }),
  (dispatch: Dispatch) =>
    bindActionCreators(
      {
        saveField,
        resetFieldModal,
      },
      dispatch
    )
);

type PropsFromRedux = ConnectedProps<typeof connector>;

const FieldSchemaModal = ({
  entityId,
  field,
  onClose,
  saveField,
  saveFieldErrorMessage,
  saveFieldStatus,
  resetFieldModal,
  entitySchema,
  isSyncariConnector,
  visible = false,
  synapse,
}: FieldSchemaModalProps & PropsFromRedux) => {
  const [values, setValues] = useState<FieldValues>();
  const [validation, setValidation] = useState<FieldModalValidation>({});
  const initialSaveFieldStatus = useRef<string | undefined>();
  const [watermarkDisabled, setWatermarkDisabled] = useState(true);
  const [disabledInputs, setDisabledInputs] = useState(false);
  const saving = saveFieldStatus === AppConstants.FETCH_STATUS.LOADING;

  const {
    parentAttributeSupported,
    showCompositeKey,
    showUnique,
    multiValueFieldEnabled,
    userEditableId,
    userEditableWm,
    //remove condition to support non syncari synapse
    userEditableReadOnly,
  } = useFieldSchema(synapse, field?.isSyncariDefined);

  useEffect(() => {
    // Only set the values when the user want to see the form
    if (!visible) {
      return;
    }
    if (field) {
      // We handpick items that are editable
      // We also mapped the names since some of the update attribute
      // have different property name :(
      setValues({
        apiName: field.apiName,
        displayName: field.displayName,
        description: field.description,
        compositeKey: field.compositeKey,
        dataType: field.dataType,
        tags: field.tags,
        idField: field.isIdField,
        required: field.isRequired,
        multiValueField: field.isMultiValueField,
        length: field.length,
        unique: field.isUnique,
        watermarkField: field.isWatermarkField,
        readOnly: field.isReadonly,
        picklistValues: field.picklistValues,
        referenceTo: field.referenceTo,
        referenceTargetField: field.referenceTargetField,
        parentAttributeId: field.parentAttributeId,
        isSyncariDefined: field.isSyncariDefined,
        dataStoreName: field.dataStoreName,
      });

      if (!isSyncariConnector) {
        // Enable inputs if its a syncari defined/user created attribute
        const canEditInputs = field.isSyncariDefined || userEditableId || userEditableWm;
        setDisabledInputs(!canEditInputs);
      }
    } else {
      // Always set the syncari defined for synapse fields.
      // The user are not allowed to change this, at least for now...
      setValues({
        isSyncariDefined: !isSyncariConnector,
      });
      // Make all the field editable when creating
      // a new syncari or synapse fields
      setDisabledInputs(false);
    }

    if (field) {
      setWatermarkDisabled(!ALLOWED_WATERMARK_DATATYPE.includes(field.dataType));
    }
  }, [field, isSyncariConnector, userEditableId, userEditableWm, visible]);

  useEffect(() => {
    if (initialSaveFieldStatus?.current !== saveFieldStatus) {
      if (
        initialSaveFieldStatus.current === AppConstants.FETCH_STATUS.LOADING &&
        saveFieldStatus === AppConstants.FETCH_STATUS.SUCCESS
      ) {
        onClose && onClose();
      }
      initialSaveFieldStatus.current = saveFieldStatus;
    }
  }, [saveFieldStatus, onClose]);

  const parentAttributeList = useMemo(() => {
    return entitySchema?.data
      ?.filter((datum) => datum.published?.fields && isComplexDataType(datum.published?.fields.dataType))
      .map((datum) => {
        return {
          title: datum.published.fields.displayName,
          label: datum.published.fields.displayName,
          value: datum.published.fields.id,
        };
      })
      .filter(Boolean);
  }, [entitySchema]);

  const change = (evt: React.ChangeEvent<HTMLInputElement>) => {
    const isWatermarkField = evt.target.name === WATERMARK_FIELD_NAME;
    const watermarkFieldChecked = isWatermarkField && evt.target.checked;
    let newValues = {
      ...values,
      // Automatically check the required and readonly if the watermark field is checked
      required: watermarkFieldChecked ? true : values?.required,
      readOnly: watermarkFieldChecked ? true : values?.readOnly,
      [evt.target.name]: evt.target.type === AppConstants.INPUT_TYPE.CHECKBOX ? evt.target.checked : evt.target.value,
    };
    if (evt.target.name === ID_FIELD && evt.target.checked) {
      // Automatically make id fields unique, readonly and required
      // otherwise the backend validation will throw an error.
      newValues = {
        ...newValues,
        unique: true,
        readOnly: true,
        required: true,
      };
    }
    setValues(newValues);
  };

  const onPicklistChange = (name: string, value: string | string[]) => {
    const isDatatypeChange = name === DATA_TYPE_FIELD_NAME && typeof value === 'string';
    const nonWatermarkDatatypeSelected = isDatatypeChange && !ALLOWED_WATERMARK_DATATYPE.includes(value);
    const nonLengthDatatypeSelected = isDatatypeChange && !ALLOWED_LENGTH_DATATYPE.includes(value);
    let newValues = {
      ...values,
      [name]: value,
    };
    setWatermarkDisabled(nonWatermarkDatatypeSelected);
    if (nonWatermarkDatatypeSelected) {
      newValues = {
        ...newValues,
        watermarkField: false,
      };
    }
    if (nonLengthDatatypeSelected && newValues.length) {
      // clear length requirement for datatypes that should not support it
      newValues.length = 0;
    }
    setValues(newValues);
  };

  const onTagChange = (tags: string[]) => {
    setValues({
      ...values,
      tags,
    });
  };

  const onCompositeKeyChange = (compositeKey?: string) => {
    setValues((prev) => ({ ...prev, compositeKey }));
  };

  const onCancel = () => {
    onClose && onClose();
    resetFieldModal && resetFieldModal();
  };

  const validate = () => {
    if (!values?.apiName) {
      setValidation({
        apiNameValidateStatus: ValidateStatuses.ERROR,
        apiNameHelp: tn('api_name_not_empty'),
      });
      return false;
    } else if (values?.apiName.match(/[^a-zA-Z\d\+_:-]/)) {
      setValidation({
        apiNameValidateStatus: ValidateStatuses.ERROR,
        apiNameHelp: tn('api_name_alpha_numeric'),
      });
      return false;
    } else if (!values?.dataType) {
      setValidation({
        dataTypeValidateStatus: ValidateStatuses.ERROR,
        dataTypeHelp: tn('data_type_not_empty'),
      });
      return false;
    }
    setValidation({
      apiNameValidateStatus: ValidateStatuses.BLANK,
      apiNameHelp: '',
    });
    return true;
  };

  const save = () => {
    if (validate() && values) {
      // @ts-expect-error: Id will not be empty at this point
      saveField && saveField(values, { refresh: true, fieldId: field?.id, entityId });
    }
  };

  const filterOption = (input: string, option: React.ReactElement<OptionProps>) => {
    return option?.props?.value ? option.props.value.toString().toLowerCase().indexOf(input.toLowerCase()) >= 0 : false;
  };

  const apiNameFieldDisableld = disabledInputs || Boolean(field);

  const isNewField = !field?.displayName;

  return (
    <DrawerPanel
      className="synri-field-schema-modal"
      mask
      maskClosable
      destroyOnClose
      onClose={onCancel}
      title={isNewField ? tn('new_field') : field?.displayName}
      visible={visible}
      width="large"
      footer={
        <>
          <Button onClick={onCancel}>{tc('cancel')}</Button>
          <Button onClick={save} type="primary" disabled={saving}>
            {saving ? tc('saving') : isNewField ? tc('create') : tc('save')}
          </Button>
        </>
      }>
      {saveFieldErrorMessage && (
        <InlineMessage type={InlineMessageTypes.ERROR} title={saveFieldErrorMessage}>
          {saveFieldErrorMessage}
        </InlineMessage>
      )}
      <div className="synri-input-container">
        <InputWithLabel
          name="displayName"
          label={tn('display_name')}
          value={values?.displayName}
          datatype={INPUT_TYPE.STRING}
          disabled={disabledInputs}
          data-testid="field-display-name"
          onChange={change}
          onBlur={() => {
            if (values?.displayName) {
              const updates: Partial<FieldValues> = {};
              const apiNameFromDisplayName = createApiName(values.displayName, schemaApiNameRegex);

              // Automatically pre-fill the apiName based on the display name
              if (!apiNameFieldDisableld && !values?.apiName) {
                updates.apiName = apiNameFromDisplayName;
              }

              // Automatically pre-fill the apiName based on the display name
              if (!disabledInputs && !values?.dataStoreName) {
                updates.dataStoreName = apiNameFromDisplayName;
              }
              setValues({ ...values, ...updates });
            }
          }}
        />
        <InputWithLabel
          name="apiName"
          label={tn('api_name')}
          datatype={INPUT_TYPE.STRING}
          validateStatus={validation?.apiNameValidateStatus}
          help={validation?.apiNameHelp}
          disabled={apiNameFieldDisableld}
          data-testid="field-api-name"
          value={values?.apiName}
          onChange={change}
        />
        <InputWithLabel
          name="dataStoreName"
          label={tn('data_store_name')}
          value={values?.dataStoreName}
          datatype={INPUT_TYPE.STRING}
          disabled={disabledInputs}
          onChange={change}
          help={disabledInputs || !values?.dataStoreName ? '' : tn('data_store_name_warning')}
        />
        <InputWithLabel
          name="description"
          label={tn('description')}
          value={values?.description}
          datatype={INPUT_TYPE.TEXTAREA}
          disabled={disabledInputs}
          onChange={change}
        />
        <InputWithLabel
          label={tn('data_type')}
          name={DATA_TYPE_FIELD_NAME}
          className="data-type"
          validateStatus={validation?.dataTypeValidateStatus}
          help={validation?.dataTypeHelp}
          datatype={INPUT_TYPE.PICKLIST}
          disabled={disabledInputs}
          onChange={onPicklistChange.bind(null, DATA_TYPE_FIELD_NAME)}
          // Show only child for synapse entities, otherwise show everything
          options={Object.keys(DataTypeIcons)
            .filter((datatype) => {
              // List field is deprecated. It should not be allowed when creating new fields.
              if (isNewField && datatype === 'list') {
                return false;
              }
              return (isSyncariConnector && !isDisallowedSyncariDataType(datatype)) || !isDisallowedDataType(datatype);
            })
            .map((datatype) => (
              <Option value={datatype} key={datatype}>
                <div className="field-schema-panel__datatype-option">
                  <FieldTypeBadge dataType={datatype as FieldDataType} description={td(datatype)} disableTooltip />
                  <span>{td(datatype)}</span>
                </div>
              </Option>
            ))}
          value={values?.dataType}
          filterOption={filterOption}
        />

        {(isSyncariConnector || values?.isSyncariDefined) &&
          values?.dataType &&
          ALLOWED_LENGTH_DATATYPE.includes(values.dataType) && (
            <InputWithLabel
              name="length"
              label={tn('length')}
              datatype={INPUT_TYPE.INTEGER}
              disabled={disabledInputs}
              onChange={change}
              value={values?.length}
            />
          )}
        {multiValueFieldEnabled && (
          <InputWithLabel
            name="multiValueField"
            label={tc('multi_value_field')}
            datatype={INPUT_TYPE.CHECKBOX}
            disabled={disabledInputs}
            onChange={change}
            checked={values?.multiValueField}
          />
        )}
        <DataTypeInputs
          onChange={onPicklistChange}
          disabled={disabledInputs}
          values={values}
          datatype={values?.dataType}
          entityId={entityId}
        />

        {parentAttributeSupported && (
          <InputWithLabel
            label={tn('parent_attribute')}
            datatype={INPUT_TYPE.PICKLIST}
            disabled={disabledInputs}
            onChange={onPicklistChange.bind(null, 'parentAttributeId')}
            optionData={parentAttributeList}
            value={values?.parentAttributeId}
          />
        )}
        {values?.dataType && ALLOWED_ID_DATATYPE.includes(values.dataType) && (
          <>
            {/* Hiding the idField because its autogenerated on Syncari entities */}
            {!isSyncariConnector && (
              <InputWithLabel
                name={ID_FIELD}
                label={tn('id_field')}
                checked={values?.idField}
                datatype={INPUT_TYPE.CHECKBOX}
                disabled={!userEditableId || disabledInputs}
                onChange={change}
              />
            )}
            {/* If a field is the ID field for DynamoDB show the composite key selector as well */}
            {values?.idField && showCompositeKey && (
              <CompositeKeySelector
                fieldId={field?.id}
                entityId={entityId}
                onChange={onCompositeKeyChange}
                value={values?.compositeKey}
              />
            )}
          </>
        )}
        {/* TODO: Make the DataType accessible, not just types */}
        {values?.isSyncariDefined && (
          <>
            <InputWithLabel
              label={tn('syncari_field')}
              checked
              datatype={INPUT_TYPE.CHECKBOX}
              disabled
              value={values?.isSyncariDefined}
            />
            <InputWithLabel
              name="required"
              label={tn('required')}
              checked={values?.required}
              datatype={INPUT_TYPE.CHECKBOX}
              // If this field is an ID field the required box cannot be changed
              disabled={values?.idField || disabledInputs}
              onChange={change}
            />
            {/* Unique is required for the DynamoDB id field. */}
            {showUnique && (
              <InputWithLabel
                name="unique"
                label={tn('unique')}
                checked={values?.unique}
                datatype={INPUT_TYPE.CHECKBOX}
                disabled={disabledInputs}
                onChange={change}
              />
            )}
          </>
        )}

        <InputWithLabel
          name="readOnly"
          label={tn('read_only')}
          checked={values?.readOnly}
          datatype={INPUT_TYPE.CHECKBOX}
          disabled={disabledInputs}
          onChange={change}
        />
        {!values?.isSyncariDefined && (
          <>
            <InputWithLabel
              name="required"
              label={tn('required')}
              checked={values?.required}
              datatype={INPUT_TYPE.CHECKBOX}
              // Required is always true (and disabled) if the field is an id field
              disabled={values?.idField || disabledInputs}
              onChange={change}
            />
            {showUnique && (
              <InputWithLabel
                name="unique"
                label={tn('unique')}
                checked={values?.unique}
                datatype={INPUT_TYPE.CHECKBOX}
                disabled={disabledInputs}
                onChange={change}
              />
            )}
            {!isSyncariConnector && (
              <InputWithLabel
                name="readOnly"
                label={tn('read_only')}
                checked={values?.readOnly}
                datatype={INPUT_TYPE.CHECKBOX}
                disabled={disabledInputs}
                onChange={change}
              />
            )}
          </>
        )}
        {!isSyncariConnector && (
          <InputWithLabel
            name={WATERMARK_FIELD_NAME}
            label={tn('watermark_field')}
            checked={values?.watermarkField}
            datatype={INPUT_TYPE.CHECKBOX}
            disabled={!userEditableWm || watermarkDisabled || disabledInputs}
            onChange={change}
          />
        )}
        <InputWithLabel
          label={tn('tags')}
          datatype="tag"
          disabled={disabledInputs}
          defaultValue={values?.tags}
          onChange={onTagChange}
        />
      </div>
    </DrawerPanel>
  );
};

export default connector(FieldSchemaModal);
