import { Handle, Position } from '@xyflow/react';

import { NodeModel } from 'components/GraphItemFilter';

export interface ReactFlowCustomNodeProps {
  id: string;
  data: NodeModel & any;
}

const ReactFlowCustomNode = ({ id, data }: ReactFlowCustomNodeProps) => {
  // console.log('render node ', id);
  return (
    <>
      <Handle type="source" position={Position.Right} className="big-handle" />
      <div className="pipeline-node">
        <div className="pipeline-node__left-bar" style={{ backgroundColor: data.typeColor }}>
          <img className="pipeline-node__left-bar--icon" src={data.icon} alt="node-icon" />
        </div>
        <div className="pipeline-node__content">
          <span className="pipeline-node__content--label">{data.metadata.displayName}</span>
          <span className="pipeline-node__content--sublabel">{data.metadata.description || '-'}</span>
        </div>
      </div>
      <Handle
        type="target"
        // The position here is a little irrelevent because the handle covers the
        // entire node surface. Then in the custom edge we determine where the
        // edges connect based on the closest proximity.
        position={Position.Bottom}
        className="custom-target-handle"
        isConnectableStart={false}
      />
    </>
  );
};

export default ReactFlowCustomNode;
