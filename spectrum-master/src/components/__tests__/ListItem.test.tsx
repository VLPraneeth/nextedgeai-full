//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { ListItem } from 'components';
import { render, userEvent } from 'tests/helpers';

test('ListItem component renders provided text', async () => {
  const title = 'A Great Title';

  const { findByText } = render(<ListItem title={title} />);

  expect(await findByText(title)).toBeInTheDocument();
});

test('ListItem component triggers onClick', async () => {
  const title = 'A Great Title';
  const onClick = jest.fn();

  const { container } = render(<ListItem title={title} onClick={onClick} />);

  const wrapper = container.querySelector('.synri-list-item-content');
  wrapper && (await userEvent.click(wrapper));

  expect(onClick).toHaveBeenCalled();
});
