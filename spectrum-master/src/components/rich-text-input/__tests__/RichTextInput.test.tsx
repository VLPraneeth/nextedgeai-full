import { render, screen } from 'tests/helpers';

import RichTextInput from '../RichTextInput';

test('RichTextInput supports defaultValue', async () => {
  render(<RichTextInput name="name" defaultValue="<p>default value</p>" />);

  const element = await screen.findByText('default value');
  expect(element).toBeInTheDocument();
});
