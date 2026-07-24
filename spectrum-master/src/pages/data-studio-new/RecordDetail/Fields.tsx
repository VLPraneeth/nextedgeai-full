import { Redirect, RouteComponentProps, useNavigate, useParams } from '@reach/router';
import Modal from 'antd/lib/modal';
import Spin from 'antd/lib/spin';
import cx from 'classnames';
import { omitBy } from 'lodash';
import { matchSorter, MatchSorterOptions } from 'match-sorter';
import * as React from 'react';
import { useCallback, useMemo, useState } from 'react';

import { ReactComponent as FieldsIcon } from 'assets/icons/record-fields.svg';
import Button from 'components/Button';
import Can from 'components/Can';
import FieldTypeBadge from 'components/FieldTypeBadge';
import { useI18nContext } from 'components/I18nProvider';
import InputWithLabel, { InputWithLabelProps } from 'components/inputs/InputWithLabel';
import { PicklistValue } from 'components/inputs/types';
import { HStack, Stack } from 'components/layout';
import RouteSpin from 'components/RouteSpin';
import SearchBox from 'components/SearchBox';
import { FieldDataType } from 'components/types';
import { Text, TranslatedText } from 'components/typography';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import useToastForFetchStatusChange from 'hooks/useToastForFetchStatusChange';
import { EMPTY_ARRAY, EMPTY_OBJECT } from 'store/constants';
import {
  clearUpdateRecordDataErrors,
  createRecord,
  EnhancedFieldMetadata,
  EntityRecord,
  selectCreateRecordDataErrors,
  selectCreateRecordDataStatus,
  selectUpdateRecordDataErrors,
  selectUpdateRecordDataStatus,
  updateRecordData,
  useEntityRecord,
} from 'store/data-studio';
import { Entity } from 'store/entity/types';
import AppConstants from 'utils/AppConstants';
import { AllPermissions } from 'utils/PermissionsConstants';
import RouteConstants from 'utils/RouteConstants';
import { isPrimitive, isReactChangeEvent } from 'utils/TypeUtils';
import { makeUrl } from 'utils/UrlUtil';

import { useDeleteRecordDataModal } from '../hooks';

import './RecordDetail.less';
import { Icon } from 'antd';
import Tooltip from 'components/tooltip/Tooltip';

const fieldNameMatchSorterOptions: MatchSorterOptions<EnhancedFieldMetadata> = {
  keys: [
    { threshold: matchSorter.rankings.CONTAINS, key: 'apiName' },
    { threshold: matchSorter.rankings.CONTAINS, key: 'label' },
  ],
};

const AUTOCOMPLETE_DATATYPE_OVERRIDES: FieldDataType[] = [
  AppConstants.INPUT_TYPE.PICKLIST,
  AppConstants.INPUT_TYPE.REFERENCE,
  AppConstants.INPUT_TYPE.POLYMORPHIC_REFERENCE,
];

interface FieldErrorObject {
  code: string;
  message: string;
}

interface RecordFieldProps extends InputWithLabelProps {
  dataType: FieldDataType;
  label: string;
  mode: string;
  fieldErrors?: FieldErrorObject[];
}

const RecordField = ({ dataType, label, onChange, mode, fieldErrors, ...props }: RecordFieldProps) => {
  const handleOnChange = useCallback(
    (evt: boolean | string | number | React.ChangeEvent<HTMLInputElement>) => {
      onChange(props.name, evt);
    },
    [onChange, props.name]
  );

  return (
    <>
      <div className={cx('field-label', dataType === AppConstants.INPUT_TYPE.TEXTAREA && 'textarea')}>
        <HStack spacing="xxs">
          <Text className="field-label">
            {label}
            <Text weight="medium" className="record-field-row__api-name">
              [{props.apiName}]
            </Text>
          </Text>
        </HStack>
      </div>
      <InputWithLabel
        className={cx('field-value', mode === 'view' && 'field-value-view-mode')}
        prefix={mode === 'view' ? null : <FieldTypeBadge dataType={dataType} description={dataType} />}
        size="default"
        onChange={handleOnChange}
        defaultValue={props.defaultValue ?? props.value}
        disabled={mode === 'view'}
        {...props}
      />
      {fieldErrors && fieldErrors.length > 0 && (
        <div className="field-error-messages">
          {fieldErrors.map((error, index) => (
            <Text key={index} size="sm" color="red-500" className="field-error-message">
              {error?.message}
            </Text>
          ))}
        </div>
      )}
    </>
  );
};

