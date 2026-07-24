//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { FieldDataType } from 'components/types';
import { FetchStatus } from 'store/types';

import { CreateFieldModal } from './slice';

export interface Mapping {
  id: string;
  synapseId: string;
  synapseName?: string;
  synapseEntityId: string;
  synapseEntityDisplayName?: string;
  synapseEntityApiName?: string;
  synapseFieldId: string;
  synapseFieldApiName?: string;
  synapseFieldDisplayName?: string;
  synapseFieldDatatype?: FieldDataType;
  syncDirectionId: string;
  directions?: string[];
  syncariFieldId: string;
  syncariFieldApiName?: string;
  syncariFieldDisplayName?: string;
  syncariFieldDatatype?: string;
  syncariFieldIsRequired?: boolean;
  syncariFieldIsMultiValued?: boolean;
  synapseReadOnly?: boolean;
  syncariReadOnly?: boolean;
  errorMessage?: string;
  edited?: boolean;
  safeErrorMessage?: boolean;
  createNewSyncariField?: boolean;
}

export interface ServerMapping
  extends Omit<
    Mapping,
    'syncDirectionId' | 'synapseReadOnly' | 'syncariReadOnly' | 'errorMessage' | 'safeErrorMessage'
  > {
  syncariFieldIsRequired?: boolean;
  syncariFieldIsMultiValued?: boolean;
}

export interface EditedServerMapping {
  existing: ServerMapping;
  updated: ServerMapping;
}

export interface MappingError extends Partial<Mapping> {
  id: string;
  errorMessage: string;
}

export interface MappingsResponse {
  success: boolean;
  error?: MappingError[];
  result?: ServerMapping[];
  newEntityDraft?: boolean;
  entityDraftUpdated?: boolean;
}

export interface FastMapperState {
  createFieldModal: CreateFieldModal;
  deleteMappingsResponse?: MappingsResponse | null;
  editMappingsStatus: FetchStatus;
  editMappingsErrorMessage?: string;
  editMappingsResponse?: MappingsResponse | null;
  fastMapperVisible: boolean;
  fastMapperEntityId: string;
  mappings?: Required<ServerMapping>[] | null;
  mappingsStatus: FetchStatus;
  saveMappingsStatus: FetchStatus;
  saveMappingsErrorMessage?: string;
  saveMappingsResponse?: MappingsResponse | null;
}
