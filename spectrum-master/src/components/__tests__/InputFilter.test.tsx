//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import Text from 'antd/lib/typography/Text';

import { render, screen, userEvent, waitFor } from 'tests/helpers';

import InputFilter from '../InputFilter';

describe('InputFilter', () => {
  test('InputFilter renders filterChildren', async () => {
    render(<InputFilter filterChildren={<Text>Input fields</Text>} />);

    expect(await screen.findByText('Input fields')).toBeInTheDocument();
  });

  test('InputFilter show applied filters count when filterChildren is collapsed', async () => {
    const clearFn = jest.fn(() => {});
    render(<InputFilter filterChildren={<Text>Input fields</Text>} filterCount={3} clearFilters={clearFn} />);

    await waitFor(async () => expect(await screen.findByText('3 filters applied')).toBeInTheDocument());

    const collapseButton = await screen.findByTestId('filter-collapse-button');
    await userEvent.click(collapseButton);
  });
});
