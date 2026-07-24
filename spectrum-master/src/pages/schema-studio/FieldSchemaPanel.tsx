//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Modal } from 'antd';
import Select from 'antd/lib/select';
import { memo, useEffect, useMemo, useState } from 'react';
import { connect, ConnectedProps } from 'react-redux';

import DrawerPanel from 'components/DrawerPanel';
import Fieldset from 'components/Fieldset';
import FieldTypeBadge, { DataTypeIcons } from 'components/FieldTypeBadge';
import InputWithLabel from 'components/inputs/InputWithLabel';
import PropertyPanelAction from 'components/PropertyPanelAction';
import { ScrollableArea } from 'components/scrollable-area/ScrollableArea';
import { FieldDataType } from 'components/types';
import { useEnhancedSelector } from 'hooks/redux';
import useToastForFetchStatusChange from 'hooks/useToastForFetchStatusChange';
import { useSynapseRefreshingStatus } from 'store/connectors';
import { useDeleteSchemaField } from 'store/schema';
import { selectEntitySchema } from 'store/schema/selectors';
import { Connector } from 'store/schema/types';
import AppConstants from 'utils/AppConstants';
import { tc, tNamespaced } from 'utils/i18nUtil';

import { RootState } from '../../reducers';
import CompositeKeySelector from './CompositeKeySelector';
import DataTypeInputs from './DataTypeInputs';
import { ALLOWED_ID_DATATYPE, ALLOWED_LENGTH_DATATYPE } from './FieldSchemaModal';
import { useFieldSchema } from './SchemaStudio.hooks';
import { FieldModel, isComplexDataType } from './types';

import './FieldSchemaPanel.less';

const { Option } = Select;

const INPUT_TYPE = AppConstants.INPUT_TYPE;
const tn = tNamespaced('FieldSchemaPanel');
const tnm = tNamespaced('FieldSchemaModal');
const td = tNamespaced('DataTypes');

const connector = connect((state: RootState, props: FieldSchemaPanelProps) => ({
  entitySchema: selectEntitySchema(state, props),
}));

interface FieldSchemaPanelProps {
  /**
   * Id of the connector which this field belongs to
   */
  connectorId?: string;
  /**
   * Id of the entity which this field belongs to
   */
  entityId: string;
  /**
   * Field model that is selected from the table
   */
  field?: FieldModel & { hasDraft?: boolean };
  /**
   * Handler for editing the field
   */
  editField: (field: FieldModel) => void;
  /**
   * Callback when this modal is closed
   */
  onClose?: () => void;
  /**
   * Flag if we are editing or adding a syncari field for a syncari connector
   */
  isSyncariConnector: boolean;
  synapse?: Connector;
}

type PropsFromRedux = ConnectedProps<typeof connector>;

