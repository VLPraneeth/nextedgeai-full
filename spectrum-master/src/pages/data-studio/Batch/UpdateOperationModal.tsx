import Icon from 'antd/lib/icon';
import message from 'antd/lib/message';
import cx from 'classnames';
import { keyBy } from 'lodash/fp';
import { useCallback, useMemo, useState, useRef } from 'react';
import * as React from 'react';
import { useDispatch } from 'react-redux';

import { ReactComponent as TrashIcon } from 'assets/icons/Trash.svg';
import Button, { IconButton } from 'components/Button';
import FieldTypeBadge from 'components/FieldTypeBadge';
import { useI18nContext } from 'components/I18nProvider';
import InlineMessage from 'components/InlineMessage';
import InputContainer from 'components/inputs/InputContainer';
import { HStack, Stack } from 'components/layout';
import Select from 'components/SelectInput';
import { TranslatedText, Truncation, Text } from 'components/typography';
import { useUpdateRecordsMutation, util as dataStudioBatchApiUtil } from 'store/data-studio-batch';
import { BatchOperation } from 'store/data-studio-batch/types';
import { EntityField } from 'store/entity/types';
import AppConstants from 'utils/AppConstants';
import { replaceItem } from 'utils/ArrayUtil';
import { packageData } from 'utils/ErrorUtils';
import { wrapIcon } from 'utils/IconUtils';

import EntityFilterPreview from './EntityFilterPreview';
import { EnhancedModal, useOperationModalContext } from './OperationModalProvider';
import { CommonOperationModalProps, FieldRowData, FieldOption } from './types';
import { UNSUPPORTED_DATATYPES, getFirstAvailableField } from './utils';

import './OperationModal.less';

export type UpdateOperationModalProps = CommonOperationModalProps;

const UpdateModal = ({ commonI18nArgs: i18nArgs = {}, entity, filter, fieldValues }: UpdateOperationModalProps) => {
  const addFieldButtonRef = useRef<HTMLDivElement | null>(null);
  const { closeModal } = useOperationModalContext();
  const { tn } = useI18nContext();
  const [updateRecords, { isLoading: isUpdating }] = useUpdateRecordsMutation();
  const [fieldData, setFieldData] = useState<FieldRowData[]>(() => []);
  const [formError, setFormError] = useState<null | string>(null);
  const dispatch = useDispatch();

  // quick lookup map of field data
  const entityFieldsMap = useMemo(() => keyBy('apiName', entity.fields), [entity.fields]);
  // these are the fields that have edited data so far
  const editedFieldKeys = useMemo(() => fieldData.map((f) => f.name), [fieldData]);

  // our select options, filtering out already selected fields and unsupported datatypes
  const fieldOptions: FieldOption[] = useMemo(() => {
    return entity.fields
      .filter((f) => !UNSUPPORTED_DATATYPES.includes(f.dataType) && !editedFieldKeys.includes(f.apiName))
      .map((field) => ({
        label: field.displayName,
        value: field.apiName,
        extraData: field,
      }));
  }, [entity.fields, editedFieldKeys]);

  const makeFieldOnChangeHandler = useCallback(
    (idx: number) => (name: string, value: unknown) =>
      setFieldData((prev) => {
        const previousFieldData = prev[idx];
        const previousField = entityFieldsMap[previousFieldData.name];
        const newField = entityFieldsMap[name];

        const newValue =
          // coerce to boolean if we're working with a boolean field
          newField.dataType === AppConstants.INPUT_TYPE.BOOLEAN
            ? Boolean(value)
            : // if the datatype has changed, reset the value
            previousField.dataType !== newField.dataType
            ? ''
            : value;

        return replaceItem(prev, idx, {
          name,
          value: newValue,
        });
      }),
    [entityFieldsMap]
  );

  const handleFieldDelete = useCallback((idx: number) => {
    setFieldData((prev) => prev.filter((_, fieldIdx) => idx !== fieldIdx));
  }, []);

  function handleAddField() {
    const firstAvailableField = getFirstAvailableField(entity.fields, editedFieldKeys);

    if (firstAvailableField) {
      setFieldData((prev) => [...prev, { name: firstAvailableField.apiName, value: '' }]);

      requestAnimationFrame(() => {
        // scroll the panel up to keep our button in view
        addFieldButtonRef.current?.scrollIntoView({ behavior: 'smooth' });
      });
    }
  }

  const handleRequestUpdate = async () => {
    setFormError(null);

    try {
      if (!filter?.criteria) {
        throw new Error(tn('filter_is_required'));
      }

      if (Object.values(fieldData).length < 1) {
        throw new Error(tn('no_field_updates_provided'));
      }

      const batch = await updateRecords({
        entityId: entity.id,
        predicate: packageData(filter.criteria),
        fields: Object.fromEntries(fieldData.map(({ name, value }) => [name, value])),
      }).unwrap();

      // batch should be guaranteed here, but let's be extra safe
      if (batch) {
        // optimistically update our general query as well as the UPDATE specific query
        dispatch(
          dataStudioBatchApiUtil.updateQueryResult('getBatchesForEntity', { entityId: entity.id }, (draft) => {
            draft.push(batch);
            return draft;
          })
        );

        dispatch(
          dataStudioBatchApiUtil.updateQueryResult(
            'getBatchesForEntity',
            { entityId: entity.id, operation: BatchOperation.UPDATE },
            (draft) => {
              draft.push(batch);
              return draft;
            }
          )
        );
        message.success(tn('update_request_successful', i18nArgs));
        closeModal();
      } else {
        throw new Error(tn('generic_error'));
      }
    } catch (err) {
      setFormError((err as Error)?.message || (err as any)?.data?.message || tn('generic_error'));
      return;
    }
  };

  return (
    <EnhancedModal
      onOk={handleRequestUpdate}
      title={tn('update_modal_title', i18nArgs)}
      okButtonProps={{
        title: tn('update_save_btn'),
        type: 'primary',
        disabled: isUpdating || fieldData.length < 1,
        loading: isUpdating,
      }}>
      <Stack>
        <InlineMessage className="synri-inline-message-no-bottom-margin" type="error">
          {formError && <Text>{formError}</Text>}
        </InlineMessage>
        <Stack divider spacing="lg">
          <Stack spacing="sm">
            <TranslatedText text="scope_title" weight="semibold" />
            {filter ? (
              [
                <TranslatedText key="label" beDangerous text="scope_with_filter" args={i18nArgs} />,
                <EntityFilterPreview key="filter" fieldValues={fieldValues} filter={filter} />,
              ]
            ) : (
              <TranslatedText beDangerous text="scope_without_filter" args={i18nArgs} />
            )}
          </Stack>

          <Stack className="record-update-fields-container">
            <TranslatedText text="fields_title" weight="semibold" />
            {fieldData.length < 1 ? (
              <TranslatedText text="no_fields_yet" />
            ) : (
              <div className="record-update-field-rows-container">
                {fieldData.map(({ name, value }, idx) => {
                  const field = entityFieldsMap[name];

                  return (
                    <FieldRow
                      key={name}
                      field={field}
                      value={value}
                      onChange={makeFieldOnChangeHandler(idx)}
                      onDelete={() => handleFieldDelete(idx)}
                      fieldOptions={fieldOptions}
                    />
                  );
                })}
              </div>
            )}
            <div ref={addFieldButtonRef}>
              <Button icon="plus" type="primary" onClick={handleAddField}>
                <TranslatedText namespace="Common" text="add" />
              </Button>
            </div>
          </Stack>
        </Stack>
      </Stack>
    </EnhancedModal>
  );
};

