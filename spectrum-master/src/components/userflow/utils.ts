import userflow from 'userflow.js';

import { UserState } from 'store/user/types';

import { UserflowUserAttributes } from './types';

/**
 * Strictly-typed function to identify current user for Userflow tracking
 *
 * @param {UserState} currentUser
 */
export async function identifyUser(currentUser: UserState) {
  const userAttributes: UserflowUserAttributes = {
    email: currentUser.email,
    first_name: currentUser.firstName,
    in_trial: currentUser.currentInstanceType === 'trial',
    instance_id: currentUser.currentInstanceNextEdgeId,
    instance_type: currentUser.currentInstanceType,
    last_name: currentUser.lastName,
    signed_up_at: currentUser.createdAt,
  };

  try {
    await userflow.identify(currentUser.id, { ...userAttributes });
  } catch (err) {
    console.error(err);
  }
}

/**
 * Strictly-typed function for updating attribute values for the current user in Userflow.
 * Allows only the specific attributes defined in UserflowUserAttributes
 *
 * @param {UserflowUserAttributes} attributes Object literal with values to be updated
 */
export const updateUserflowUser = (attributes: UserflowUserAttributes) => {
  userflow.updateUser({ ...attributes });
};
