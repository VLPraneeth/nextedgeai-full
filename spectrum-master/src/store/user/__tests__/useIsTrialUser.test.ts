import { InstanceType } from 'store/instances/slice';
import { renderHook } from 'tests/helpers';
import AppConstants from 'utils/AppConstants';

import { useIsTrialUser } from '../selector.hooks';

const renderHookWithInstanceType = (instanceType: InstanceType) =>
  renderHook(useIsTrialUser, {
    testState: { user: { currentInstanceType: instanceType } },
  });

describe('useIsTrialUser', () => {
  afterEach(() => {
    localStorage.clear();
  });
  it("returns false if user's current instance type is 'production'", () => {
    const isTrialUser = renderHookWithInstanceType('production');

    expect(isTrialUser).toBe(false);
  });
  it("returns false if user's current instance type is not 'sandbox'", () => {
    const isTrialUser = renderHookWithInstanceType('sandbox');

    expect(isTrialUser).toBe(false);
  });
  it("returns true if user's current instance type is 'trial'", () => {
    const isTrialUser = renderHookWithInstanceType('trial');

    expect(isTrialUser).toBe(true);
  });

  it('returns true if simulating trial with localStorage, even for non-trial instances', () => {
    localStorage.setItem(AppConstants.SIMULATE_TRIAL_INSTANCE, 'true');
    const isTrialUser = renderHookWithInstanceType('production');

    expect(isTrialUser).toBe(true);
  });
  it('returns true if simulating trial wih localStorage, even for non-trial instances', () => {
    localStorage.setItem(AppConstants.SIMULATE_TRIAL_INSTANCE, 'true');
    const isTrialUser = renderHookWithInstanceType('sandbox');

    expect(isTrialUser).toBe(true);
  });
});
