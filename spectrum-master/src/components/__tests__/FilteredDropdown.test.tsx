import { noop } from 'lodash';

import { fireEvent, render, userEvent } from 'tests/helpers';

import FilteredDropdown from '../FilteredDropdown/FilteredDropdown';
import { FilteredDropdownItemProps } from '../FilteredDropdown/FilteredDropdownItem';

describe('FilteredDropdown.tsx', () => {
  const firstItemClickWatcher = jest.fn();

  const items: FilteredDropdownItemProps[] = [
    {
      id: 'first',
      title: 'First',
      onClick: firstItemClickWatcher,
    },
    {
      id: 'second',
      title: 'Second',
      onClick: noop,
    },
    {
      id: 'third',
      title: 'Third',
      onClick: noop,
    },
  ];

  test('should render all items', () => {
    const { getAllByRole } = render(<FilteredDropdown open items={items} />);

    const result = getAllByRole('button');
    expect(result).toHaveLength(items.length);
  });

  test('should render only items that match the filter', async () => {
    const { getByPlaceholderText, getAllByRole } = render(<FilteredDropdown open items={items} />);

    const searchInput = getByPlaceholderText('Filter…') as HTMLInputElement;

    fireEvent.click(searchInput);
    await userEvent.type(searchInput, 'second', { delay: 10 });

    const result = getAllByRole('button');
    expect(result).toHaveLength(1);
  });

  test('should fire the onClick handler when item is focused and Enter is pressed', async () => {
    const { getAllByRole } = render(<FilteredDropdown open items={items} />);

    const listItems = getAllByRole('button');
    fireEvent.focus(listItems[0]);
    fireEvent.keyDown(listItems[0], { key: 'Enter', code: 13 });

    expect(firstItemClickWatcher).toHaveBeenCalledTimes(1);
  });
});
