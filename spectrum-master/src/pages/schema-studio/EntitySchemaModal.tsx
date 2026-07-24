import { navigate } from '@reach/router';
import { Button } from 'antd';
import * as React from 'react';
import { useEffect, useRef, useState } from 'react';
import { connect, ConnectedProps } from 'react-redux';
import { bindActionCreators } from 'redux';

import Checkbox, { CheckboxChangeEvent } from 'components/Checkbox';
import InlineMessage, { Types as InlineMessageTypes } from 'components/InlineMessage';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { ValidateStatuses } from 'components/inputs/types';
import Modal from 'components/Modal';
import { TextTag } from 'components/text-tag';
import { Text, TranslatedText } from 'components/typography';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { EMPTY_OBJECT } from 'store/constants';
import { getEntities } from 'store/entity/actions';
import { resetEntityModal } from 'store/schema/actions';
import { CloneEntityPayload, useClonePipelineMutation } from 'store/schema/api';
import { saveEntity } from 'store/schema/thunks';
import AppConstants from 'utils/AppConstants';
import { t, tc, tNamespaced } from 'utils/i18nUtil';
import RouteConstants from 'utils/RouteConstants';
import { createApiName, schemaApiNameRegex } from 'utils/StringUtil';
import { makeUrl } from 'utils/UrlUtil';

import { RootState } from '../../reducers';
import { EntityModel } from './types';

import './EntitySchemaModal.scss';

const tn = tNamespaced('EntitySchemaModal');
const { INPUT_TYPE } = AppConstants;

type EntityValues = Partial<EntityModel>;

type EntityValuesWithRunDfiAndRunMerge = EntityValues & {
  runDFI?: boolean;
  runMerge?: boolean;
};

interface EntitySchemaModalProps {
  /**
   * Selected entity on the table. The modal will
   * switch to create new entity if the entity is empty
   */
  entityId?: string;
  /**
   * Calback when this modal is closed
   */
  onClose?: () => void;
  /**
   * Connector id of the entity. A new entity is added to this connector or updating
   * the enttiy if the entity parameter is specified
   */
  connectorId?: string;
  /**
   * Make this modal visible or hidden
   */
  visible?: boolean;
  /**
   * True if cloning an existing entity pipeline
   */
  cloning?: boolean;
}

interface EntityModalValidation {
  /**
   * Status of display name validation
   */
  displayNameValidateStatus?: ValidateStatuses;
  /**
   * Help message to display on the field
   */
  displayNameHelp?: string;
  /**
   * Status of api name validation
   */
  apiNameValidateStatus?: ValidateStatuses;
  /**
   * Help message to display on the field
   */
  apiNameHelp?: string;
  /**
   * Status of data store name validation
   */
  dataStoreNameValidateStatus?: ValidateStatuses;
  /**
   * Help message to display on the field
   */
  dataStoreNameHelp?: string;
}

const connector = connect(
  (state: RootState) => ({
    entityDetails: state.schema.entityDetails,
    saveEntityErrorMessage: state.schema.saveEntityErrorMessage,
    saveEntityStatus: state.schema.saveEntityStatus,
  }),
  (dispatch) =>
    bindActionCreators(
      {
        saveEntity,
        resetEntityModal,
      },
      dispatch
    )
);

type PropsFromRedux = ConnectedProps<typeof connector>;

