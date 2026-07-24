import React, { createContext, useContext } from 'react';

import { PipelineEditorProps } from '../../PipelineEditor.types';
import { PipelineNodeV2 } from '../types/BackendPipeline.types';
import { ReactFlowNodeV2 } from '../types/ReactFlow.types';

export interface PipelineEditorContextType extends PipelineEditorProps {
  hasUnsavedChanges: boolean;
  setHasUnsavedChanges: React.Dispatch<React.SetStateAction<boolean>>;
  selectedGraphNode: ReactFlowNodeV2 | null;
  saveNodeConfiguration: (node: Partial<PipelineNodeV2>) => void;
  savePipeline: () => Promise<void>;
  supplementalNodeData: Record<string, any>;
  isDraft: boolean;
  setSelectedNodeIds: React.Dispatch<React.SetStateAction<string[]>>;
}

export const PipelineEditorContext = createContext<PipelineEditorContextType>({} as any);

export const usePipelineEditor = () => useContext(PipelineEditorContext);
