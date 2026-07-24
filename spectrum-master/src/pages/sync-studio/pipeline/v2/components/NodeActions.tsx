import { showNodeConfigModal } from 'actions/entityPipelineActions';
import { ReactComponent as CloneIcon } from 'assets/icons/clone-new.svg';
import { ReactComponent as CopyIcon } from 'assets/icons/copy-new.svg';
import { ReactComponent as EditIcon } from 'assets/icons/edit-new.svg';
import { ReactComponent as FieldsIcon } from 'assets/icons/fields-new.svg';
import { ReactComponent as MergeStudioIcon } from 'assets/icons/merge-studio-new.svg';
import { ReactComponent as PaintPaletteIcon } from 'assets/icons/palette-new.svg';
import { ReactComponent as TagIcon } from 'assets/icons/tag-new.svg';
import { ReactComponent as TrashIcon } from 'assets/icons/trash-2.svg';
import { useEnhancedDispatch } from 'hooks/redux';

import useEnhancedReactFlow from '../hooks/useEnhancedReactFlow';

import './NodeActions.scss';

export interface ActionBarProps {
  nodeId: string;
  edit?: boolean;
  copy?: boolean;
  clone?: boolean;
  palette?: boolean;
  trash?: boolean;
  fields?: boolean;
  mergeStudio?: boolean;
  tags?: boolean;
}

const NodeActions = ({ nodeId, edit, copy, clone, palette, trash, fields, mergeStudio, tags }: ActionBarProps) => {
  const dispatch = useEnhancedDispatch();

  const { setEdges, setNodes } = useEnhancedReactFlow();

  return (
    <div className="node-actions">
      {edit && (
        <button
          className="node-actions__box"
          onClick={() => {
            dispatch(showNodeConfigModal(true));
          }}>
          <EditIcon className="node-actions__box--icon" />
        </button>
      )}
      {copy && (
        <button className="node-actions__box">
          <CopyIcon className="node-actions__box--icon" />
        </button>
      )}
      {clone && (
        <button className="node-actions__box">
          <CloneIcon className="node-actions__box--icon" />
        </button>
      )}
      {palette && (
        <button className="node-actions__box">
          <PaintPaletteIcon className="node-actions__box--icon" />
        </button>
      )}
      {trash && (
        <button
          className="node-actions__box danger"
          onClick={() => {
            setNodes((nodes) => nodes.filter((node) => node.id !== nodeId));
            setEdges((edges) => edges.filter((edge) => edge.source !== nodeId && edge.target !== nodeId));
          }}>
          <TrashIcon className="node-actions__box--icon" />
        </button>
      )}
      {fields && (
        <button className="node-actions__box">
          <FieldsIcon className="node-actions__box--icon" />
        </button>
      )}
      {mergeStudio && (
        <button className="node-actions__box">
          <MergeStudioIcon className="node-actions__box--icon" />
        </button>
      )}
      {tags && (
        <button className="node-actions__box">
          <TagIcon className="node-actions__box--icon" />
        </button>
      )}
    </div>
  );
};

export default NodeActions;
