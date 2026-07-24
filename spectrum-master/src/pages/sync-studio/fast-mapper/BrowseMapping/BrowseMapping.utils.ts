import { Mapping, ServerMapping } from 'store/fast-mapper';

export const serverMappingsToMappings = (serverMappings: Required<ServerMapping>[] | null | undefined[]) => {
  const mappings: Mapping[] = [];

  if (serverMappings) {
    serverMappings.forEach((serverMapping) => {
      if (serverMapping) {
        mappings.push({
          id: serverMapping.id,
          synapseId: serverMapping.synapseId,
          synapseName: serverMapping.synapseName,
          synapseEntityDisplayName: serverMapping.synapseEntityDisplayName,
          synapseEntityApiName: serverMapping.synapseEntityApiName,
          synapseFieldApiName: serverMapping.synapseFieldApiName,
          synapseFieldDisplayName: serverMapping.synapseFieldDisplayName,
          synapseFieldDatatype: serverMapping.synapseFieldDatatype,
          directions: serverMapping.directions,
          synapseEntityId: serverMapping.synapseEntityId,
          synapseFieldId: serverMapping.synapseFieldId,
          synapseReadOnly: false, // TODO: maybe need to change
          // @ts-ignore
          syncDirectionId: serverMapping.syncDirectionId,
          syncariFieldId: serverMapping.syncariFieldId,
          syncariFieldApiName: serverMapping.syncariFieldApiName,
          syncariFieldDisplayName: serverMapping.syncariFieldDisplayName,
          syncariFieldDatatype: serverMapping.syncariFieldDatatype,
          syncariFieldIsRequired: serverMapping.syncariFieldIsRequired,
          syncariFieldIsMultiValued: serverMapping.syncariFieldIsMultiValued,
        });
      }
    });
  }

  return mappings;
};
