import { render, screen } from 'tests/helpers';

import { ValidationResultsSection } from './ValidationResultsSection';

describe('ValidationResultsSection', () => {
  it('should render its title prop', async () => {
    render(<ValidationResultsSection title="Title" />);

    expect(await screen.findByText('Title')).toBeVisible();
  });
});
