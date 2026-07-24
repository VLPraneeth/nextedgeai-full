//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { waitFor } from '@testing-library/react';
import produce from 'immer';
import { sumBy } from 'lodash';

import { render, userEvent } from 'tests/helpers';

import { TranslatedPipelinePicker as PipelinePicker } from '../PipelinePicker';
import { pipelinePickerEntityValue, pipelineSchemas as entities } from '../PipelinePicker.fixtures';

test('PipelinePicker renders tree and selects all fields pipelines when entity is selected', async () => {
  const entity = entities[4];

  const { findByText, findAllByText } = render(<PipelinePicker entities={entities} />);

  const totalEntities = entities.length;

  let unselectedFieldsText = await findAllByText(`0 selected`);
  expect(unselectedFieldsText).toHaveLength(totalEntities);

  const entityCheckbox = await findByText(entity.displayName);
  await userEvent.click(entityCheckbox);

  unselectedFieldsText = await findAllByText(`0 selected`);
  expect(unselectedFieldsText).toHaveLength(totalEntities - 1);

  const fieldsLength = entity.fields.length;
  const selectedFieldsText = await findByText(`${fieldsLength} selected`);
  expect(selectedFieldsText).toBeInTheDocument();
});

test('PipelinePicker shows results for all entities', async () => {
  const targetFieldText = 'Website';

  const { findByPlaceholderText, getAllByText, queryByText } = render(<PipelinePicker entities={entities} />);

  expect(queryByText(targetFieldText)).not.toBeInTheDocument();

  const filter = await findByPlaceholderText('Filter…');
  expect(filter).toBeInTheDocument();

  await userEvent.type(filter, targetFieldText);

  await waitFor(() => {
    const websiteFields = getAllByText(targetFieldText);
    expect(websiteFields).toHaveLength(2);
  });
});

test('PipelinePicker supports clearing search results', async () => {
  const { queryAllByLabelText, findAllByPlaceholderText, getAllByLabelText } = render(
    <PipelinePicker entities={entities} />
  );

  let buttons = getAllByLabelText('expand-branch');
  expect(buttons).toHaveLength(entities.length);

  await userEvent.click(buttons[0]);
  await userEvent.click(buttons[1]);

  // Expand two branches
  const countOfItemsToExpand = 2;

  buttons = getAllByLabelText('expand-branch');
  expect(buttons).toHaveLength(entities.length - countOfItemsToExpand);
  buttons = getAllByLabelText('collapse-branch');
  expect(buttons).toHaveLength(countOfItemsToExpand);

  const filter = await findAllByPlaceholderText('Filter…');
  expect(filter).toHaveLength(countOfItemsToExpand + 1);

  // Simple top level filter that will have results for every entity
  await userEvent.type(filter[0], 'a');

  // All entities should be expanded when top level search is active
  await waitFor(() => {
    expect(queryAllByLabelText('expand-branch')).toHaveLength(0);
    buttons = getAllByLabelText('collapse-branch');
    expect(buttons).toHaveLength(entities.length);
  });

  // After clearing the filter the previous expanded items should be expanded
  await userEvent.clear(filter[0]);
  await waitFor(() => {
    buttons = getAllByLabelText('expand-branch');
    expect(buttons).toHaveLength(entities.length - countOfItemsToExpand);
  });
});

test('PipelinePicker supports select all fields and deselect all fields', async () => {
  const { findByText, findAllByText } = render(<PipelinePicker entities={entities} />);

  let buttons = await findAllByText('Select all fields');
  expect(buttons).toHaveLength(entities.length);

  await userEvent.click(buttons[0]);

  const selectedFieldCount = await findByText(`${entities[0].fields.length} selected`);

  expect(selectedFieldCount).toBeInTheDocument();
});

test('PipelinePicker supports receiving an entities value prop', async () => {
  const { container } = render(<PipelinePicker entities={entities} value={pipelinePickerEntityValue} />);

  const numberOfCheckedCheckboxes = container.querySelectorAll('input[type="checkbox"]:checked');

  expect(numberOfCheckedCheckboxes).toHaveLength(pipelinePickerEntityValue.entities.length);
});

test('All entities should be available for selection', async () => {
  const { container } = render(<PipelinePicker entities={entities} value={pipelinePickerEntityValue} />);
  const numberOfCheckedCheckboxes = container.querySelectorAll('input[type="checkbox"]');

  expect(numberOfCheckedCheckboxes).toHaveLength(entities.length);
});

test('No entities are selected while loading, after loading selected entities are checked', async () => {
  const loadingEntities = produce(entities, (draft) => {
    draft[0].loading = true;
  });

  const { container, rerender } = render(
    <PipelinePicker entities={loadingEntities} value={pipelinePickerEntityValue} />
  );

  const numberOfCheckedCheckboxesWhileLoading = container.querySelectorAll('input[type="checkbox"]:checked');
  expect(numberOfCheckedCheckboxesWhileLoading).toHaveLength(0);

  rerender(<PipelinePicker entities={entities} value={pipelinePickerEntityValue} />);

  const numberOfCheckedCheckboxes = container.querySelectorAll('input[type="checkbox"]:checked');
  expect(numberOfCheckedCheckboxes).toHaveLength(pipelinePickerEntityValue.entities.length);
});

test('When changes exist, all entities and fields are visible and disabled', async () => {
  const { container } = render(<PipelinePicker entities={entities} value={pipelinePickerEntityValue} hasChanges />);

  const avialableCheckboxes =
    sumBy(pipelinePickerEntityValue.entities, (entity) => entity.fields.length) +
    pipelinePickerEntityValue.entities.length;

  const numberOfCheckedCheckboxesWhileLoading = container.querySelectorAll('input[type="checkbox"]:disabled');
  expect(numberOfCheckedCheckboxesWhileLoading).toHaveLength(avialableCheckboxes);
});
