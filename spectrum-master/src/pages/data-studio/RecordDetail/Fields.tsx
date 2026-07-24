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
  EnhancedFieldMetadata,
  EntityRecord,
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
import ErrorSummary from './ErrorSummary';

import './RecordDetail.less';

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

interface RecordFieldProps extends InputWithLabelProps {
  dataType: FieldDataType;
  label: string;
}

const RecordField = ({ dataType, label, onChange, ...props }: RecordFieldProps) => {
  const handleOnChange = useCallback(
    (evt: boolean | string | number | React.ChangeEvent<HTMLInputElement>) => {
      onChange(props.name, evt);
    },
    [onChange, props.name]
  );

  return (
    <tr className="record-field-row">
      <td className={cx('record-field-label', dataType === AppConstants.INPUT_TYPE.TEXTAREA && 'textarea')}>
        <HStack spacing="xxs">
          <FieldTypeBadge dataType={dataType} description={dataType} />
          <Text weight="semibold" color="gray-1000">
            {label}
            <Text weight="semibold" color="gray-700" className="record-field-row__api-name">
              ({props.apiName})
            </Text>
          </Text>
        </HStack>
      </td>
      <td>
        <InputWithLabel
          className="record-field"
          datatype={
            AUTOCOMPLETE_DATATYPE_OVERRIDES.includes(dataType) ? AppConstants.INPUT_TYPE.AUTOCOMPLETE : dataType
          }
          size="small"
          onChange={handleOnChange}
          defaultValue={props.defaultValue ?? props.value}
          {...props}
        />
      </td>
    </tr>
  );
};

type DataStudioRecordFieldsProps = {
  entity: Entity;
  entityId: string;
  loading?: boolean;
  metadata: Record<string, EnhancedFieldMetadata>;
  record: EntityRecord;
  recordId: string;
};

