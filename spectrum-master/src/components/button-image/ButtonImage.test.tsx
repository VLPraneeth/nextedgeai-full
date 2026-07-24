//
// Copyright (c) 2019-Present Syncari All rights reserved.
//

import ButtonImage from 'components/button-image';
import { render, screen, userEvent } from 'tests/helpers';

describe('ButtonImage', () => {
  it('should render without any issues', () => {
    render(<ButtonImage imageAlt="alt text" imageSrc="brandImage.png" onClick={() => {}} />);
    screen.queryByText('alt text');
  });

  it('should be clickable', async () => {
    const clickSpy = jest.fn();
    render(<ButtonImage imageAlt="alt text" imageSrc="brandImage.png" onClick={clickSpy} />);

    await userEvent.click(screen.getByAltText('alt text'));
    expect(clickSpy).toHaveBeenCalledTimes(1);
  });
});
