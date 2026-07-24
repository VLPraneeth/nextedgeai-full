import { render as renderMain } from '@testing-library/react';

import { render, screen, userEvent } from 'tests/helpers';
import AppConstants from 'utils/AppConstants';

import Select, { Option } from './Select';
import { PicklistValue } from './types';

describe('Select', () => {
  it('renders options from children', async () => {
    render(
      <Select>
        <Option value="1">Child 1</Option>
        <Option value="2">Child 2</Option>
      </Select>
    );

    await userEvent.click(screen.getByRole('combobox'));

    expect(screen.getByRole('option', { name: 'Child 1' })).toBeVisible();
    expect(screen.getByRole('option', { name: 'Child 2' })).toBeVisible();
  });

  it('renders options from `options` prop`', async () => {
    const optionArray = [<Option value="1">Child 1</Option>, <Option value="2">Child 2</Option>];

    render(<Select options={optionArray} />);

    await userEvent.click(screen.getByRole('combobox'));

    expect(screen.getByRole('option', { name: 'Child 1' })).toBeVisible();
    expect(screen.getByRole('option', { name: 'Child 2' })).toBeVisible();
  });

  it('generates options from `optionsData` prop`', async () => {
    const optionData = [
      {
        label: 'Child 1',
        value: '1',
      },
      {
        label: 'Child 2',
        value: '2',
      },
    ];

    render(<Select optionData={optionData} />);

    await userEvent.click(screen.getByRole('combobox'));

    expect(screen.getByRole('option', { name: 'Child 1' })).toBeVisible();
    expect(screen.getByRole('option', { name: 'Child 2' })).toBeVisible();
  });

  it('generates options with groups from `optionsData` prop`', async () => {
    const optionData: PicklistValue[] = [
      { label: 'A', value: 'A', picklistGroup: 'Letters' },
      { label: 'B', value: 'B', picklistGroup: 'Letters' },
      { label: 'C', value: 'C', picklistGroup: 'Letters' },
      { label: '1', value: '1', picklistGroup: 'Numbers' },
      { label: '2', value: '2', picklistGroup: 'Numbers' },
      { label: '3', value: '3', picklistGroup: 'Numbers' },
    ];

    render(<Select optionData={optionData} />);

    await userEvent.click(screen.getByRole('combobox'));

    const options = screen.getAllByRole('option');

    expect(options).toHaveLength(8);
    expect(options[0]).toHaveTextContent('Letters');
    expect(options[1]).toHaveTextContent('A');
    expect(options[2]).toHaveTextContent('B');
    expect(options[3]).toHaveTextContent('C');
    expect(options[4]).toHaveTextContent('Numbers');
    expect(options[5]).toHaveTextContent('1');
    expect(options[6]).toHaveTextContent('2');
    expect(options[7]).toHaveTextContent('3');
  });

  it('allows searching options by the displayed label (child text)', async () => {
    const { container } = renderMain(
      <Select>
        <Option value="1">Foo</Option>
        <Option value="2">Bar</Option>
        <Option value="3">Fizz</Option>
        <Option value="4">Buzz</Option>
      </Select>
    );

    await userEvent.type(container.getElementsByClassName('ant-select-search__field')[0], 'b');

    expect(screen.queryByRole('option', { name: 'Bar' })).toBeVisible();
    expect(screen.queryByRole('option', { name: 'Buzz' })).toBeVisible();
    expect(screen.queryByRole('option', { name: 'Foo' })).not.toBeInTheDocument();
    expect(screen.queryByRole('option', { name: 'Fizz' })).not.toBeInTheDocument();
  });

  describe('read-only mode', () => {
    it('renders value as text', () => {
      render(
        <Select displayMode={AppConstants.INPUT_DISPLAY_MODE.READONLY} value="Foo" defaultValue="Bar">
          <Option value="1">Foo</Option>
          <Option value="2">Bar</Option>
        </Select>
      );

      expect(screen.queryAllByRole('combobox')).toHaveLength(0);
      expect(screen.queryAllByRole('option')).toHaveLength(0);
      expect(screen.getByText('Foo')).toBeVisible();
    });

    it('if no value, displays default value', () => {
      render(
        <Select displayMode={AppConstants.INPUT_DISPLAY_MODE.READONLY} defaultValue="Bar">
          <Option value="1">Foo</Option>
          <Option value="2">Bar</Option>
        </Select>
      );

      expect(screen.queryAllByRole('combobox')).toHaveLength(0);
      expect(screen.queryAllByRole('option')).toHaveLength(0);
      expect(screen.getByText('Bar')).toBeVisible();
    });

    it('displays label of the item from `values` array with a `value` that matches the `value` prop', () => {
      render(
        <Select
          displayMode={AppConstants.INPUT_DISPLAY_MODE.READONLY}
          value={'foo'}
          values={[
            { label: 'Foo', value: 'foo', picklistGroup: 'Letters' },
            { label: 'Bar', value: 'bar', picklistGroup: 'Letters' },
          ]}
        />
      );

      expect(screen.queryAllByRole('combobox')).toHaveLength(0);
      expect(screen.queryAllByRole('option')).toHaveLength(0);
      expect(screen.getByText('Foo')).toBeVisible();
    });
  });
});
