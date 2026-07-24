import { Button, Modal, Tooltip } from 'antd';
import { OptionProps } from 'antd/lib/select';
import cx from 'classnames';
import { find, isUndefined, partition } from 'lodash';
import { ChangeEvent, useCallback, useEffect, useMemo, useState } from 'react';

import FieldTypeBadge from 'components/FieldTypeBadge';
import { DataTypeIcons } from 'components/FieldTypeBadge';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { Option } from 'components/inputs/Select';
import { FieldDataType } from 'components/types';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { CreateFieldModalMode, showCreateField } from 'store/fast-mapper/slice';
import AppConstants from 'utils/AppConstants';
import { tc, tNamespaced } from 'utils/i18nUtil';
import { apiNameRegEx } from 'utils/RegexUtil';
import { createApiName, humanize } from 'utils/StringUtil';

import { useAddMappingContext } from '../AddMapping';
import { FieldType, getCompatibleDestinationTypes } from '../FastMapper.util';
import { FastMapperMode } from '../FastMapperModal';
import { MapperFields, useMapper, useEditableCell } from '../Mapper';

import './CreateFieldDropdown.scss';

const tn = tNamespaced('CreateFieldDropdown');

export interface CreateFieldState {
  displayName: string;
  apiName: string;
  dataType: FieldDataType;
  isMultivalued: boolean;
  isRequired: boolean;
}

const initialFieldState: CreateFieldState = {
  displayName: '',
  apiName: '',
  dataType: 'boolean',
  isMultivalued: false,
  isRequired: false,
};

type ModalPosition = {
  top: number;
  left: number;
};

// Rough height of the modal in px.
// The actual hight is 389.5px.
const MODAL_HEIGHT = 400;

