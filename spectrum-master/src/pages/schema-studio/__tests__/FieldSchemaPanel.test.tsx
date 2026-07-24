//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { getEmptySchemaField } from 'store/schema';
import { initialState } from 'store/schema/slice';
import { FieldModel } from 'store/schema/types';
import { render, screen } from 'tests/helpers';
import { tNamespaced } from 'utils/i18nUtil';

import FieldSchemaPanel from '../FieldSchemaPanel';

const tn = tNamespaced('FieldSchemaModal');

const entityId = '5ef7dc5f8c3f9729293ee467';

const testState = {
  testState: {
    schema: {
      connectorEntitySchemas: {
        [entityId]: {},
      },
    },
    schemaSlice: initialState,
    entityPipeline: {
      fieldDraftSummary: {},
    },
  },
};

describe('Field Schema Panel', () => {
  test('Field schema panel renders a field', async () => {
    const field = getEmptySchemaField();

    render(
      <FieldSchemaPanel isSyncariConnector entityId={entityId} field={field} editField={(entity: FieldModel) => {}} />,
      testState
    );

    expect(await screen.findByText(field.displayName)).toBeInTheDocument();
  });

  test('Field schema panel delete button should be disabled if not Syncari defined', async () => {
    const field = getEmptySchemaField({ isSyncariDefined: false });

    render(
      <FieldSchemaPanel
        isSyncariConnector={false}
        entityId={entityId}
        field={field}
        editField={(entity: FieldModel) => {}}
      />,
      testState
    );

    expect(await screen.findByTestId('action-name-Delete Field')).toHaveAttribute('aria-disabled', 'true');
  });

  test('Field schema panel delete button should be disabled if isSyncariConnector', async () => {
    const field = getEmptySchemaField({ isSyncariDefined: false });

    render(
      <FieldSchemaPanel isSyncariConnector entityId={entityId} field={field} editField={(entity: FieldModel) => {}} />,
      testState
    );

    expect(await screen.findByTestId('action-name-Delete Field')).toHaveAttribute('aria-disabled', 'true');
  });

  test('Field schema panel delete button should be enabled if isSyncariDefined', async () => {
    const field = getEmptySchemaField({ isSyncariDefined: true, hasDraft: true });

    render(
      <FieldSchemaPanel
        isSyncariConnector={false}
        entityId={entityId}
        field={field}
        editField={(entity: FieldModel) => {}}
      />,
      testState
    );

    expect(await screen.findByTestId('action-name-Delete Field')).toHaveAttribute('aria-disabled', 'false');
  });
  test('Field schema panel delete button should be disabled if no draft exists', async () => {
    const field = getEmptySchemaField({ isSyncariDefined: true, hasDraft: false });

    render(
      <FieldSchemaPanel
        isSyncariConnector={false}
        entityId={entityId}
        field={field}
        editField={(entity: FieldModel) => {}}
      />,
      testState
    );

    expect(await screen.findByTestId('action-name-Delete Field')).toHaveAttribute('aria-disabled', 'true');
  });

  test('Field schema panel ID Field option should exist for string types', async () => {
    const field = getEmptySchemaField({ dataType: 'string' });

    render(
      <FieldSchemaPanel
        isSyncariConnector={false}
        entityId={entityId}
        field={field}
        editField={(entity: FieldModel) => {}}
      />,
      testState
    );

    const idLabel = await screen.findByText(tn('id_field'));
    expect(idLabel).toBeInTheDocument();
  });

  test('Field schema panel ID Field option should not exist for boolean types', async () => {
    const field = getEmptySchemaField({ dataType: 'boolean' });

    render(
      <FieldSchemaPanel
        isSyncariConnector={false}
        entityId={entityId}
        field={field}
        editField={(entity: FieldModel) => {}}
      />,
      testState
    );

    const idLabel = screen.queryByText(tn('id_field'));
    expect(idLabel).not.toBeInTheDocument();
  });
});
