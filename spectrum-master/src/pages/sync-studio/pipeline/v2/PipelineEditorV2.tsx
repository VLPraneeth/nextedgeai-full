import { ReactFlowProvider } from '@xyflow/react';
import { Spin } from 'antd';
import cx from 'classnames';
import { useCallback, useState } from 'react';
import { connect } from 'react-redux';

import { tCommon } from 'utils/i18nUtil';

import { PipelineEditorProps, PipelineToolbarProps } from '../PipelineEditor.types';
import { usePipelineEditor } from './context/PipelineEditorV2.context';
import PipelineEditorLoadingView from './LoadPipeline/PipelineEditorLoadingView';
import PipelineCanvas from './PipelineCanvas';
import PipelineEditorModals from './PipelineEditorModals';
import { mapDispatchToPropsPipeline, mapStateToPropsPipeline } from './PipelineEditorv2.connector';
import { PipelineRightPanel } from './PipelineRightPanel';
import EntityPipelineToolbar from './toolbar/EntityPipelineToolbar';
import FieldPipelineToolbar from './toolbar/FieldPipelineToolbar';

import '@xyflow/react/dist/style.css';
import './PipelineEditorV2.scss';

const PipelineEditorV2 = (props: PipelineEditorProps) => {
  const { hasUnsavedChanges, setHasUnsavedChanges, supplementalNodeData, isDraft } = usePipelineEditor();

  const toolbarProps: PipelineToolbarProps = {
    ...props,
    hasUnsavedChanges,
    setHasUnsavedChanges,
  };

  const getLoadingStatus = () => {
    let loadingMessage = tCommon('loading');
    let loading = false;
    const connectorOrAttributesFetching = props.isEntityPipeline
      ? props.connectorEntitiesFetching
      : props.attributeNodesFetching;
    if (
      props.pipelineFetching ||
      props.pipelineFunctionsFetching ||
      props.pipelineActionsFetching ||
      props.entitiesFetching ||
      connectorOrAttributesFetching
    ) {
      loading = true;
    }
    if (props.pipelineDeleting) {
      loading = true;
      loadingMessage = tCommon('deleting_pipeline');
    }
    if (props.pipelineDiscarding) {
      loading = true;
      loadingMessage = tCommon('discarding_pipeline');
    }
    if (props.isEntityPipeline && props.pipelineApproving) {
      loading = true;
      loadingMessage = tCommon('publishing_pipeline');
    }
    if (props.pipelineSaving) {
      loading = true;
      loadingMessage = tCommon('saving_pipeline');
    }
    if (props.pipelineCreating) {
      loading = true;
      loadingMessage = tCommon('creating_draft');
    }
    return { loadingMessage, loading };
  };

  const { loadingMessage, loading } = getLoadingStatus();

  return (
    <>
      <Spin tip={loadingMessage} spinning={loading}>
        <div
          className={cx(
            'editor-container',
            props.isEntityPipeline ? 'entity-pipeline-editor' : 'field-pipeline-editor'
          )}
          style={{ flex: 1 }}>
          {props.isEntityPipeline ? (
            <EntityPipelineToolbar {...toolbarProps} />
          ) : (
            <FieldPipelineToolbar {...toolbarProps} />
          )}
          <PipelineCanvas {...props} />
          <div className="flow-right-content ">
            <PipelineRightPanel {...props} />
          </div>
        </div>
      </Spin>
      <PipelineEditorModals {...props} />
    </>
  );
};

export const ConnectedPipeLineEditor = connect(mapStateToPropsPipeline, mapDispatchToPropsPipeline)(PipelineEditorV2);

const KeyPipelineEditor = (props: PipelineEditorProps) => {
  const [reloadKey, setReloadKey] = useState(1);

  // Allow remounting the PipelineEditorV2 component directly
  const remountComponent = useCallback(() => {
    setReloadKey(Math.random());
  }, []);

  // Unmount and remount the pipeline when the id or graph version changes
  const key = [props.entityId, props.graphVersion, props.fieldId, reloadKey].join('_');

  return (
    <ReactFlowProvider key={key}>
      <PipelineEditorLoadingView {...props} remountComponent={remountComponent}>
        <ConnectedPipeLineEditor {...props} remountComponent={remountComponent} />
      </PipelineEditorLoadingView>
    </ReactFlowProvider>
  );
};

export default KeyPipelineEditor;