const EntitySchemaModal = ({
  entityId,
  entityDetails,
  onClose,
  saveEntity,
  resetEntityModal,
  connectorId,
  saveEntityErrorMessage,
  saveEntityStatus,
  visible = false,
  cloning = false,
}: EntitySchemaModalProps & PropsFromRedux) => {
  const [values, setValues] = useState<EntityValuesWithRunDfiAndRunMerge>({});
  const [validation, setValidation] = useState<EntityModalValidation>({
    dataStoreNameHelp: t('FieldSchemaModal.data_store_name_warning'),
  });

  const dispatch = useEnhancedDispatch();

  const [clonePipelineMutation, { isLoading }] = useClonePipelineMutation();

  const { clonePipelineIsDraft, clonePipelineName } = useEnhancedSelector((state) => state.entityPipeline);
  const [cloneErrorMessage, setCloneErrorMessage] = useState('');

  const initialSaveEntityStatus = useRef<string | undefined>();
  const saving = isLoading || saveEntityStatus === AppConstants.FETCH_STATUS.LOADING;

  useEffect(() => {
    if (!entityId) {
      setValues(EMPTY_OBJECT);
    } else if (visible && entityId && entityDetails) {
      setValues(entityDetails);
    }
  }, [entityId, entityDetails, visible]);

  useEffect(() => {
    if (initialSaveEntityStatus?.current !== saveEntityStatus) {
      if (
        initialSaveEntityStatus.current === AppConstants.FETCH_STATUS.LOADING &&
        saveEntityStatus === AppConstants.FETCH_STATUS.SUCCESS
      ) {
        setValues(EMPTY_OBJECT);
        onClose && onClose();
      }
      initialSaveEntityStatus.current = saveEntityStatus;
    }
  }, [saveEntityStatus, onClose]);

  const change = (evt: React.ChangeEvent<HTMLInputElement>) => {
    setValues({
      ...values,
      [evt.target.name]: evt.target.value,
    });
  };

  const onTagChange = (tags: string[]) => {
    setValues({
      ...values,
      tags,
    });
  };

  const onCheckboxChange = (field: 'runDFI' | 'runMerge') => (evt: CheckboxChangeEvent) => {
    setValues({
      ...values,
      [field]: evt.target.checked,
    });
  };

  const onCancel = () => {
    setValues(EMPTY_OBJECT);
    onClose && onClose();
    resetEntityModal && resetEntityModal();
  };

  const validateDisplayName = () => {
    if (!values.displayName) {
      setValidation((validation) => ({
        ...validation,
        displayNameValidateStatus: ValidateStatuses.ERROR,
        displayNameHelp: tn('display_name_not_empty'),
      }));

      return false;
    }

    setValidation((validation) => ({
      ...validation,
      displayNameValidateStatus: ValidateStatuses.BLANK,
      displayNameHelp: '',
    }));

    return true;
  };

  const validateApiName = () => {
    if (!values.apiName) {
      setValidation((validation) => ({
        ...validation,
        apiNameValidateStatus: ValidateStatuses.ERROR,
        apiNameHelp: tn('api_name_not_empty') as string,
      }));

      return false;
    } else if (values.apiName.match(/[^a-zA-Z\d\+_:-]/)) {
      setValidation((validation) => ({
        ...validation,
        apiNameValidateStatus: ValidateStatuses.ERROR,
        apiNameHelp: tn('api_name_alpha_numeric') as string,
      }));

      return false;
    }

    setValidation((validation) => ({
      ...validation,
      apiNameValidateStatus: ValidateStatuses.BLANK,
      apiNameHelp: '',
    }));

    return true;
  };

  const validateDataStoreName = () => {
    // Limit the data store name to be 59 characters or less. This limit
    // is due to postgresql limitiations for field names.
    if (values.dataStoreName && values.dataStoreName.length > 59) {
      setValidation((validation) => ({
        ...validation,
        dataStoreNameValidateStatus: ValidateStatuses.ERROR,
        dataStoreNameHelp: t('FieldSchemaModal.data_store_name_too_long'),
      }));

      return false;
    }

    // Do not allow whitespace in the data store name.
    if (values.dataStoreName && !values.dataStoreName.match(/^\S*$/)) {
      setValidation((validation) => ({
        ...validation,
        dataStoreNameValidateStatus: ValidateStatuses.ERROR,
        dataStoreNameHelp: t('FieldSchemaModal.data_store_name_no_whitespace'),
      }));

      return false;
    }

    setValidation((validation) => ({
      ...validation,
      dataStoreNameValidateStatus: ValidateStatuses.BLANK,
      dataStoreNameHelp: t('FieldSchemaModal.data_store_name_warning'),
    }));

    return true;
  };

  const save = async () => {
    const isDisplayNameValid = validateDisplayName();
    const isApiNameValid = validateApiName();
    const isDataStoreNameValid = validateDataStoreName();

    if (isDisplayNameValid && isApiNameValid && isDataStoreNameValid) {
      if (cloning) {
        const payload: CloneEntityPayload = {
          ...values,
          entityId: entityId as string,
          cloneFromDraft: clonePipelineIsDraft,
        };
        clonePipelineMutation(payload).then((response) => {
          if ('data' in response) {
            setCloneErrorMessage('');
            const pipelineUrl = makeUrl(RouteConstants.ENTITY_PIPELINE_GRAPH_VERSION, {
              entityId: response.data.targetId,
              graphVersion: AppConstants.GRAPH_STATUS.DRAFT,
            });

            // Refetch entities to get the new entity in the schema
            dispatch(getEntities());

            onCancel();
            navigate(pipelineUrl);
          } else if ('error' in response) {
            setCloneErrorMessage((response.error as any).data.message || t('ErrorUi.unexpected_error'));
          }
        });
      } else if (connectorId) {
        saveEntity && saveEntity(values, { refresh: true, connectorId });
      }
    }
  };

  const errorMessage =
    cloneErrorMessage ||
    (typeof saveEntityErrorMessage === 'string' ? saveEntityErrorMessage : saveEntityErrorMessage.message);

  const apiNameFieldDisableld = Boolean(entityId) && !cloning;

  let titleText = values?.displayName ? values?.displayName : tn('new_entity');
  let actionText = entityId ? tc('save') : tc('create');
  let cloneTag = null;

  if (cloning) {
    actionText = tc('clone');
    titleText = tn('clone_name', { clonePipelineName });
    cloneTag = clonePipelineIsDraft ? (
      <TextTag text={tc('draft')} color="orange" />
    ) : (
      <TextTag text={tc('published')} color="blue" />
    );
  }

  return (
    <Modal
      mask
      onCancel={onCancel}
      title={
        <div className="synri-entity-schema-modal-title-container">
          <Text className="synri-entity-schema-modal-title-container__title_text">{titleText}</Text>
          {cloneTag}
        </div>
      }
      width={640}
      visible={visible}
      footer={
        <>
          <Button onClick={onCancel}>{tc('cancel')}</Button>
          <Button onClick={save} type="primary" disabled={saving} loading={saving}>
            {actionText}
          </Button>
        </>
      }>
      {errorMessage && (
        <InlineMessage type={InlineMessageTypes.ERROR} title={errorMessage}>
          {errorMessage}
        </InlineMessage>
      )}

      <InputWithLabel
        name="displayName"
        validateStatus={validation?.displayNameValidateStatus}
        help={validation?.displayNameHelp}
        label={tn('display_name')}
        value={values?.displayName}
        datatype={INPUT_TYPE.STRING}
        onChange={change}
        onBlur={() => {
          // Automatically pre-fill the apiName based on the display name
          if (values?.displayName && !apiNameFieldDisableld && !values?.apiName) {
            setValues({ ...values, apiName: createApiName(values.displayName, schemaApiNameRegex) });
          }
        }}
      />
      <InputWithLabel
        name="apiName"
        validateStatus={validation?.apiNameValidateStatus}
        help={validation?.apiNameHelp}
        disabled={apiNameFieldDisableld}
        label={tn('api_name')}
        datatype={INPUT_TYPE.STRING}
        value={values?.apiName}
        onChange={change}
      />
      <InputWithLabel
        name="dataStoreName"
        validateStatus={validation?.dataStoreNameValidateStatus}
        label={t('FieldSchemaModal.data_store_name')}
        value={values?.dataStoreName}
        datatype={INPUT_TYPE.STRING}
        onChange={change}
        help={!values?.dataStoreName ? '' : validation?.dataStoreNameHelp}
      />
      <InputWithLabel
        name="description"
        label={tn('description')}
        value={values?.description}
        datatype={INPUT_TYPE.TEXTAREA}
        onChange={change}
      />
      <InputWithLabel label={tn('tags')} datatype="tag" onChange={onTagChange} defaultValue={values?.tags} />
      <div className="entity-additional-rules-wrapper">
        <p>{tn('rules-title')}</p>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          <Checkbox checked={values?.runDFI || false} onChange={onCheckboxChange('runDFI')}>
            <TranslatedText namespace="EntitySchemaModal" text="run-dfi" beDangerous />
          </Checkbox>
          <Checkbox checked={values?.runMerge || false} onChange={onCheckboxChange('runMerge')}>
            <TranslatedText namespace="EntitySchemaModal" text="run-merge" beDangerous />
          </Checkbox>
        </div>
      </div>
    </Modal>
  );
};

export default connector(EntitySchemaModal);