type FieldRowProps = {
  field: EntityField;
  fieldOptions: FieldOption[];
  value: unknown;
  onChange: (name: string, value: unknown) => void;
  onDelete: () => void;
};

const renderFieldOption = ({ extraData: field, label }: FieldOption) => {
  return (
    <HStack spacing="z">
      <FieldTypeBadge description={field.dataType} dataType={field.dataType} />
      <Truncation tooltipText={`${label} (${field.apiName})`}>
        <Text>{label}</Text>
        <Text color="gray-700">{` (${field.apiName})`}</Text>
      </Truncation>
    </HStack>
  );
};

const DeleteIcon = () => <Icon component={wrapIcon(TrashIcon)} />;

const FieldRow = ({ field, fieldOptions, value, onChange, onDelete }: FieldRowProps) => {
  const handleOnChange = useCallback(
    (evt: boolean | string | number | React.ChangeEvent<HTMLInputElement>) => {
      if (!field) {
        return;
      }
      if (typeof evt === 'boolean' || typeof evt === 'number' || typeof evt === 'string') {
        return onChange(field.apiName, evt);
      }

      onChange(field.apiName, evt.target.value);
    },
    [onChange, field]
  );

  const filterOption = useCallback(
    (inputValue: any, option: any) => option.props.title?.toLowerCase().includes(inputValue.toLowerCase()),
    []
  );

  return (
    <HStack className="record-update-field-row" align="start" justify="space-between">
      <HStack className="record-update-field-row-content" align="start">
        <Select<FieldOption>
          className="record-update-field-select"
          dropdownMatchSelectWidth={false}
          options={fieldOptions}
          showSearch
          filterOption={filterOption}
          value={field?.apiName}
          onChange={(name) => onChange(name, value)}
          renderOption={renderFieldOption}
        />
        {field &&
          // need to satisfy TS here
          field.dataType !== 'timestamp' &&
          field.dataType !== 'object' &&
          field.dataType !== 'list' &&
          field.dataType !== 'id' && (
            <InputContainer
              className={cx('record-update-field-input', `record-update-field-input-${field.dataType}`)}
              datatype={
                field.dataType === AppConstants.INPUT_TYPE.PICKLIST ? AppConstants.INPUT_TYPE.STRING : field.dataType
              }
              value={value}
              onChange={handleOnChange}
            />
          )}
      </HStack>

      <IconButton onClick={onDelete} icon={DeleteIcon} />
    </HStack>
  );
};

export default UpdateModal;
