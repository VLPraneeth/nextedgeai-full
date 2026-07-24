//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { MergeOptions } from 'components/merge-options';
import { render, userEvent } from 'tests/helpers';

test('MergeOptions renders the auto-arrange checkbox option', async () => {
  const { getByRole } = render(<MergeOptions onChange={jest.fn()} />);

  const checkbox = getByRole('checkbox');
  expect(checkbox).toBeInTheDocument();
  expect(checkbox).toBeChecked();
});

test('MergeOptions uses the defaultValue provided', async () => {
  const onChange = jest.fn(() => {});
  const { getByRole } = render(<MergeOptions onChange={onChange} defaultValue={{ autoArrange: false }} />);

  const checkbox = getByRole('checkbox');
  expect(checkbox).toBeInTheDocument();
  expect(checkbox).not.toBeChecked();

  // The onChange gets called with the initial value. Clearing the mock to only
  // use the click event.
  onChange.mockClear();

  await userEvent.click(checkbox);

  expect(onChange).toHaveBeenCalledWith({
    autoArrange: true,
    global: {
      destination: 'REPLACE',
      source: 'REPLACE',
    },
  });
});
