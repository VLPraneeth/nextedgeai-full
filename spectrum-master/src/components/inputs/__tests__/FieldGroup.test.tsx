import { render, screen } from 'tests/helpers';

import FieldGroup from '../FieldGroup';
import TextInputProxy from '../InputProxy/TextInputProxy';

test('FieldGroup displays correctly', async () => {
  const helpText = 'How do you want to be greeted?';
  const labelText = 'Salutation';
  const handleChange = jest.fn();

  render(
    <FieldGroup helpText={helpText} label={labelText}>
      <TextInputProxy onChange={handleChange} value="Hello World" />
    </FieldGroup>
  );

  const input = await screen.findByLabelText(labelText);
  expect(input).toBeInTheDocument();

  expect(await screen.findByText(helpText)).toBeInTheDocument();
});