const FieldSchemaPanel = memo(
  ({
    connectorId,
    entityId,
    field,
    onClose,
    editField,
    isSyncariConnector,
    entitySchema,
    synapse,
  }: FieldSchemaPanelProps & PropsFromRedux) => {
    const [visible, setVisible] = useState(false);

    const { deleteSchemaField, deleteStatus, deleteError } = useDeleteSchemaField();

    const {
      isDynamoDb,
      multiValueFieldEnabled,
      showCompositeKey,
      showUnique,
      parentAttributeSupported,
      userEditableReadOnly,
    } = useFieldSchema(synapse, field?.isSyncariDefined);

    const { entities } = useEnhancedSelector((state) => state.entity);

    const currentEntity = entities?.find((entity) => entity.id === entityId);
    // @ts-ignore
    const isReadonly = currentEntity?.readonly ?? false;

    useEffect(() => {
      setVisible(!!field);
    }, [field]);

    useToastForFetchStatusChange(deleteStatus, {
      success: tn('successfully_deleted_field'),
      error: deleteError,
    });

    const close = () => {
      setVisible(false);
      onClose && onClose();
    };

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

    const { isRefreshing } = useSynapseRefreshingStatus(connectorId);

    const editActionDisable = useMemo(() => {
      let disabled = false;
      let disabledMessage = '';

      if (isRefreshing) {
        disabled = true;
        disabledMessage = tc('unavailable_during_refresh');
      }

      if (!field?.hasDraft) {
        disabled = true;
        disabledMessage = tn('approved_field_cannot_modify');
      }

      // Override: Disable edit for syncari watermark and id fields. Its Syncari managed fields.
      if (!disabled && !field?.schemaUpdatable && isSyncariConnector && field?.isSystem) {
        disabled = true;
        disabledMessage = tn('disabled_edit_syncari_id_watermark');
      }

      return {
        disabled,
        disabledMessage,
      };
    }, [field?.hasDraft, field?.isSystem, field?.schemaUpdatable, isRefreshing, isSyncariConnector]);

    const deleteActionDisable = useMemo(() => {
      let disabled = !isSyncariConnector && !field?.isSyncariDefined && !field?.schemaDeletable;
      let disabledMessage = tn('disabled_delete_field_message');

      if (isRefreshing) {
        disabled = true;
        disabledMessage = tc('unavailable_during_refresh');
      }

      if (!field?.hasDraft) {
        disabled = true;
        disabledMessage = tn('approved_field_cannot_modify');
      }

      return {
        disabled,
        disabledMessage,
      };
    }, [field, isRefreshing, isSyncariConnector]);

    return (
      <DrawerPanel mask={false} onClose={close} title={field?.displayName || ''} visible={visible} useLandingZone>
        <div className="h-full">
          <PropertyPanelAction
            actions={[
              {
                name: tn('edit_field') as string,
                id: 'edit',
                icon: 'edit',
                disabled: editActionDisable.disabled,
                disabledMessage: editActionDisable.disabledMessage,
                handler: () => {
                  field && editField(field);
                },
              },
              {
                name: tn('delete_field') as string,
                id: 'delete',
                icon: 'delete',
                disabled: deleteActionDisable.disabled,
                disabledMessage: deleteActionDisable.disabledMessage,
                handler: () => {
                  Modal.confirm({
                    title: tn('delete_field'),
                    content: tn('confirm_delete_field_message', { fieldName: field?.displayName }),
                    onOk: () =>
                      field &&
                      deleteSchemaField({ entityId, fieldId: field.id }).then((res) => {
                        if (res.meta.requestStatus === 'fulfilled') {
                          // The setTimeout gives a moment for the modals to close
                          // before closing the drawer. Otherwise the screen
                          // shifts off the viewport.
                          setTimeout(close, 200);
                        }
                      }),
                    okText: tc('delete'),
                    okType: 'danger',
                  });
                },
              },
            ]}
          />
          <Fieldset className="synri-entity-property-fieldset" title="Field">
            <ScrollableArea>
              <InputWithLabel
                label={tnm('display_name')}
                datatype={INPUT_TYPE.STRING}
                value={field?.displayName}
                disabled
              />
              <InputWithLabel label={tnm('api_name')} datatype={INPUT_TYPE.STRING} value={field?.apiName} disabled />
              <InputWithLabel
                label={tnm('data_store_name')}
                datatype={INPUT_TYPE.STRING}
                value={field?.dataStoreName}
                disabled
              />
              <InputWithLabel
                label={tnm('description')}
                datatype={INPUT_TYPE.TEXTAREA}
                value={field?.description}
                disabled
              />
              <InputWithLabel
                label={tnm('data_type')}
                datatype={INPUT_TYPE.PICKLIST}
                value={field?.dataType}
                options={Object.keys(DataTypeIcons).map((key: string) => (
                  <Option value={key} key={key}>
                    <div className="field-schema-panel__datatype-option">
                      <FieldTypeBadge dataType={key as FieldDataType} description={td(key)} disableTooltip />
                      <span>{td(key)}</span>
                    </div>
                  </Option>
                ))}
                disabled
              />
              <DataTypeInputs values={field} datatype={field?.dataType} disabled />

              {(isSyncariConnector || field?.isSyncariDefined) &&
                field?.dataType &&
                ALLOWED_LENGTH_DATATYPE.includes(field.dataType) && (
                  <InputWithLabel
                    name="length"
                    label={tn('length')}
                    datatype={INPUT_TYPE.INTEGER}
                    disabled
                    value={field?.length}
                  />
                )}

              {parentAttributeSupported && (
                <InputWithLabel
                  label={tnm('parent_attribute')}
                  datatype={INPUT_TYPE.PICKLIST}
                  optionData={parentAttributeList}
                  value={field?.parentAttributeId}
                  disabled
                />
              )}
              {field?.isSyncariDefined && (
                <InputWithLabel
                  name="required"
                  label={tnm('syncari_field')}
                  checked
                  datatype={INPUT_TYPE.CHECKBOX}
                  disabled
                />
              )}
              {multiValueFieldEnabled && (
                <InputWithLabel
                  name="multiValueField"
                  label={tc('multi_value_field')}
                  datatype={INPUT_TYPE.CHECKBOX}
                  checked={field?.isMultiValueField}
                  disabled
                />
              )}

              {/* TODO: Make the DataType accessible, not just types */}
              {(!field?.isSyncariDefined || isDynamoDb) && (
                <>
                  {field?.dataType && ALLOWED_ID_DATATYPE.includes(field.dataType) && (
                    <>
                      <InputWithLabel
                        name="idField"
                        label={tnm('id_field')}
                        checked={field?.isIdField}
                        datatype={INPUT_TYPE.CHECKBOX}
                        disabled
                      />
                      {/* If a field is the ID field for DynamoDB show the composite key selector as well */}
                      {field?.compositeKey && showCompositeKey && (
                        <CompositeKeySelector
                          fieldId={field?.id}
                          entityId={entityId}
                          value={field?.compositeKey}
                          disabled
                        />
                      )}
                    </>
                  )}
                  <InputWithLabel
                    name="required"
                    label={tnm('required')}
                    checked={field?.isRequired}
                    datatype={INPUT_TYPE.CHECKBOX}
                    disabled
                  />
                  {showUnique && (
                    <InputWithLabel
                      name="unique"
                      label={tnm('unique')}
                      checked={field?.isUnique}
                      datatype={INPUT_TYPE.CHECKBOX}
                      disabled
                    />
                  )}
                </>
              )}
              {userEditableReadOnly && (
                <InputWithLabel
                  name="readonly"
                  label={tnm('read_only')}
                  checked={field?.isReadonly}
                  datatype={INPUT_TYPE.CHECKBOX}
                  disabled
                />
              )}
              {(!isSyncariConnector || isReadonly) && (
                <InputWithLabel
                  name="watermarkField"
                  label={tnm('watermark_field')}
                  checked={field?.isWatermarkField}
                  datatype={INPUT_TYPE.CHECKBOX}
                  disabled
                />
              )}
              <InputWithLabel label={tnm('tags')} datatype={INPUT_TYPE.TAG} defaultValue={field?.tags} disabled />
            </ScrollableArea>
          </Fieldset>
        </div>
      </DrawerPanel>
    );
  }
);

export default connector(FieldSchemaPanel);
