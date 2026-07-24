import { ColDef, GetQuickFilterTextParams } from 'ag-grid-community';
import { find } from 'lodash';

import { Mapping } from 'store/fast-mapper';
import { tNamespaced } from 'utils/i18nUtil';

import { FastMapperMode } from '../FastMapperModal';
import { CellAction, ConnectorRenderer, ConnectorEntityEditor, MapperFields, getDirections } from '../Mapper';

const tn = tNamespaced('AddMapping');

export const getColumns = (mappings: Mapping[] | undefined) => {
  return [
    {
      headerName: '',
      field: 'select',
      minWidth: 48,
      maxWidth: 48,
      checkboxSelection: true,
      headerCheckboxSelection: true,
      suppressMovable: true,
      headerCheckboxSelectionFilteredOnly: true,
    },
    {
      headerName: tn('synapse_name'),
      field: MapperFields.SYNAPSE_ID,
      editable: true,
      cellRendererFramework: ConnectorRenderer,
      cellRendererParams: {
        fieldId: MapperFields.SYNAPSE_ID,
        mode: FastMapperMode.BROWSE,
      },
      cellEditorFramework: ConnectorEntityEditor,
      cellEditorParams: {
        fieldId: MapperFields.SYNAPSE_ID,
        mode: FastMapperMode.BROWSE,
      },
      suppressKeyboardEvent: () => true,
      suppressMovable: true,
      resizable: true,
      sortable: true,
      comparator: (valueA, valueB, nodeA, nodeB) =>
        nodeA.data[MapperFields.SYNAPSE_NAME]?.localeCompare(nodeB.data[MapperFields.SYNAPSE_NAME]),
      getQuickFilterText: (params: GetQuickFilterTextParams) => {
        return params.data.synapseName;
      },
    },
    {
      headerName: tn('synapse_entity'),
      field: MapperFields.SYNAPSE_ENTITY_ID,
      editable: true,
      cellRendererFramework: ConnectorRenderer,
      cellRendererParams: {
        fieldId: MapperFields.SYNAPSE_ENTITY_ID,
        mode: FastMapperMode.BROWSE,
        showApiName: false,
      },
      cellEditorFramework: ConnectorEntityEditor,
      cellEditorParams: {
        fieldId: MapperFields.SYNAPSE_ENTITY_ID,
        mode: FastMapperMode.BROWSE,
      },
      suppressKeyboardEvent: () => true,
      suppressMovable: true,
      resizable: true,
      sortable: true,
      comparator: (valueA, valueB, nodeA, nodeB) =>
        nodeA.data[MapperFields.SYNAPSE_ENTITY_DISPLAY_NAME]?.localeCompare(
          nodeB.data[MapperFields.SYNAPSE_ENTITY_DISPLAY_NAME]
        ),
      getQuickFilterText: (params: GetQuickFilterTextParams) => {
        let filterText = '';
        if (params.data) {
          const { synapseEntityDisplayName, synapseEntityApiName } = params.data;
          if (synapseEntityDisplayName && synapseEntityApiName) {
            filterText = `${synapseEntityDisplayName} ${synapseEntityApiName}`;
          }
        }
        return filterText;
      },
    },
    {
      headerName: tn('synapse_field'),
      field: MapperFields.SYNAPSE_FIELD_ID,
      editable: true,
      cellRendererFramework: ConnectorRenderer,
      cellRendererParams: {
        fieldId: MapperFields.SYNAPSE_FIELD_ID,
        mode: FastMapperMode.BROWSE,
        showApiName: false,
      },
      cellEditorFramework: ConnectorEntityEditor,
      cellEditorParams: {
        fieldId: MapperFields.SYNAPSE_FIELD_ID,
        mode: FastMapperMode.BROWSE,
      },
      suppressKeyboardEvent: () => true,
      suppressMovable: true,
      resizable: true,
      sortable: true,
      comparator: (valueA, valueB, nodeA, nodeB) =>
        nodeA.data[MapperFields.SYNAPSE_FIELD_DISPLAY_NAME]?.localeCompare(
          nodeB.data[MapperFields.SYNAPSE_FIELD_DISPLAY_NAME]
        ),
      getQuickFilterText: (params: GetQuickFilterTextParams) => {
        let filterText = '';
        if (params.data) {
          const { synapseFieldDisplayName, synapseFieldApiName, synapseFieldDatatype } = params.data;
          if (synapseFieldDisplayName && synapseFieldApiName && synapseFieldDatatype) {
            filterText = `${synapseFieldDisplayName} ${synapseFieldApiName} ${synapseFieldDatatype}`;
          }
        }
        return filterText;
      },
    },
    {
      headerName: tn('sync_direction'),
      field: MapperFields.SYNC_DIRECTION_ID,
      editable: true,
      cellRendererFramework: ConnectorRenderer,
      cellRendererParams: {
        fieldId: MapperFields.SYNC_DIRECTION_ID,
        mode: FastMapperMode.BROWSE,
        showApiName: false,
        initialMappings: mappings,
      },
      cellEditorFramework: ConnectorEntityEditor,
      cellEditorParams: {
        fieldId: MapperFields.SYNC_DIRECTION_ID,
        mode: FastMapperMode.BROWSE,
        initialMappings: mappings,
      },
      maxWidth: 138,
      suppressKeyboardEvent: () => true,
      suppressMovable: true,
      resizable: true,
      sortable: true,
      getQuickFilterText: (data: GetQuickFilterTextParams) => {
        const direction = find(getDirections(), { id: data.value });
        return direction?.displayName || data.value;
      },
    },
    {
      headerName: tn('syncari_entity_field', { interpolation: { escapeValue: false } }),
      field: MapperFields.SYNCARI_ENTITY_FIELD_ID,
      editable: true,
      cellRendererFramework: ConnectorRenderer,
      cellRendererParams: {
        fieldId: MapperFields.SYNCARI_ENTITY_FIELD_ID,
        mode: FastMapperMode.BROWSE,
        showApiName: false,
      },
      cellEditorFramework: ConnectorEntityEditor,
      cellEditorParams: {
        fieldId: MapperFields.SYNCARI_ENTITY_FIELD_ID,
        mode: FastMapperMode.BROWSE,
      },
      suppressKeyboardEvent: () => true,
      suppressMovable: true,
      resizable: true,
      sortable: true,
      comparator: (valueA, valueB, nodeA, nodeB) =>
        nodeA.data[MapperFields.SYNCARI_ENTITY_FIELD_DISPLAY_NAME]?.localeCompare(
          nodeB.data[MapperFields.SYNCARI_ENTITY_FIELD_DISPLAY_NAME]
        ),
      getQuickFilterText: (params: GetQuickFilterTextParams) => {
        let filterText = '';
        if (params.data) {
          const { syncariFieldDisplayName, syncariFieldApiName, syncariFieldDatatype } = params.data;
          if (syncariFieldDisplayName && syncariFieldApiName && syncariFieldDatatype) {
            filterText = `${syncariFieldDisplayName} ${syncariFieldApiName} ${syncariFieldDatatype}`;
          }
        }
        return filterText;
      },
    },
    {
      headerName: '',
      field: MapperFields.ERROR_MESSAGE,
      minWidth: 48,
      maxWidth: 48,
      cellRendererFramework: CellAction,
      cellRendererParams: {
        mode: FastMapperMode.BROWSE,
      },
      cellClass: 'cell-action',
      suppressMovable: true,
      editable: false,
    },
  ] as ColDef[];
};
