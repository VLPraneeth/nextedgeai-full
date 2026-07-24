// @ts-nocheck
//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { useEffect } from 'react';

import Fieldset, { useFieldsetContext } from 'components/Fieldset';
import { makeElementNotFoundError, userEvent, render, screen, waitFor } from 'tests/helpers';

it('Fieldset renders with text', async () => {
  render(<Fieldset title="My Fieldset" />);
  expect(await screen.findByText('My Fieldset')).toBeInTheDocument();
});

it('Fieldset verify classnames', () => {
  const { container } = render(<Fieldset title="My Fieldset" className="my-classname" />);
  // eslint-disable-next-line testing-library/no-container
  expect(container.querySelector('.synri-fieldset.my-classname')).toBeInTheDocument();
});

it('Fieldset verify html properties passed down', () => {
  const { container } = render(<Fieldset title="My Fieldset" tabIndex="12" />);
  // eslint-disable-next-line testing-library/no-container
  expect(container.querySelector('fieldset[tabIndex="12"]')).toBeInTheDocument();
});

it('Fieldset collapses', async () => {
  render(
    <Fieldset title="Collapsible Fieldset" collapsible>
      <div>Here's some fieldset content!</div>
    </Fieldset>
  );

  expect(await screen.findByText('Collapsible Fieldset')).toBeInTheDocument();
  expect(await screen.findByText("Here's some fieldset content!")).toBeInTheDocument();

  // collapse
  await userEvent.click(await screen.findByRole('button', { name: 'collapse Collapsible Fieldset' }));
  expect(await screen.findByText('Collapsible Fieldset')).toBeInTheDocument();
  await waitFor(async () => expect(await screen.findByText("Here's some fieldset content!")).not.toBeVisible());

  // re-expand I'm adding await
  await userEvent.click(await screen.findByRole('button', { name: 'collapse Collapsible Fieldset' }));
  expect(await screen.findByText("Here's some fieldset content!")).toBeInTheDocument();
});

const FieldsetBadger = ({ collapsedBadge, children }) => {
  const fieldset = useFieldsetContext();

  useEffect(() => {
    if (collapsedBadge) {
      fieldset.updateCollapsedBadge(collapsedBadge);
    }
  }, [fieldset, collapsedBadge]);

  return <div>{children}</div>;
};

it('Fieldset collapsed badge', async () => {
  render(
    <Fieldset title="Fieldset with badge" collapsible>
      <FieldsetBadger collapsedBadge="99%">Here's some fieldset content!</FieldsetBadger>
    </Fieldset>
  );

  expect(await screen.findByText('Fieldset with badge')).toBeInTheDocument();
  expect(await screen.findByText("Here's some fieldset content!")).toBeInTheDocument();
  await expect(screen.findByText('99%')).rejects.toThrow(makeElementNotFoundError('99%'));

  // collapse
  await userEvent.click(await screen.findByRole('button', { name: 'collapse Fieldset with badge' }));
  expect(await screen.findByText('Fieldset with badge')).toBeVisible();
  expect(await screen.findByText('99%')).toBeVisible();
  await waitFor(async () => expect(await screen.findByText("Here's some fieldset content!")).not.toBeVisible());

  // re-expand
  await userEvent.click(await screen.findByRole('button', { name: 'collapse Fieldset with badge' }));
  expect(await screen.findByText("Here's some fieldset content!")).toBeInTheDocument();
  await waitFor(async () => expect(await screen.findByText('99%')).not.toBeVisible());
});

it('Fieldset collapse/expand from title', async () => {
  render(
    <Fieldset title="Fieldset with badge" collapsible>
      <FieldsetBadger collapsedBadge="99%">Here's some fieldset content!</FieldsetBadger>
    </Fieldset>
  );

  expect(await screen.findByText('Fieldset with badge')).toBeInTheDocument();
  expect(await screen.findByText("Here's some fieldset content!")).toBeInTheDocument();

  // collapse
  await userEvent.click(await screen.findByRole('button', { name: 'Fieldset with badge' }));
  await waitFor(async () => expect(await screen.findByText("Here's some fieldset content!")).not.toBeVisible());

  // re-expand
  await userEvent.click(await screen.findByRole('button', { name: 'Fieldset with badge' }));
  expect(await screen.findByText("Here's some fieldset content!")).toBeInTheDocument();
  await waitFor(async () => expect(await screen.findByText('99%')).not.toBeVisible());
});
