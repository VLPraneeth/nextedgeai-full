import { FetchStatus } from 'store/types';
import AppConstants from 'utils/AppConstants';

/**
 *  Returns a combined status for all provided statuses. For FetchStatus, it can be important
 *  to represent a series of fetches as a single status that provides the most meaning.
 *
 *  It's important that this order matches the expectation of importance of a user,
 *  ERROR is the most important status to know about
 *  SUCCESS should have the highest bar for acceptance, only allowed when all other statues are false
 *
 */
export function getCombinedFetchStatus(...statuses: FetchStatus[]) {
  return (
    [AppConstants.FETCH_STATUS.ERROR, AppConstants.FETCH_STATUS.LOADING].find((fetchStatus) =>
      statuses.some((s) => s === fetchStatus)
    ) ||
    [AppConstants.FETCH_STATUS.IDLE, AppConstants.FETCH_STATUS.SUCCESS].find((fetchStatus) =>
      statuses.every((s) => s === fetchStatus)
    )
  );
}
