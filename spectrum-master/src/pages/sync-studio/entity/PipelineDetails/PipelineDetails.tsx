import { ResyncDraftWarningModal } from 'pages/sync-studio/entity-pipeline/entity-resync/ResyncDraftWarningModal';
import ResyncRequestModal from 'pages/sync-studio/entity-pipeline/entity-resync/ResyncRequestModal';

import { PipelineDetailsActivityPanel } from '../PipelineDetailsActivityPanel';
import { PipelineDetailsFilterPanel } from '../PipelineDetailsFilterPanel';
import { PipelineDetailsSummary } from '../PipelineDetailsSummary';
import { PipelineDetailsTable } from '../PipelineDetailsTable';
import { usePipelineStateToasts } from './PipelineDetails.hooks';

import './PipelineDetails.scss';

const PipelineDetailsModals = () => {
  return (
    <>
      <ResyncRequestModal />
      <ResyncDraftWarningModal />
    </>
  );
};

export interface PipelineDetailsProps {
  contextPanel: JSX.Element;
}

export const PipelineDetails = ({ contextPanel }: PipelineDetailsProps) => {
  usePipelineStateToasts();

  return (
    <div className="pipeline-details">
      <div className="pipeline-details__main">
        <PipelineDetailsSummary className="pipeline-details__summary" />
        <PipelineDetailsTable className="pipeline-details__table" />
        <PipelineDetailsFilterPanel />
        <PipelineDetailsActivityPanel />
        <PipelineDetailsModals />
      </div>
      <div className="pipeline-details__context-panel">{contextPanel}</div>
    </div>
  );
};