function DataStudioRecordFields({
  entity,
  entityId,
  loading,
  metadata,
  record,
  recordId,
}: DataStudioRecordFieldsProps) {
  const { tc, tn } = useI18nContext();
  const navigate = useNavigate();

  const dispatch = useEnhancedDispatch();

  const updateErrors = useEnhancedSelector((state) => selectUpdateRecordDataErrors(state, entityId, recordId));
  const updateStatus = useEnhancedSelector((state) => selectUpdateRecordDataStatus(state, entityId, recordId));

  const requestRecordDelete = useDeleteRecordDataModal(entityId, recordId, {
    // navigate back to previous page after delete is successful
    onSuccess: () => navigate(-1),
    showToasts: true,
  });

  useToastForFetchStatusChange(updateStatus, {
    error: tn('update_record_toast_error'),
    success: tn('update_record_toast_success'),
  });

  const isUpdating = updateStatus === AppConstants.FETCH_STATUS.LOADING;

  const [initialFormData, setInitialFormData] = useState<Record<string, any>>(() => record?.values || EMPTY_OBJECT);
  const [formData, setFormData] = useState<EntityRecord['values']>(() => initialFormData);
  const [searchQuery, setSearchQuery] = useState('');

  const changeHandler = useCallback(
    (name: string, evt: boolean | string | number | React.ChangeEvent<HTMLInputElement>) => {
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
    []
  );

  const handleSubmit = useCallback(
    async (e: React.FormEvent<HTMLFormElement>) => {
      e.preventDefault();
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

        // if we've successfully updated
        if (result) {
          const { record, errors = {} } = result;

          if (record && Object.values(errors).length < 1) {
            // we've successfully updated the record, update the initial values
            // to the new data so if the user resets the form, we will get the
            // expected result

            setInitialFormData(record?.values);
          }
        }
      }
    },
    [record, formData, dispatch, entityId, recordId, metadata]
  );

  const handleReset = useCallback(
    async (e: React.FormEvent<HTMLFormElement>) => {
      e.preventDefault();

      dispatch(clearUpdateRecordDataErrors(entityId, recordId));

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
    [dispatch, entityId, recordId, initialFormData, tn]
  );

  const hasErrors = updateErrors && Object.keys(updateErrors).length > 0;

  const filteredFields = matchSorter<EnhancedFieldMetadata>(
    Object.values(metadata),
    searchQuery,
    fieldNameMatchSorterOptions
  ).filter((meta) => meta.canDisplay);

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

  return (
    <Spin wrapperClassName="data-studio-record-detail-spin" spinning={loading}>
      <form onSubmit={handleSubmit} onReset={handleReset}>
        <div className="record-fields-list">
          <Stack spacing="z">
            <HStack className="record-fields-list-header" justify="space-between">
              <HStack>
                <SearchBox
                  size="small"
                  allowClear
                  className="record-fields-search"
                  placeholder={tn('filter_fields_placeholder')}
                  onChange={(evt) => setSearchQuery(evt.target.value)}
                  value={searchQuery}
                />
              </HStack>
              <HStack spacing="lg">
                {hasErrors && <ErrorSummary errors={updateErrors} />}
                <HStack spacing="sm">
                  <Can permission={AllPermissions.WRITE_DATA_STUDIO}>
                    <Button onClick={requestRecordDelete} size="small" type="danger">
                      {tc('delete')}
                    </Button>
                  </Can>
                  <Can permission={AllPermissions.WRITE_DATA_STUDIO}>
                    <Button className="ghost-danger" type="danger" size="small" htmlType="reset">
                      {tc('reset')}
                    </Button>
                  </Can>
                  <Can permission={AllPermissions.WRITE_DATA_STUDIO}>
                    <Button type="primary" htmlType="submit" size="small" disabled={isUpdating} loading={isUpdating}>
                      {tc('save')}
                    </Button>
                  </Can>
                </HStack>
              </HStack>
            </HStack>
            <Stack fill scrollOverflow>
              {filteredFields.length ? (
                <table className="record-fields-table">
                  <tbody>
                    {filteredFields.map(({ key, ...meta }) => {
                      const fieldError = getErrorForField(key, updateErrors);

                      let values: PicklistValue[] = EMPTY_ARRAY;
                      if (meta.dataType === AppConstants.INPUT_TYPE.PICKLIST) {
                        // Get entity field data for possible picklist values
                        values = fieldValuesMap[meta.fieldId] || EMPTY_ARRAY;
                      }

                      return (
                        <RecordField
                          key={key}
                          dataType={meta.dataType as FieldDataType}
                          disabled={!meta.canEdit}
                          id={key}
                          label={meta.label}
                          apiName={meta.apiName}
                          name={key}
                          onChange={changeHandler}
                          readOnly={!meta.canEdit}
                          tooltip={fieldError}
                          tooltipClassName={fieldError ? 'error-text' : undefined}
                          tooltipIcon={fieldError ? 'exclamation-circle' : 'question-circle'}
                          validateStatus={fieldError ? 'error' : isUpdating ? 'validating' : null}
                          value={formData[key]}
                          values={values}
                        />
                      );
                    })}
                  </tbody>
                </table>
              ) : (
                <div className="record-fields-table-empty">
                  <TranslatedText color="black" text="no_fields_found" />
                </div>
              )}
            </Stack>
          </Stack>
        </div>
      </form>
    </Spin>
  );
}

function getErrorForField(fieldKey: string, errors?: Record<string, string>) {
  if (errors && fieldKey in errors) {
    return errors[fieldKey];
  }
}

export const fieldsPageOption = {
  id: 'fields',
  name: 'Fields',
  icon: FieldsIcon,
};

// eslint-disable-next-line no-empty-pattern
const DataStudioRecordFieldsContainer = ({}: RouteComponentProps) => {
  const params: { entityId: string; recordId: string } = useParams();
  const { entityId, recordId } = params;
  const { tn } = useI18nContext();

  const { data: record, metadata, idle, loading, entity } = useEntityRecord({ entityId, recordId });

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
    />
  );
};

export default DataStudioRecordFieldsContainer;
