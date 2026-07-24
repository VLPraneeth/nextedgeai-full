//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { keyBy, mapValues } from 'lodash';

import { withI18n } from 'components/I18nProvider';
import { render, userEvent } from 'tests/helpers';

import { pipelineSchemas as entities } from '../PipelinePicker.fixtures';
import PipelinePickerFieldsInitial from '../PipelinePickerFields';

const PipelinePickerFields = withI18n(PipelinePickerFieldsInitial, 'PipelinePicker');

test('PipelinePickerFields renders checkboxes and calls update events', async () => {
  const entity = entities[0];
  const targetField = entity.fields[0];
  const fields = mapValues(keyBy(entity.fields, 'id'), () => false);

  const updateSelectedItem = jest.fn();

  const { findByText } = render(
    <PipelinePickerFields
      entity={entities[0]}
      selectedItems={fields}
      updateSelectedItem={updateSelectedItem}
      hasTopFilter={false}
    />
  );

  const fieldCheckbox = await findByText(targetField.displayName);
  await userEvent.click(fieldCheckbox);

  expect(updateSelectedItem).toHaveBeenCalledWith(true, entity.id, targetField.id);
});

test('PipelinePickerFields hides fields when search is active', async () => {
  const entity = entities[0];
  const targetField = entity.fields[0];
  const fields = mapValues(keyBy(entity.fields, 'id'), () => false);

  const updateSelectedItem = jest.fn();

  const { findByPlaceholderText, findByText, queryByText } = render(
    <PipelinePickerFields
      entity={entities[0]}
      selectedItems={fields}
      updateSelectedItem={updateSelectedItem}
      hasTopFilter={false}
    />
  );

  const fieldCheckbox = await findByText(targetField.displayName);
  expect(fieldCheckbox).toBeInTheDocument();

  const filter = await findByPlaceholderText('Filter…');
  expect(filter).toBeInTheDocument();

  await userEvent.type(filter, 'Text that no field will match');

  expect(queryByText(targetField.displayName)).not.toBeInTheDocument();
});

test('PipelinePickerFields filters by apiName and displayName', async () => {
  const entity = entities[0];
  const targetField = entity.fields[0];
  const fields = mapValues(keyBy(entity.fields, 'id'), () => false);

  const updateSelectedItem = jest.fn();

  const { findByPlaceholderText, findByText } = render(
    <PipelinePickerFields
      entity={entities[0]}
      selectedItems={fields}
      updateSelectedItem={updateSelectedItem}
      hasTopFilter={false}
    />
  );

  const fieldCheckbox = await findByText(targetField.displayName);
  expect(fieldCheckbox).toBeInTheDocument();

  const filter = await findByPlaceholderText('Filter…');
  expect(filter).toBeInTheDocument();

  await userEvent.type(filter, targetField.displayName);

  const fieldByDisplayName = await findByText(targetField.displayName);
  expect(fieldByDisplayName).toBeInTheDocument();

  await userEvent.clear(filter);
  await userEvent.type(filter, targetField.apiName);

  const fieldByApiName = await findByText(targetField.displayName);
  expect(fieldByApiName).toBeInTheDocument();
});
