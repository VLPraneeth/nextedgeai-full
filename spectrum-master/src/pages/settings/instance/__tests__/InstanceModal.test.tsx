import * as slice from 'store/instances/slice';
import { render, screen, userEvent } from 'tests/helpers';

import InstanceModal from '../InstanceModal';

test('instance creation modal', async () => {
  const createInstanceSpy = jest.spyOn(slice, 'createInstance');

  render(<InstanceModal />, {
    testState: {
      // @ts-ignore
      instance: {
        instanceModalVisible: true,
      },
    },
  });

  expect(await screen.findByText('Create Instance')).toBeInTheDocument();
  await userEvent.type(await screen.findByLabelText('Name'), 'New Test Instance');
  await userEvent.type(await screen.findByLabelText('Display Name'), 'Test Instance Display Name');

  await userEvent.click(await screen.findByText('Production'));
  await userEvent.click(await screen.findByText('Sandbox'));

  await userEvent.click(await screen.findByRole('button', { name: 'Create' }));

  expect(createInstanceSpy).toBeCalledWith({
    instanceName: 'New Test Instance',
    displayName: 'Test Instance Display Name',
    orgId: '',
    planName: 'default',
    type: 'sandbox',
  });
});

test('change instance type to production', async () => {
  const updateInstanceSpy = jest.spyOn(slice, 'updateInstance');

  render(
    <InstanceModal
      // @ts-ignore
      instance={{
        name: 'Instance 1',
        displayName: 'Instance 1',
        syncariId: 'syncariId',
        type: 'sandbox',
        status: 'ACTIVE',
        planName: 'default',
        orgName: 'Syncari Master',
      }}
    />,
    {
      testState: {
        instance: {
          instanceModalVisible: true,
        },
      },
    }
  );

  expect(await screen.findByText('Edit Instance')).toBeInTheDocument();
  await userEvent.type(await screen.findByLabelText('Name'), 'New Test Instance');
  await userEvent.type(await screen.findByLabelText('Display Name'), ' - Test Instance Display Name');

  await userEvent.click(await screen.findByText('Sandbox'));
  await userEvent.click(await screen.findByText('Production'));

  await userEvent.click(await screen.findByRole('button', { name: 'Save' }));

  expect(updateInstanceSpy).toBeCalledWith({
    instanceName: 'Instance 1',
    displayName: 'Instance 1 - Test Instance Display Name',
    type: 'production',
    planName: 'default',
    orgId: '',
    name: 'Instance 1',
    syncariId: 'syncariId',
  });
});

const instanceWithType = (type: slice.InstanceType) =>
  ({
    name: 'Instance 1',
    displayName: 'Instance 1',
    type,
    status: 'ACTIVE',
    planName: 'default',
    orgName: 'Syncari Master',
  } as slice.Instance);

test('prevent instance edit to lower type', async () => {
  render(<InstanceModal instance={instanceWithType('internal')} />, {
    testState: {
      instance: {
        instanceModalVisible: true,
      },
    },
  });

  expect(await screen.findByText('Edit Instance')).toBeInTheDocument();
  await userEvent.click(await screen.findByText('Internal'));
  let optionButton = await screen.findByText('Sandbox');
  expect(optionButton).toHaveAttribute('aria-disabled', 'false');
});

test('prevent changing type if type is production instance edit to lower type', async () => {
  render(<InstanceModal instance={instanceWithType('production')} />, {
    testState: {
      instance: {
        instanceModalVisible: true,
      },
    },
  });

  expect(await screen.findByText('Edit Instance')).toBeInTheDocument();
  await userEvent.click(await screen.findByText('Production'));

  let optionButton = await screen.findByText('Internal');
  expect(optionButton).toHaveAttribute('aria-disabled', 'true');

  optionButton = await screen.findByText('Demo');
  expect(optionButton).toHaveAttribute('aria-disabled', 'true');

  optionButton = await screen.findByText('Sandbox');
  expect(optionButton).toHaveAttribute('aria-disabled', 'true');

  const prodElements = await screen.findAllByText('Production');
  expect(prodElements[1]).not.toHaveAttribute('aria-disabled');
});