type DataStudioRecordFieldsProps = {
  entity: Entity;
  entityId: string;
  loading?: boolean;
  metadata: Record<string, EnhancedFieldMetadata>;
  record: EntityRecord;
  recordId: string;
  mode?: string;
  onRecordCreated?: () => void;
  onRecordDeleted?: () => void;
};

interface DataStudioRecordFieldsContainerProps extends RouteComponentProps {
  displayMode: 'view' | 'edit' | 'create';
  onRecordCreated?: () => void;
  onRecordDeleted?: () => void;
}

// Type definition for error payload
type ErrorsPayload =
  | {
      fields?: Record<string, FieldErrorObject[]>;
      record?: any[];
    }
  | Record<string, string>; // Support both new and legacy formats

function DataStudioRecordFields({
  entity,
  entityId,
  loading,
  metadata,
  record,
  recordId,
  mode,
  onRecordCreated,
  onRecordDeleted,
}: DataStudioRecordFieldsProps) {
  const { tc, tn } = useI18nContext();
  const navigate = useNavigate();

  const dispatch = useEnhancedDispatch();

  const [updateErrors, setUpdateErrors] = useState<ErrorsPayload | undefined>(undefined);
  const [createErrors, setCreateErrors] = useState<ErrorsPayload | undefined>(undefined);

  const updateStatus = useEnhancedSelector((state) => selectUpdateRecordDataStatus(state, entityId, recordId));
  const createStatus = useEnhancedSelector((state) => selectCreateRecordDataStatus(state, entityId));

  const requestRecordDelete = useDeleteRecordDataModal(entityId, recordId, {
    // navigate back to previous page after delete is successful
    onSuccess: () => {
      // Refetch entity record counts before navigating to ensure counts are updated
      onRecordDeleted?.();
      navigate(-1);
    },
  });

  useToastForFetchStatusChange(updateStatus, {
    error: tn('update_record_toast_error'),
    success: tn('update_record_toast_success'),
  });

  useToastForFetchStatusChange(createStatus.status, {
    error: tn('create_record_toast_error'),
    success: tn('create_record_toast_success'),
  });

  const isUpdating = updateStatus === AppConstants.FETCH_STATUS.LOADING;

  // Initialize empty form for create mode
  const [initialFormData, setInitialFormData] = useState<Record<string, any>>(() => {
    if (mode === 'create') {
      return Object.keys(metadata || {}).reduce((acc, key) => {
        acc[key] = '';
        return acc;
      }, {} as Record<string, any>);
    }
    return record?.values || EMPTY_OBJECT;
  });

  const [formData, setFormData] = useState<EntityRecord['values']>(() => initialFormData);
  const [searchQuery, setSearchQuery] = useState('');

  const changeHandler = useCallback(
    (name: string, evt: boolean | string | number | React.ChangeEvent<HTMLInputElement>) => {
      // Clear field-specific errors when user modifies a field
      if (mode === 'create' && createErrors) {
        setCreateErrors((prev) => {
          if (!prev) return prev;
          if (isNestedErrorFormat(prev) && prev.fields) {
            const { [name]: removed, ...rest } = prev.fields;
            return { ...prev, fields: rest };
          }
          if (typeof prev === 'object' && name in prev) {
            const { [name]: removed, ...rest } = prev as Record<string, string>;
            return rest;
          }
          return prev;
        });
      } else if (mode === 'edit' && updateErrors) {
        setUpdateErrors((prev) => {
          if (!prev) return prev;
          if (isNestedErrorFormat(prev) && prev.fields) {
            const { [name]: removed, ...rest } = prev.fields;
            return { ...prev, fields: rest };
          }
          if (typeof prev === 'object' && name in prev) {
            const { [name]: removed, ...rest } = prev as Record<string, string>;
            return rest;
          }
          return prev;
        });
      }

      if (isReactChangeEvent(evt)) {
        const newValue = evt.target.value;

        setFormData((prev) => ({
          ...prev,
          [name]: newValue,
        }));
        return;
      }

      if (isPrimitive(evt)) {
        setFormData((prev) => ({
          ...prev,
          [name]: evt,
        }));
        return;
      }
    },
    [mode, createErrors, updateErrors]
  );

  const handleSubmit = useCallback(
    async (e: React.FormEvent<HTMLFormElement>) => {
      e.preventDefault();
      setCreateErrors(undefined);
      setUpdateErrors(undefined);

      if (mode === 'create') {
        const response = await dispatch(createRecord(entityId, { values: formData }));
        if (response) {
          const { errors } = response;
          // Check for field errors or record errors in the new nested format
          const hasFieldErrors = errors?.fields && Object.keys(errors.fields).length > 0;
          const hasRecordErrors = errors?.record && errors.record.length > 0;

          if (hasFieldErrors || hasRecordErrors) {
            setCreateErrors(errors);
            return;
          }

          // Reset filters and sorts to ensure the newly created record is visible
          if (onRecordCreated) {
            onRecordCreated();
          } else {
            // Fallback if no callback provided
            navigate(makeUrl(RouteConstants.DATA_STUDIO_ENTITY, { entityId }));
          }
        }
        return;
      }

      if (record) {
        // Remove any values that can not be edited by the user
        const values = omitBy(
          {
            ...record.values,
            ...formData,
          },
          (_, key) => !metadata[key]?.canEdit
        );

        const result = await dispatch(updateRecordData(entityId, recordId, { ...record, values }));
        if (result) {
          const { record: updatedRecord, errors } = result;

          // Check for field errors or record errors in the new nested format
          const hasFieldErrors = errors?.fields && Object.keys(errors.fields).length > 0;
          const hasRecordErrors = errors?.record && errors.record.length > 0;

          if (updatedRecord && !hasFieldErrors && !hasRecordErrors) {
            // we've successfully updated the record, update the initial values
            // to the new data so if the user resets the form, we will get the
            // expected result

            setInitialFormData(updatedRecord?.values);
            navigate(makeUrl(RouteConstants.DATA_STUDIO_ENTITY, { entityId }));
          } else if (hasFieldErrors || hasRecordErrors) {
            // Store errors in local state
            setUpdateErrors(errors);
          }
        }
      }
    },
    [record, formData, dispatch, entityId, recordId, metadata, mode, navigate]
  );

  const handleReset = useCallback(
    async (e: React.FormEvent<HTMLFormElement>) => {
      e.preventDefault();

      setUpdateErrors(undefined);
      setCreateErrors(undefined);

      Modal.confirm({
        title: tn('reset_record_detail_modal_title'),
        cancelText: tn('reset_record_detail_modal_cancel_btn'),
        okText: tn('reset_record_detail_modal_ok_btn'),
        onCancel: () => Promise.resolve(),
        onOk: () => {
          // reset the form
          setFormData(initialFormData);
        },
      });
    },
    [initialFormData, tn]
  );

  const excludedApiNames = ['syncariId', 'idMapping', 'datastudio_isDeleted'];

  // Filter and sort fields based on mode
  const filteredFields = matchSorter<EnhancedFieldMetadata>(
    Object.values(metadata),
    searchQuery,
    fieldNameMatchSorterOptions
  )
    .filter((meta) => {
      // In create mode, filter out fields that can't be displayed or are externalId
      if (mode === 'create') {
        return meta.canDisplay && !excludedApiNames.includes(meta.apiName as string) && meta.dataType !== 'externalId';
      }
      // In other modes, show all fields
      return true;
    })
    .sort((a, b) => {
      // Get the index of each field in the excludedApiNames array (-1 if not found)
      const aExcludedIndex = excludedApiNames.indexOf(a.apiName as string);
      const bExcludedIndex = excludedApiNames.indexOf(b.apiName as string);

      // If both are in excludedApiNames, sort by their array order
      if (aExcludedIndex !== -1 && bExcludedIndex !== -1) {
        return aExcludedIndex - bExcludedIndex;
      }

      if (aExcludedIndex !== -1) return -1;

      if (bExcludedIndex !== -1) return 1;

      // Both are not in excludedApiNames, sort alphabetically by label
      const labelA = a.label.toLowerCase();
      const labelB = b.label.toLowerCase();
      return labelA.localeCompare(labelB);
    });

  // Helper function to determine if a field should be disabled in edit mode
  const shouldDisableField = (meta: Omit<EnhancedFieldMetadata, 'key'>, currentMode: string | undefined) => {
    // In view mode, all fields are disabled (read-only)
    if (currentMode === 'view') {
      return true;
    }

    // In edit mode, disable system fields:
    if (currentMode === 'edit') {
      return meta.isSystem || meta.dataType === 'externalId';
    }

    return false;
  };

  const fieldValuesMap = useMemo(() => {
    const fieldMap: Record<string, PicklistValue[]> = {};

    if (!entity?.fields) {
      return fieldMap;
    }

    return entity.fields.reduce((map, field) => {
      if (field.values) {
        map[field.id] = field.values.map((value) => {
          return { value, label: value };
        });
      }
      return fieldMap;
    }, fieldMap);
  }, [entity?.fields]);

  // Add footer for create mode
  const renderDrawerFooter = () => {
    const hasEmptyValues = Object.values(formData).every((value) => !value);
    if (mode === 'create') {
      return (
        <div className="record-detail-footer">
          <div className="footer-actions">
            <Can permission={AllPermissions.WRITE_DATA_STUDIO}>
              <Tooltip title={hasEmptyValues ? tn('empty_fields_warning_text') : ''} mouseEnterDelay={0}>
                <span>
                  <Button size="large" type="danger" htmlType="reset" className="reset-btn" disabled={hasEmptyValues}>
                    Reset
                  </Button>
                </span>
              </Tooltip>
            </Can>
            <Can permission={AllPermissions.WRITE_DATA_STUDIO}>
              <Tooltip title={hasEmptyValues ? tn('empty_fields_warning_text') : ''} mouseEnterDelay={0}>
                <span>
                  <Button
                    size="large"
                    type="primary"
                    className="confirm-btn"
                    htmlType="submit"
                    loading={isUpdating}
                    disabled={isUpdating || hasEmptyValues}>
                    Create Now
                  </Button>
                </span>
              </Tooltip>
            </Can>
          </div>
        </div>
      );
    } else if (mode === 'edit') {
      return (
        <div className="record-detail-footer">
          <div className="footer-actions">
            <Can permission={AllPermissions.WRITE_DATA_STUDIO}>
              <Button size="large" type="danger" htmlType="reset" className="reset-btn">
                Reset
              </Button>
            </Can>
            <Can permission={AllPermissions.WRITE_DATA_STUDIO}>
              <Button
                size="large"
                type="primary"
                className="confirm-btn"
                htmlType="submit"
                loading={isUpdating}
                disabled={isUpdating}>
                Confirm and Save
              </Button>
            </Can>
          </div>
        </div>
      );
    } else if (mode === 'view') {
      return (
        <div className="record-detail-footer">
          <div className="footer-actions">
            <Can permission={AllPermissions.WRITE_DATA_STUDIO}>
              <Button size="large" type="danger" className="delete-btn" onClick={requestRecordDelete}>
                <Icon type="delete" />
              </Button>
            </Can>
            <Button
              size="large"
              type="primary"
              className="confirm-btn"
              onClick={() =>
                navigate(makeUrl(RouteConstants.DATA_STUDIO_RECORD_FIELDS, { entityId, recordId }), {
                  replace: false,
                })
              }>
              <Icon type="edit" /> Edit Record
            </Button>
          </div>
        </div>
      );
    }
  };

  return (
    <Spin wrapperClassName="data-studio-record-detail-spin" spinning={loading}>
      <form onSubmit={handleSubmit} onReset={handleReset}>
        <div className="record-detail-fields">
          <SearchBox
            allowClear
            className="record-fields-search"
            placeholder={tn('filter_fields_placeholder')}
            onChange={(evt) => setSearchQuery(evt.target.value)}
            value={searchQuery}
          />
          {filteredFields.length ? (
            <div className="record-fields-list">
              {filteredFields.map(({ key, ...meta }) => {
                const errorMode = mode === 'create' ? createErrors : updateErrors;
                const fieldErrors = getAllErrorsForField(meta.apiName as string, errorMode);
                let values: PicklistValue[] = EMPTY_ARRAY;
                if (meta.dataType === AppConstants.INPUT_TYPE.PICKLIST) {
                  values = fieldValuesMap[meta.fieldId] || EMPTY_ARRAY;
                }

                const isFieldDisabled = shouldDisableField(meta, mode);

                return (
                  <div key={key} className="record-field">
                    <div className="field-value">
                      <RecordField
                        key={key}
                        dataType={meta.dataType as FieldDataType}
                        disabled={isFieldDisabled}
                        id={key}
                        label={meta.label}
                        apiName={meta.apiName}
                        name={key}
                        onChange={changeHandler}
                        readOnly={isFieldDisabled}
                        validateStatus={fieldErrors?.length ? 'error' : isUpdating ? 'validating' : null}
                        value={formData[key]}
                        values={values}
                        mode={mode as string}
                        fieldErrors={fieldErrors}
                      />
                    </div>
                  </div>
                );
              })}
            </div>
          ) : (
            <div className="record-fields-table-empty">
              <TranslatedText color="black" text="no_fields_found" />
            </div>
          )}
        </div>
        {renderDrawerFooter()}
      </form>
    </Spin>
  );
}