export const CreateFieldDropdown = () => {
  const dispatch = useEnhancedDispatch();

  const { state } = useAddMappingContext();
  const { id, mode, position, visible, data } = useEnhancedSelector((state) => state.fastMapper.createFieldModal);

  const row = useMemo(() => find(state.values, { id }), [state.values, id]);

  const { changeHandler, setSelectedValue } = useEditableCell(
    MapperFields.SYNCARI_ENTITY_FIELD_ID,
    id,
    FastMapperMode.ADD
  );
  const { addNewCustomField, picklistData } = useMapper(FastMapperMode.ADD, row, MapperFields.SYNCARI_ENTITY_FIELD_ID);

  const [formState, setFormState] = useState<CreateFieldState>(data ?? initialFieldState);
  const [displayNameVisited, setDisplayNameVisited] = useState(false);
  const [apiNameVisited, setApiNameVisited] = useState(false);
  const [modalPosition, setModalPosition] = useState<ModalPosition>();

  const { displayNameStatus, apiNameStatus } = useMemo(
    () => ({
      displayNameStatus: displayNameVisited && !formState.displayName ? 'error' : 'success',
      apiNameStatus: apiNameVisited && !formState.apiName ? 'error' : 'success',
    }),
    [apiNameVisited, displayNameVisited, formState.apiName, formState.displayName]
  );

  const { displayNameHelp, apiNameHelp } = useMemo(
    () => ({
      displayNameHelp: displayNameStatus === 'error' ? tn('display_name_help') : undefined,
      apiNameHelp: apiNameStatus === 'error' ? tn('api_name_help') : undefined,
    }),
    [apiNameStatus, displayNameStatus]
  );

  const synapseField = useMemo(() => {
    if (
      row?.synapseFieldId &&
      picklistData[MapperFields.SYNAPSE_FIELD_ID] &&
      picklistData[MapperFields.SYNCARI_ENTITY_FIELD_ID]
    ) {
      return find(picklistData[MapperFields.SYNAPSE_FIELD_ID], { id: row.synapseFieldId });
    }
  }, [picklistData, row?.synapseFieldId]);

  const datatypeOptions = useMemo(() => {
    const destinationTypes = getCompatibleDestinationTypes(synapseField?.dataType as FieldType);
    const dataTypeKeys = Object.keys(DataTypeIcons).filter((dataType) => dataType !== 'list');
    const [compatableDatatypes, incompatableDatatypes] = partition(dataTypeKeys, (datatype) => {
      // Allow any datatype if there is no synapse field selected or if
      // the provided type could not produce any destination types.
      if (!synapseField || destinationTypes.length === 0) {
        return true;
      }

      return destinationTypes.includes(datatype as FieldType);
    });

    const options: JSX.Element[] = [];

    compatableDatatypes.forEach((datatype) => {
      options.push(
        <Option value={datatype} key={datatype}>
          <div className="create-field-dropdown__datatype-option">
            <FieldTypeBadge dataType={datatype as FieldDataType} description={humanize(datatype)} disableTooltip />
            <span>{humanize(datatype)}</span>
          </div>
        </Option>
      );
    });

    incompatableDatatypes.forEach((datatype) => {
      options.push(
        <Option value={datatype} key={datatype} disabled>
          <Tooltip
            title={tc('disabled_datatype_tooltip', { source: synapseField?.dataType, destination: datatype })}
            placement="leftTop"
            mouseEnterDelay={1}>
            <div className="create-field-dropdown__datatype-option">
              <FieldTypeBadge dataType={datatype as FieldDataType} description={humanize(datatype)} disableTooltip />
              <span>{humanize(datatype)}</span>
            </div>
          </Tooltip>
        </Option>
      );
    });

    return options;
  }, [synapseField]);

  const onChange = (event: ChangeEvent<HTMLInputElement>) => {
    setFormState((previousFormState) => ({
      ...previousFormState,
      [event.target.name]:
        event.target.type === AppConstants.INPUT_TYPE.CHECKBOX ? event.target.checked : event.target.value,
    }));
  };

  const onDatatypeChange = (value: FieldDataType) => {
    setFormState((previousFormState) => ({
      ...previousFormState,
      dataType: value,
    }));
  };

  const onApiNameChange = (event: ChangeEvent<HTMLInputElement>) => {
    if (!event.target.value.match(apiNameRegEx)) {
      setFormState((previousFormState) => ({
        ...previousFormState,
        apiName: event.target.value,
      }));
    }
  };

  const autopopulateApiName = useCallback(() => {
    if (!formState.apiName) {
      setFormState((previousFormState) => ({
        ...previousFormState,
        apiName: createApiName(previousFormState.displayName),
      }));
    }
  }, [formState.apiName]);

  const handleClose = () => {
    setFormState(initialFieldState);
    dispatch(showCreateField({ id: '', mode, position, visible: false }));
  };

  const onCreate = () => {
    setSelectedValue(formState.displayName);
    changeHandler(id, formState.displayName);
    addNewCustomField(formState);
    handleClose();
  };

  const filterOption = (input: string, option: React.ReactElement<OptionProps>) => {
    return option?.props?.value ? option.props.value.toString().toLowerCase().indexOf(input.toLowerCase()) >= 0 : false;
  };

  const footer = (
    <>
      <Button onClick={handleClose}>{tc('cancel')}</Button>
      <Button onClick={onCreate} disabled={!formState.displayName || !formState.apiName} type="primary">
        {mode === CreateFieldModalMode.CREATE && tc('create')}
        {mode === CreateFieldModalMode.EDIT && tc('save')}
      </Button>
    </>
  );

  // Reset form state
  useEffect(() => {
    if (visible) {
      setFormState(data ?? initialFieldState);
      setDisplayNameVisited(false);
      setApiNameVisited(false);
    }
  }, [data, visible]);

  // Autofill form from synapseField if applicable
  useEffect(() => {
    if (visible && synapseField && isUndefined(data)) {
      setFormState((previousFormState) => ({
        ...previousFormState,
        displayName: synapseField.displayName,
        apiName: createApiName(synapseField.displayName),
        dataType: synapseField.dataType,
        isMultivalued: synapseField.multiValueField,
      }));
    }
  }, [data, synapseField, visible]);

  // Adjust the position of the modal if it overflows off the screen
  useEffect(() => {
    if (position) {
      const newPosition: ModalPosition = {
        top: position.top ?? 0,
        left: position.left ?? 0,
      };

      if (newPosition.top + MODAL_HEIGHT > window.innerHeight) {
        newPosition.top = window.innerHeight - MODAL_HEIGHT;
      }

      setModalPosition(newPosition);
    }
  }, [position]);

  if (!id) {
    return null;
  }

  return (
    <Modal
      style={{
        top: modalPosition?.top,
        left: modalPosition?.left,
      }}
      className={cx('create-field-dropdown', visible && 'create-field-dropdown--expanding')}
      footer={footer}
      visible={visible}
      closable={false}
      mask={false}
      transitionName=""
      onCancel={handleClose}
      width={position?.width}>
      <div className="create-field-dropdown__content">
        <InputWithLabel
          name="displayName"
          datatype={AppConstants.INPUT_TYPE.STRING}
          label={tn('display_name')}
          value={formState.displayName}
          onChange={onChange}
          validateStatus={displayNameStatus}
          help={displayNameHelp}
          onBlur={() => {
            setDisplayNameVisited(true);
            autopopulateApiName();
          }}
          required
        />
        <InputWithLabel
          name="apiName"
          datatype={AppConstants.INPUT_TYPE.STRING}
          label={tn('api_name')}
          value={formState.apiName}
          onChange={onApiNameChange}
          validateStatus={apiNameStatus}
          help={apiNameHelp}
          onBlur={() => {
            setApiNameVisited(true);
          }}
          required
        />
        <InputWithLabel
          name="dataType"
          datatype={AppConstants.INPUT_TYPE.PICKLIST}
          label={tn('data_type')}
          value={formState.dataType}
          onChange={onDatatypeChange}
          required
          options={datatypeOptions}
          filterOption={filterOption}
        />
        <InputWithLabel
          name="isMultivalued"
          className="create-field-dropdown__checkbox"
          datatype={AppConstants.INPUT_TYPE.CHECKBOX}
          checked={formState.isMultivalued}
          onChange={onChange}
          label={tn('multivalued')}
        />
        <InputWithLabel
          name="isRequired"
          className="create-field-dropdown__checkbox"
          datatype={AppConstants.INPUT_TYPE.CHECKBOX}
          checked={formState.isRequired}
          onChange={onChange}
          label={tn('required')}
        />
      </div>
    </Modal>
  );
};
