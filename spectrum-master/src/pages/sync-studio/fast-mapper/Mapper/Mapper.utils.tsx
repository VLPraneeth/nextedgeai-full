import Select from 'antd/lib/select';
import isEqual from 'fast-deep-equal';
import produce from 'immer';

import InlineSvg from 'components/icons/InlineSvg';
import { FieldDataType } from 'components/types';
import { ConnectorMetadata } from 'reducers/connectorReducer';
import { EMPTY_ARRAY } from 'store/constants';
import { Mapping, ServerMapping, MappingError, EditedServerMapping } from 'store/fast-mapper/types';
import { createUniqueEntityTitle } from 'utils/FieldUtil';
import { tNamespaced, tc } from 'utils/i18nUtil';

import { DirectionId } from '../../types';
import { convertToDirections } from '../FastMapper.util';
import { DirectionOption as DO, EditedMapping } from '../types';
import { CellItem } from './Mapper.renderers';
import { DirectionOption, SynapseEntityOption, SynapseOption } from './Mapper.types';
import { MapperFields } from './Mapper.types';

const tn = tNamespaced('AddMapping');

export const getDirections = (): DO[] => {
  return [
    {
      id: DirectionId.SYNC_TO,
      displayName: tn('destination'),
    },
    {
      id: DirectionId.SYNC_FROM,
      displayName: tn('source'),
    },
    {
      id: DirectionId.BIDIRECTIONAL,
      displayName: tn('both'),
    },
  ];
};

export const getDirectionOptions = (row?: Mapping) => {
  const directions = getDirections();

  // User has to select synapse field before the direction can be selected
  if (!row || typeof row?.synapseReadOnly === 'undefined') {
    return EMPTY_ARRAY;
  }

  return directions.filter((direction) => {
    // The only direction we are currently filtering is the "Destination" mapping
    // if the source is readOnly.
    if (row?.synapseReadOnly && direction.id !== DirectionId.SYNC_FROM) {
      return false;
    }

    return true;
  });
};

export const makeDirectionOption = ({ id, displayName, icon }: DirectionOption) => {
  return (
    <Select.Option key={id} value={id} title={displayName}>
      <CellItem title={displayName} />
    </Select.Option>
  );
};

export const makeSynapseEntityOption = ({ id, displayName, apiName }: SynapseEntityOption) => {
  return (
    <Select.Option key={id} value={id} title={createUniqueEntityTitle(displayName, apiName)}>
      <div className="synri-field-option">
        <span className="synri-field-option-display-name">{displayName}</span>
        <span className="synri-field-option-api-name">({apiName})</span>
      </div>
    </Select.Option>
  );
};

export const makeSynapseOption = (connectorIdToMetadataMap: Record<string, ConnectorMetadata>) => ({
  id,
  name,
  iconUri,
  iconTitle,
  connectorId,
}: SynapseOption) => {
  const showDraftTag =
    connectorIdToMetadataMap[connectorId]?.custom && connectorIdToMetadataMap[connectorId]?.draftStatus !== 'APPROVED';

  return (
    <Select.Option key={id} value={id} title={name}>
      <CellItem
        title={name}
        prefix={iconUri && iconTitle && <InlineSvg src={iconUri} title={iconTitle} />}
        showDraftTag={showDraftTag}
      />
    </Select.Option>
  );
};

export const makeDropdownHeader = (key: string, title: string) => {
  return (
    <Select.Option disabled className="mapper__dropdown-header" key={key}>
      {title}
      <div />
    </Select.Option>
  );
};

export const removeAutomaticallyAddedRow = (rows: Mapping[]) => {
  if (rows.length <= 1) {
    return rows;
  }
  const secondLastRow = rows[rows.length - 2];
  const lastRow = rows[rows.length - 1];
  if (
    lastRow[MapperFields.SYNAPSE_ID] === secondLastRow[MapperFields.SYNAPSE_ID] &&
    lastRow[MapperFields.SYNAPSE_ENTITY_ID] === secondLastRow[MapperFields.SYNAPSE_ENTITY_ID] &&
    !lastRow[MapperFields.SYNAPSE_FIELD_ID] &&
    !lastRow[MapperFields.SYNC_DIRECTION_ID] &&
    !lastRow[MapperFields.SYNCARI_ENTITY_FIELD_ID]
  ) {
    return produce(rows, (draft) => {
      draft.splice(draft.length - 1, 1);
    });
  }
  return rows;
};

export const validate = (values: Mapping[]) => {
  const errors: MappingError[] = [];
  const trimmedValues = removeAutomaticallyAddedRow(values);
  // Validate for empty
  trimmedValues.forEach((val) => {
    const emptyFields: string[] = [];
    if (!val[MapperFields.SYNAPSE_ID]?.length) {
      emptyFields.push(tn('synapse_name'));
    }
    if (!val[MapperFields.SYNAPSE_ENTITY_ID]?.length) {
      emptyFields.push(tn('synapse_entity'));
    }
    if (!val[MapperFields.SYNAPSE_FIELD_ID]?.length) {
      emptyFields.push(tn('synapse_field'));
    }
    if (!val[MapperFields.SYNC_DIRECTION_ID]?.length) {
      emptyFields.push(tn('sync_direction'));
    }
    if (!val[MapperFields.SYNCARI_ENTITY_FIELD_ID]?.length) {
      emptyFields.push(tn('syncari_entity_field'));
    }
    emptyFields.length &&
      errors.push({
        id: val.id,
        safeErrorMessage: true,
        errorMessage: tc('cannot_be_empty', { name: emptyFields.join(', ') }),
      });
  });

  // Validate for duplicates
  trimmedValues.forEach((o, oIdx: number) => {
    const { id: oId, ...restO } = o;
    var eq = trimmedValues.find((i, iIdx: number) => {
      const { id: iId, ...restE } = i;
      if (oIdx > iIdx) {
        return isEqual(restE, restO);
      }
      return false;
    });
    if (eq) {
      errors.push({
        id: oId,
        errorMessage: tn('duplicate_mapping'),
      });
    }
    return o;
  });
  return errors;
};

export const getSerializedValue = (value: Mapping): ServerMapping => {
  const {
    id,
    synapseId,
    synapseEntityId,
    synapseFieldId,
    syncDirectionId,
    syncariFieldId,
    createNewSyncariField,
    syncariFieldDisplayName,
    syncariFieldApiName,
    syncariFieldDatatype,
    syncariFieldIsMultiValued,
    syncariFieldIsRequired,
  } = value;

  return {
    id,
    synapseId,
    synapseEntityId,
    synapseFieldId,
    directions: convertToDirections(syncDirectionId),
    syncariFieldId,
    createNewSyncariField: !!createNewSyncariField,
    syncariFieldDisplayName,
    syncariFieldApiName,
    syncariFieldDatatype: syncariFieldDatatype as FieldDataType,
    syncariFieldIsMultiValued,
    syncariFieldIsRequired,
  };
};

export const getSerializedValues = (values: Mapping[]): ServerMapping[] => {
  return removeAutomaticallyAddedRow(values).map(getSerializedValue);
};

export const getSerializedEditedValues = (editedValues: EditedMapping[]): EditedServerMapping[] => {
  return editedValues.map((editedValues) => {
    return {
      existing: getSerializedValue(editedValues.existing),
      updated: getSerializedValue(editedValues.updated),
    } as EditedServerMapping;
  });
};