function isNestedErrorFormat(errors: ErrorsPayload): errors is { fields?: Record<string, FieldErrorObject[]> } {
  return typeof errors === 'object' && 'fields' in errors;
}

// function getErrorForField(fieldApiName: string, errors?: ErrorsFormat): string | undefined {
//   if (!errors) return undefined;

//   // Handle new nested format: errors.fields[apiName] = [{code, message}, ...]
//   if (isNestedErrorFormat(errors) && errors.fields) {
//     const fieldErrors = errors.fields[fieldApiName];
//     if (Array.isArray(fieldErrors) && fieldErrors.length > 0) {
//       // Return the first error message
//       return fieldErrors[0].message;
//     }
//     return undefined;
//   }

//   // Handle legacy flat format: errors[fieldKey] = "error message"
//   const flatErrors = errors as Record<string, string>;
//   if (fieldApiName in flatErrors) {
//     return flatErrors[fieldApiName];
//   }

//   return undefined;
// }

function getAllErrorsForField(fieldApiName: string, errors?: ErrorsPayload): FieldErrorObject[] | undefined {
  if (!errors) return undefined;

  // Handle new nested format: errors.fields[apiName] = [{code, message}, ...]
  if (isNestedErrorFormat(errors) && errors.fields) {
    const fieldErrors = errors.fields[fieldApiName];
    if (Array.isArray(fieldErrors) && fieldErrors.length > 0) {
      return fieldErrors;
    }
    return undefined;
  }

  // Handle legacy flat format: errors[fieldKey] = "error message"
  const flatErrors = errors as Record<string, string>;
  if (fieldApiName in flatErrors) {
    return [{ code: 'ERROR', message: flatErrors[fieldApiName] }];
  }

  return undefined;
}

