import { CellClassParams } from 'ag-grid-community';

import { PipelineLog } from 'store/pipeline/types';

import { usePipelineLogsContext } from './PipelineLogs.context';

import './PipelineLogs.renderers.scss';

export interface InputOutpuRendererProps {
  record: PipelineLog['input'] | PipelineLog['output'];
}

export const InputOutpuRenderer = ({ value, ...rest }: CellClassParams) => {
  const { setJsonData } = usePipelineLogsContext();

  const jsonString = JSON.stringify(value, undefined, 4);

  return (
    <a
      className="input-output-renderer"
      onClick={() => {
        if (value) {
          setJsonData(jsonString);
        }
      }}>
      {jsonString}
    </a>
  );
};
