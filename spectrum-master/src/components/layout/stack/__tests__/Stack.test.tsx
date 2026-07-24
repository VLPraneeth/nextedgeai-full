import { render, screen } from 'tests/helpers';

import { Stack } from '..';

test('Stack renders multiple children', async () => {
  render(
    <Stack>
      <div>1</div>
      <div>2</div>
      <div>3</div>
    </Stack>
  );

  expect(screen.getByText('1')).toBeDefined();
  expect(screen.getByText('2')).toBeDefined();
  expect(screen.getByText('3')).toBeDefined();
});

test('Stack renders single child', async () => {
  render(
    <Stack>
      <div>1</div>
    </Stack>
  );

  expect(screen.getByText('1')).toBeDefined();
});
