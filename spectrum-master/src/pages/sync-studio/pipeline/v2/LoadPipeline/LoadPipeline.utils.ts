import { isEmpty } from 'lodash';

import AppConstants from 'utils/AppConstants';

const { GRAPH_STATUS } = AppConstants;

// Based on the pipeline and the graph version in the URL, return a valid value
// for draftStatus (NEW, DRAFT, APPROVED)
export const getCorrectDisplayGraph = (
  pipeline: any,
  displayedGraph: string,
  urlGraphVersion?: string
): 'NEW' | 'DRAFT' | 'APPROVED' | undefined => {
  // There may be no pipeline if the user is looking at a field pipeline that
  // does not exist.
  if (isEmpty(pipeline)) {
    return;
  }

  // displayedGraph is the graph status in the URL
  if (displayedGraph === GRAPH_STATUS.NEW) {
    // Navigate to draft if the user is trying to navigate to new
    // and a draft exists.
    if (pipeline.draft) {
      return GRAPH_STATUS.DRAFT;
    } else if (pipeline.draftStatus !== GRAPH_STATUS.NEW) {
      return GRAPH_STATUS.APPROVED;
    } else if (urlGraphVersion?.toUpperCase() !== GRAPH_STATUS.NEW) {
      // If a user directly navigates to a url with no version then default to new
      return GRAPH_STATUS.NEW;
    }
  } else if (displayedGraph === GRAPH_STATUS.DRAFT) {
    if (pipeline.draftStatus === GRAPH_STATUS.NEW) {
      return GRAPH_STATUS.NEW;
    } else if (!pipeline.draft) {
      return GRAPH_STATUS.APPROVED;
    }
  } else if (displayedGraph === GRAPH_STATUS.APPROVED) {
    if (pipeline.draftStatus === GRAPH_STATUS.NEW) {
      return GRAPH_STATUS.NEW;
    }
  }
};
