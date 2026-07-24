import { isEmpty } from 'lodash';

import Config from 'pages/sync-studio/node-config-v2/Config';
import TestAddUpdateSimulatedPanel from 'pages/sync-studio/test/test-panels/TestAddUpdateSimulatedPanel';
import AppConstants from 'utils/AppConstants';

import CreateVersionModal from '../../entity-pipeline/CreateVersionModal';
import { ConfirmUngroupModal } from '../../node-grouping/confirm-ungroup-modal';
import { PipelineErrorResultPanel } from '../../pipeline-error/PipelineErrorResultPanel';
import { ValidationResultsPanel } from '../../validation/ValidationResultsPanel';
import { PipelineEditorProps } from '../PipelineEditor.types';
import { Settings } from '../settings/Settings';
import { usePipelineEditor } from './context/PipelineEditorV2.context';

const { GRAPH_STATUS, PIPELINE_CONTEXT } = AppConstants;

const PipelineEditorModals = (props: PipelineEditorProps) => {
  const pipelineId = props.isEntityPipeline ? props.entityId : (props.fieldId as string);
  const pipelineContext = props.isEntityPipeline ? PIPELINE_CONTEXT.ENTITY : PIPELINE_CONTEXT.FIELD;

  const { selectedGraphNode } = usePipelineEditor();

  return (
    <>
      {/* <FragmentModal
        createFragmentVisible={props.createFragmentVisible}
        enableNodeCheck={props.enableNodeCheck}
        showCreateFragmentModal={props.showCreateFragmentModal}
        nodeCheckValues={props.nodeCheckValues}
        clearNodeCheckValues={props.clearNodeCheckValues}
        pipelineContext={pipelineContext}
        // saveFragment={saveFragment}
        // selectAllNodeCheck={selectAllNodeCheck}
        // unselectAllNodeCheck={unselectAllNodeCheck}
        saveFragmentErrorMessage={props.saveFragmentErrorMessage}
        fragmentSaving={props.fragmentSaving}
        resetFragmentModal={props.resetFragmentModal}
        validating={props.pipelineValidating}
        errorMessage={props.errorMessage}
        // validate={onValidate}
      /> */}
      <ValidationResultsPanel />
      {props.graphVersion?.toUpperCase() === GRAPH_STATUS.APPROVED && <PipelineErrorResultPanel />}
      {/* <CreateGroupPanel editor={editorRef.current} />
      {isEntityPipeline && (
        <TestRunLivePanel
          //   onSaveChanges={onSaveChanges}
          //   validate={onValidate}
          pipelineValidationError={props.errorMessage}
        />
      )}
      <DeleteMultipleNodesModal editor={editorRef.current} /> */}
      <ConfirmUngroupModal />
      {/* <ConfirmDuplicateModal editor={editorRef.current} />
      <TestRunSimulatedPanel
        pipelineId={pipelineId}
        pipelineContext={pipelineContext}
        // onSaveChanges={onSaveChanges}
      /> */}
      <TestAddUpdateSimulatedPanel
        pipelineId={pipelineId}
        pipelineContext={pipelineContext}
        validating={props.pipelineValidating}
        // validate={onValidate}
        errorMessage={props.errorMessage}
      />
      {/* <TestResultPanel
        pipelineId={pipelineId}
        pipelineContext={pipelineContext}
        errorMessage={props.errorMessage}
        // validate={onValidate}
        // onSaveChanges={onSaveChanges}
      /> */}
      <Settings />

      {props.nodeConfigModalVisible && !isEmpty(selectedGraphNode) && <Config key={selectedGraphNode?.id} />}
      <CreateVersionModal entityId={props.entityId} />
    </>
  );
};

export default PipelineEditorModals;
