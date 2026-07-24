//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { render, screen } from 'tests/helpers';

import MultiValueText from '../MultiValueText';

describe('MultiValueText', () => {
  test('renders a blank text ', async () => {
    // @ts-expect-error
    render(<MultiValueText defaultValue="" value="" />);
    expect(await screen.findByDisplayValue('')).toBeVisible();
  });

  test('render multiple group ', async () => {
    render(<MultiValueText value={['one', 'two', 'Three, Inc.']} />);
    expect(await screen.findByText('one')).toBeVisible();
    expect(await screen.findByText('two')).toBeVisible();
    expect(await screen.findByText('Three, Inc.')).toBeVisible();
  });
});
