import { ColDef } from 'ag-grid-community';

import { tNamespaced } from 'utils/i18nUtil';

import { FastMapperMode } from '../FastMapperModal';
import { CellAction, ConnectorRenderer, ConnectorEntityEditor, MapperFields } from '../Mapper';
import { ValidColumnDefFieldId } from '../Mapper/Mapper.types';

const tn = tNamespaced('AddMapping');

export const getColumns = (focusedField: ValidColumnDefFieldId | undefined) => {
  return [
    {
      headerName: '',
      field: 'select',
      minWidth: 48,
      maxWidth: 48,
      checkboxSelection: true,
      headerCheckboxSelection: true,
      suppressMovable: true,
    },
    {
      headerName: tn('synapse_name'),
      field: MapperFields.SYNAPSE_ID,
      editable: true,
      cellRendererFramework: ConnectorRenderer,
      cellRendererParams: {
        fieldId: MapperFields.SYNAPSE_ID,
        mode: FastMapperMode.ADD,
      },
      cellEditorFramework: ConnectorEntityEditor,
      cellEditorParams: {
        fieldId: MapperFields.SYNAPSE_ID,
        mode: FastMapperMode.ADD,
        focusedField,
      },
      suppressKeyboardEvent: () => true,
      suppressMovable: true,
      resizable: true,
    },
    {
      headerName: tn('synapse_entity'),
      field: MapperFields.SYNAPSE_ENTITY_ID,
      editable: true,
      cellRendererFramework: ConnectorRenderer,
      cellRendererParams: {
        fieldId: MapperFields.SYNAPSE_ENTITY_ID,
        mode: FastMapperMode.ADD,
        showApiName: false,
      },
      cellEditorFramework: ConnectorEntityEditor,
      cellEditorParams: {
        fieldId: MapperFields.SYNAPSE_ENTITY_ID,
        mode: FastMapperMode.ADD,
        focusedField,
      },
      suppressKeyboardEvent: () => true,
      suppressMovable: true,
      resizable: true,
    },
    {
      headerName: tn('synapse_field'),
      field: MapperFields.SYNAPSE_FIELD_ID,
      editable: true,
      cellRendererFramework: ConnectorRenderer,
      cellRendererParams: {
        fieldId: MapperFields.SYNAPSE_FIELD_ID,
        mode: FastMapperMode.ADD,
        showApiName: false,
      },
      cellEditorFramework: ConnectorEntityEditor,
      cellEditorParams: {
        fieldId: MapperFields.SYNAPSE_FIELD_ID,
        mode: FastMapperMode.ADD,
        focusedField,
      },
      suppressKeyboardEvent: () => true,
      suppressMovable: true,
      resizable: true,
    },
    {
      headerName: tn('sync_direction'),
      field: MapperFields.SYNC_DIRECTION_ID,
      editable: true,
      cellRendererFramework: ConnectorRenderer,
      cellRendererParams: {
        fieldId: MapperFields.SYNC_DIRECTION_ID,
        mode: FastMapperMode.ADD,
        showApiName: false,
      },
      cellEditorFramework: ConnectorEntityEditor,
      cellEditorParams: {
        fieldId: MapperFields.SYNC_DIRECTION_ID,
        mode: FastMapperMode.ADD,
        focusedField,
      },
      maxWidth: 138,
      suppressKeyboardEvent: () => true,
      suppressMovable: true,
      resizable: true,
    },
    {
      headerName: tn('syncari_entity_field', { interpolation: { escapeValue: false } }),
      field: MapperFields.SYNCARI_ENTITY_FIELD_ID,
      editable: true,
      cellRendererFramework: ConnectorRenderer,
      cellRendererParams: {
        fieldId: MapperFields.SYNCARI_ENTITY_FIELD_ID,
        mode: FastMapperMode.ADD,
        showApiName: false,
      },
      cellEditorFramework: ConnectorEntityEditor,
      cellEditorParams: {
        fieldId: MapperFields.SYNCARI_ENTITY_FIELD_ID,
        mode: FastMapperMode.ADD,
        focusedField,
      },
      suppressKeyboardEvent: () => true,
      suppressMovable: true,
      resizable: true,
    },
    {
      headerName: '',
      field: MapperFields.ERROR_MESSAGE,
      minWidth: 48,
      maxWidth: 48,
      cellRendererFramework: CellAction,
      cellRendererParams: {
        mode: FastMapperMode.ADD,
      },
      cellClass: 'cell-action',
      suppressMovable: true,
    },
  ] as ColDef[];
};
