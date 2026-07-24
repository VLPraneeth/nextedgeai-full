import { Button } from 'antd';
import { noop } from 'lodash/fp';
import { useCallback } from 'react';
import { useMemo, useEffect, useState } from 'react';
import { Controlled as CodeMirror } from 'react-codemirror2';

import DrawerPanel from 'components/DrawerPanel';
import { withI18n, useI18nContext } from 'components/I18nProvider';
import { useLayoutContext } from 'pages/LayoutContext';

import { usePipelineLogsContext } from './PipelineLogs.context';

type PipelineLogsDetailsPanelProps = {
  onRequestClose?: () => void;
};

const PipelineLogsDetailsPanel = ({ onRequestClose }: PipelineLogsDetailsPanelProps) => {
  const { tc } = useI18nContext();
  const [editor, setEditor] = useState<CodeMirror.Editor | null>(null);

  const { jsonData, setJsonData } = usePipelineLogsContext();
  const layout = useLayoutContext();

  const close = useCallback(() => {
    setJsonData(null);
  }, [setJsonData]);

  const updateSize = useCallback(
    (editor?: CodeMirror.Editor) => {
      editor?.setSize(858, layout.dimensions.content.height - 134);
    },
    [layout.dimensions.content.height]
  );

  useEffect(() => {
    editor && updateSize(editor);
  }, [editor, layout.dimensions.content.height, updateSize]);

  const options = useMemo(
    () => ({
      highlightFormatting: true,
      maxBlockquoteDepth: 0,
      fencedCodeBlockHighlighting: true,
      mode: 'javascript',
      lineNumbers: true,
    }),
    []
  );

  return (
    <DrawerPanel
      absolutePositioning
      maskClosable
      onClose={close}
      mask
      className="pipeline-logs-detail-panel"
      title="Log details"
      width="xlarge"
      footer={
        <Button onClick={close} type="primary">
          {tc('close')}
        </Button>
      }
      visible={Boolean(jsonData)}>
      <div>
        <CodeMirror
          value={jsonData || ''}
          options={options}
          editorDidMount={(editor: CodeMirror.Editor) => {
            setEditor(editor);
            updateSize(editor);
          }}
          onBeforeChange={noop}
        />
      </div>
    </DrawerPanel>
  );
};

export default withI18n(PipelineLogsDetailsPanel, 'PipelineLogs');
