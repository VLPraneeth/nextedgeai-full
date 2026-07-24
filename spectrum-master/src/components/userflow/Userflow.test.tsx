import userflow from 'userflow.js';

import { InstanceState } from 'store/instances/slice';
import { UserState } from 'store/user/types';
import { render, waitFor } from 'tests/helpers';

import { Userflow } from './Userflow';

// Mocks

jest.mock('userflow.js', () => ({
  init: jest.fn(),
  identify: jest.fn(),
  updateUser: jest.fn(),
}));
const mockUserflow = userflow as jest.Mocked<typeof userflow>;

// Spy allows us to check calls and values
// mockImplementation() prevents the error printing to the test output
const consoleError = jest.spyOn(console, 'error').mockImplementation();

// Test Data

const testUser: Partial<UserState> = {
  id: '111',
  email: 'test@test.com',
  firstName: 'Eetsa',
  currentInstanceNextEdgeId: 'ABCDEF',
  lastName: 'Testman',
  createdAt: '',
  currentInstanceType: 'trial',
};

const renderUserflow = (currentInstanceState: Partial<InstanceState> = {}) =>
  render(<Userflow />, {
    testState: {
      user: testUser,
      instance: { currentInstanceState },
    },
  });

describe('Userflow', () => {
  afterEach(() => jest.clearAllMocks());
  it('initializes with token and calls identify with user details', () => {
    process.env.REACT_APP_USERFLOW_TOKEN = 'test-token';
    renderUserflow();

    expect(userflow.init).toHaveBeenCalledTimes(1);
    expect(userflow.identify).toHaveBeenCalledTimes(1);

    expect(userflow.init).toHaveBeenCalledWith('test-token');
    expect(userflow.identify).toHaveBeenCalledWith(testUser.id, {
      email: testUser.email,
      first_name: testUser.firstName,
      in_trial: true,
      instance_id: testUser.currentInstanceNextEdgeId,
      instance_type: testUser.currentInstanceType,
      last_name: testUser.lastName,
      signed_up_at: testUser.createdAt,
    });

    expect(userflow.updateUser).toHaveBeenCalledWith({
      in_trial: true,
      instance_id: testUser.currentInstanceNextEdgeId,
      instance_type: testUser.currentInstanceType,
    });
  });

  it('prints error on failed Identify call', async () => {
    process.env.REACT_APP_USERFLOW_TOKEN = 'test-token';
    mockUserflow.identify.mockImplementationOnce(() => Promise.reject('Failed'));
    renderUserflow();

    await waitFor(() => expect(consoleError).toHaveBeenCalledWith('Failed'));
  });

  it('calls updateUser with synapse and pipeline count when available', () => {
    process.env.REACT_APP_USERFLOW_TOKEN = 'test-token';
    renderUserflow({
      numberofPipelines: 10,
      numberofSynapses: 4,
      trialDaysLeft: 10,
      expiryDate: '2022-04-27T00:37:05.496+00:00',
    });

    expect(userflow.init).toHaveBeenCalledTimes(1);
    expect(userflow.identify).toHaveBeenCalledTimes(1);

    expect(userflow.updateUser).toHaveBeenCalledWith({
      synapse_count: 4,
      pipeline_count: 10,
      record_count: 0,
      end_date: '2022-04-27T00:37:05.496+00:00',
      days_remaining: 10,
    });
  });

  it('tells Userflow if Insights Studio is enabled', () => {
    process.env.REACT_APP_USERFLOW_TOKEN = 'test-token';
    renderUserflow({});

    expect(userflow.init).toHaveBeenCalledTimes(1);
    expect(userflow.identify).toHaveBeenCalledTimes(1);

    expect(userflow.updateUser).toHaveBeenCalledWith({ insights_enabled: false });
  });
});
