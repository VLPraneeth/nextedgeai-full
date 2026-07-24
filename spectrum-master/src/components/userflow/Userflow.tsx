import { useEffect, useState } from 'react';
import userflow from 'userflow.js';

import { useEnhancedSelector } from 'hooks/redux';
import { useInsightsEnabled } from 'pages/insights-studio/utils/useInsightsEnabled';
import { useCurrentInstanceState } from 'store/instances/useCurrentInstanceState';
import AppConstants from 'utils/AppConstants';

import { identifyUser, updateUserflowUser } from './utils';

/**
 * Declarative component for communicating with Userflow.
 *
 * @returns null
 */
export const Userflow = () => {
  const userflowToken = process.env.REACT_APP_USERFLOW_TOKEN;
  const currentUser = useEnhancedSelector((state) => state.user);
  const userLoginStatus = currentUser.fetchingLoginStatus;

  const [isInitialized, setInitialized] = useState(false);

  /**
   * Initialize Userflow
   */
  useEffect(() => {
    if (userflowToken) {
      userflow.init(userflowToken);
      setInitialized(true);
    }
  }, [userflowToken]);

  /**
   * Initialize Userflow and identify user when user details are available
   */
  useEffect(() => {
    if (isInitialized && currentUser.id) {
      identifyUser(currentUser);
    }
    // Only run once per user, we don't want to re-run this
    // when other parts of the user object change
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentUser.id, isInitialized]);

  /**
   * Update instanceId and inTrial when user switches instances
   */
  useEffect(() => {
    if (isInitialized) {
      updateUserflowUser({
        in_trial:
          currentUser.currentInstanceType === 'trial' ||
          localStorage.getItem(AppConstants.SIMULATE_TRIAL_INSTANCE) === 'true',
        instance_id: currentUser.currentInstanceNextEdgeId,
        instance_type: currentUser.currentInstanceType,
      });
    }
  }, [currentUser.currentInstanceNextEdgeId, currentUser.currentInstanceType, isInitialized]);

  /**
   * Update user with additional data from Instance State, when available
   */
  const {
    expiryDate,
    numberOfRecordsLeft,
    pipelineCount,
    recordLimit,
    synapseCount,
    trialDaysLeft,
  } = useCurrentInstanceState();
  useEffect(() => {
    // Check exipryDate is present to ensure data has been fetched before updating
    if (isInitialized && expiryDate) {
      updateUserflowUser({
        days_remaining: trialDaysLeft,
        end_date: expiryDate,
        pipeline_count: pipelineCount,
        record_count: recordLimit - numberOfRecordsLeft,
        synapse_count: synapseCount,
      });
    }
  }, [expiryDate, numberOfRecordsLeft, pipelineCount, recordLimit, synapseCount, trialDaysLeft, isInitialized]);

  /**
   * Update user with status of Insights feature
   */
  const isInsightsEnabled = useInsightsEnabled();
  useEffect(() => {
    if (isInitialized) {
      updateUserflowUser({
        insights_enabled: isInsightsEnabled,
      });
    }
  }, [isInsightsEnabled, isInitialized]);

  /**
   * Track a user's login. Will only trigger of the login thunk was successful.
   * Page refreshes will not trigger this effect since the fetch status stays
   * idle on refresh.
   */
  useEffect(() => {
    if (isInitialized && userLoginStatus === AppConstants.FETCH_STATUS.SUCCESS) {
      userflow.track('login', {
        login_instance_id: currentUser.currentInstanceNextEdgeId,
      });
    }
  }, [currentUser.currentInstanceNextEdgeId, isInitialized, userLoginStatus]);

  // Always return null, no UI elements
  return null;
};