export const fieldsPageOption = {
  id: 'fields',
  name: 'Fields',
  icon: FieldsIcon,
};

const DataStudioRecordFieldsContainer = ({
  displayMode,
  onRecordCreated,
  onRecordDeleted,
}: DataStudioRecordFieldsContainerProps) => {
  const params: { entityId: string; recordId: string } = useParams();
  const { entityId, recordId } = params;
  const { tn } = useI18nContext();

  const { data: record, metadata, idle, loading, entity } = useEntityRecord({ entityId, recordId });

  // Check for create mode first, before checking loading/idle status
  if (displayMode === 'create' && !record) {
    return (
      <DataStudioRecordFields
        entity={entity}
        entityId={entityId}
        loading={loading}
        metadata={metadata}
        record={{ values: {} } as EntityRecord}
        recordId="new"
        mode={displayMode}
        onRecordCreated={onRecordCreated}
      />
    );
  }

  if (!record) {
    if (idle || loading) {
      return <RouteSpin />;
    }
    Modal.error({ title: tn('window_title'), content: tn('record_not_found') });
    return <Redirect noThrow to={makeUrl(RouteConstants.DATA_STUDIO_ENTITY, { entityId })} />;
  }

  return (
    <DataStudioRecordFields
      entity={entity}
      entityId={entityId}
      key={recordId}
      loading={loading}
      metadata={metadata}
      record={record}
      recordId={recordId}
      mode={displayMode}
      onRecordCreated={onRecordCreated}
      onRecordDeleted={onRecordDeleted}
    />
  );
};

export default DataStudioRecordFieldsContainer;
