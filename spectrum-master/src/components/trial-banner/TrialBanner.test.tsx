import { mockTrialInstanceState } from 'store/instances/useCurrentInstanceState/mockInstanceState';
import { render, screen } from 'tests/helpers';

import { TrialBanner } from './TrialBanner';

const defaultMessage = 'Your free trial has 29 days remaining.';
const trialExpired = 'Your free trial has ended. Your pipelines are paused and certain features are unavailable.';
const recordLimitReached = "You've reached the 10,000 record limit of your free trial.";

describe('TiralBanner', () => {
  describe('User is not in a trial instance', () => {
    it('Does not render if user is not in a trial instance', () => {
      render(<TrialBanner />, {
        testState: {
          user: { currentInstanceType: 'sandbox' },
        },
      });

      expect(screen.queryByText(defaultMessage)).not.toBeInTheDocument();
    });
  });
  describe('User is in a trial instance', () => {
    it('Renders info banner with number of days remaining', () => {
      render(<TrialBanner />, {
        testState: {
          user: { currentInstanceType: 'trial' },
          instance: { currentInstanceState: mockTrialInstanceState },
        },
      });
      expect(screen.queryByText(defaultMessage)).toBeVisible();
    });

    it('Renders warning banner when trial is expired', () => {
      render(<TrialBanner />, {
        testState: {
          user: { currentInstanceType: 'trial' },
          instance: { currentInstanceState: { ...mockTrialInstanceState, trialExpired: true } },
        },
      });

      expect(screen.queryByText(trialExpired)).toBeVisible();
    });

    it('Renders warning banner with record limit when limit if hit', () => {
      render(<TrialBanner />, {
        testState: {
          user: { currentInstanceType: 'trial' },
          instance: {
            currentInstanceState: { ...mockTrialInstanceState, recordLimitExpired: true },
          },
        },
      });

      expect(screen.queryByText(recordLimitReached)).toBeVisible();
    });
  });
});
