import { render, screen } from 'tests/helpers';

import { ScrollableArea } from './ScrollableArea';

describe('ScrollableArea', () => {
  it('renders children', () => {
    render(<ScrollableArea>Content</ScrollableArea>);

    expect(screen.getByText('Content')).toBeVisible();
  });
});
