import { pick } from 'lodash';

import { DirectionId } from 'pages/sync-studio/types';
import { tNamespaced } from 'utils/i18nUtil';

const tn = tNamespaced('ExportFieldMappingsButton');
const ta = tNamespaced('AddMapping');

export const useExportedFields = () => {
  const exportedFields = [
    { field: 'synapseType', headerName: tn('synapse_type') },
    { field: 'synapseName', headerName: ta('synapse_name') },
    { field: 'synapseEntityDisplayName', headerName: tn('synapse_entity_display_name') },
    { field: 'synapseEntityApiName', headerName: tn('synapse_entity_api_name') },
    { field: 'synapseFieldDisplayName', headerName: tn('synapse_field_display_name') },
    { field: 'synapseFieldApiName', headerName: tn('synapse_field_api_name') },
    { field: 'synapseFieldDatatype', headerName: tn('synapse_field_data_type') },
    { field: 'syncariFieldDisplayName', headerName: tn('syncari_field_display_name') },
    { field: 'syncariFieldApiName', headerName: tn('syncari_field_api_name') },
    { field: 'syncariFieldDatatype', headerName: tn('syncari_field_data_type') },
    { field: 'syncDirectionId', headerName: ta('sync_direction') },
  ];

  const humanizeSyncDirectionId = (syncDirectionId: string) => {
    switch (syncDirectionId) {
      case DirectionId.SYNC_TO:
        return ta('destination');
      case DirectionId.SYNC_FROM:
        return ta('source');
      case DirectionId.BIDIRECTIONAL:
        return ta('bidirectional');
      default:
        return syncDirectionId;
    }
  };

  return { exportedFields, humanizeSyncDirectionId };
};

export const generateMappingCSVData = (
  data: Record<string, string>[],
  exportedFields: { field: string; headerName: string }[]
) => {
  const fieldMap: Record<string, string> = {};

  // Generate the field map
  exportedFields.forEach((field) => {
    if (field.field) {
      fieldMap[field.field] = field.headerName;
    }
  });

  // Transform object keys to display names
  return data
    .map((datum) =>
      pick(
        datum,
        exportedFields.map((field) => field.field)
      )
    )
    .map((datum) => {
      const transformedDatum: Record<string, string> = {};

      Object.keys(datum).forEach((key) => {
        transformedDatum[fieldMap[key]] = datum[key];
      });

      return transformedDatum;
    });
};
