//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { render, userEvent } from 'tests/helpers';
import AppConstants from 'utils/AppConstants';

import InputContainer from '../InputContainer';

describe('InputContainer', () => {
  test('should allow not passing a value to a switch component', async () => {
    const { findByRole } = render(<InputContainer datatype={AppConstants.INPUT_TYPE.BOOLEAN} />);

    const toggle = await findByRole('switch');
    expect(toggle).toHaveAttribute('aria-checked', 'false');

    await userEvent.click(toggle);
    expect(toggle).toHaveAttribute('aria-checked', 'true');
  });

  test('should use the value prop if passed to switch component', async () => {
    const { findByRole } = render(<InputContainer value datatype={AppConstants.INPUT_TYPE.BOOLEAN} />);

    const toggle = await findByRole('switch');
    expect(toggle).toHaveAttribute('aria-checked', 'true');

    await userEvent.click(toggle);
    expect(toggle).toHaveAttribute('aria-checked', 'true');
  });

  test('should not use the token input for array values', async () => {
    const { findByDisplayValue } = render(
      <InputContainer renderType={AppConstants.INPUT_RENDER_TYPE.TOKENS} value={['testing', 'now']} />
    );
    expect(await findByDisplayValue('testing,now')).toBeInTheDocument();
  });
});
