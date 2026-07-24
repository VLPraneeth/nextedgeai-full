export enum MapperFields {
  SYNAPSE_ID = 'synapseId',
  SYNAPSE_NAME = 'synapseName',
  SYNAPSE_ENTITY_ID = 'synapseEntityId',
  SYNAPSE_ENTITY_DISPLAY_NAME = 'synapseEntityDisplayName',
  SYNAPSE_FIELD_ID = 'synapseFieldId',
  SYNAPSE_FIELD_DISPLAY_NAME = 'synapseFieldDisplayName',
  SYNC_DIRECTION_ID = 'syncDirectionId',
  SYNCARI_ENTITY_FIELD_ID = 'syncariFieldId',
  SYNCARI_ENTITY_FIELD_DISPLAY_NAME = 'syncariFieldDisplayName',
  ERROR_MESSAGE = 'errorMessage',
}

export type ValidColumnDefFieldId =
  | MapperFields.SYNAPSE_ID
  | MapperFields.SYNAPSE_ENTITY_ID
  | MapperFields.SYNAPSE_FIELD_ID
  | MapperFields.SYNC_DIRECTION_ID
  | MapperFields.SYNCARI_ENTITY_FIELD_ID;

export interface DirectionOption {
  id: string;
  displayName: string;
  icon: React.ReactElement;
}

export interface SynapseEntityOption extends Omit<DirectionOption, 'icon'> {
  apiName: string;
}

export interface SynapseOption extends Omit<DirectionOption, 'icon' | 'displayName'> {
  name: string;
  iconUri: string;
  iconTitle: string;
  connectorId: string;
}

export type ChangeHandler = (
  id: string,
  value: string,
  options?: { displayName?: string; readOnly?: boolean; apiName?: string; dataType?: string }
) => void;
