import AppConstants from 'utils/AppConstants';
import { tNamespaced } from 'utils/i18nUtil';

import { PipelineV2 } from '../types/BackendPipeline.types';
import { AvailableVersionsModel } from './PipelineToolbarV2';

const tn = tNamespaced('PipelineEditor');

const { GRAPH_STATUS } = AppConstants;

const getAvailableVersions = (pipeline: PipelineV2): AvailableVersionsModel => {
  const availableVersions: any = {};
  const { APPROVED, DRAFT, NEW } = GRAPH_STATUS;
  if (pipeline.draft) {
    availableVersions[APPROVED] = {};
    availableVersions[DRAFT] = {};
  } else if (pipeline.draftStatus === NEW) {
    availableVersions[DRAFT] = {
      singleVersionTooltip: !availableVersions[APPROVED] ? tn('draft_not_published') : '',
    };
  } else {
    availableVersions[APPROVED] = {};
  }

  return availableVersions;
};

export default getAvailableVersions;
