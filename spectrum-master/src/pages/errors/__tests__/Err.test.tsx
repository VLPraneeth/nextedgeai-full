//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import Err from 'pages/errors/Err';
import { render, screen } from 'tests/helpers';

test('Render the Err children', async () => {
  render(
    <Err>
      <span>Testing</span>
    </Err>
  );
  expect(await screen.findByText('Testing')).toBeInTheDocument();
});
