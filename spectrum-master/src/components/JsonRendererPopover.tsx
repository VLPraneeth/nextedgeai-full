import { ColDef } from 'ag-grid-community';
import { Button, Popover, message } from 'antd';
import { noop } from 'lodash/fp';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Controlled as CodeMirror } from 'react-codemirror2';

import { ReactComponent as ClipboardIcon } from 'assets/icons/copy-clipboard.svg';
import AgTable from 'components/AgTable';
import { useLayoutContext } from 'pages/LayoutContext';
import { getCodeMirrorOptions } from 'utils/CodeMirrorUtil';
import { tc } from 'utils/i18nUtil';
import { copyStringToClipboard } from 'utils/StringUtil';

import { HStack, Stack } from './layout';

import './JsonRendererPopover.scss';

export const JsonRendererPopover = ({
  jsonString,
  format = 'json',
}: {
  jsonString: string;
  format?: 'table' | 'json';
}) => {
  const [visible, setVisible] = useState(false);

  return (
    <Popover
      visible={visible}
      onVisibleChange={setVisible}
      content={
        <div>
          {format === 'table' ? (
            <TableRenderer jsonString={jsonString} />
          ) : (
            <CodeMirrorRenderer jsonString={jsonString} />
          )}
          <div className="popover-footer">
            <Button size="small" type="primary" onClick={() => setVisible(false)}>
              {tc('close')}
            </Button>
          </div>
        </div>
      }
      trigger="click">
      <a className="input-output-renderer">{tc('view')}</a>
    </Popover>
  );
};

interface RendererProps {
  jsonString: string;
}

const TableRenderer = ({ jsonString }: RendererProps) => {
  let records: {
    header: string;
    value: any;
  }[] = [];
  let formatedJsonString = '';
  try {
    const json = JSON.parse(jsonString);
    formatedJsonString = JSON.stringify(json, undefined, 4);
    records = Object.keys(json).map((key) => {
      return {
        header: key,
        value: json[key],
      };
    });
  } catch (err) {
    console.log('error', err);
  }

  const colDefs = useMemo<ColDef[]>(() => {
    return [
      {
        headerName: tc('header'),
        colId: 'header',
        field: 'header',
      },
      {
        headerName: tc('value'),
        colId: 'value',
        field: 'value',
      },
    ];
  }, []);

  return (
    <div className="popover-table-renderer">
      <AgTable
        suppressCellSelection
        className={'custom-synapse__table'}
        domLayout="autoHeight"
        columnDefs={colDefs}
        rowData={records}
      />
      <HStack justify="end">
        <Button onClick={() => copyToClipboard(formatedJsonString)} size="small">
          <HStack align="center" spacing="xxxs">
            <ClipboardIcon width={16} height={16} /> <span>{tc('copy')}</span>
          </HStack>
        </Button>
      </HStack>
    </div>
  );
};

const CodeMirrorRenderer = ({ jsonString }: RendererProps) => {
  const [editor, setEditor] = useState<CodeMirror.Editor | null>(null);
  const layout = useLayoutContext();

  const updateSize = useCallback((editor?: CodeMirror.Editor) => {
    editor?.setSize(450, 300);
  }, []);

  useEffect(() => {
    editor && updateSize(editor);
  }, [editor, layout.dimensions.content.height, updateSize]);

  let formatedJsonString = '';
  try {
    formatedJsonString = JSON.stringify(JSON.parse(jsonString), undefined, 4);
  } catch (err) {}

  return (
    <Stack spacing="sm">
      <CodeMirror
        className="code-mirror-container"
        value={formatedJsonString}
        options={getCodeMirrorOptions()}
        editorDidMount={(editor: CodeMirror.Editor) => {
          setEditor(editor);
          updateSize(editor);
        }}
        onBeforeChange={noop}
      />
      <HStack justify="end">
        <Button onClick={() => copyToClipboard(formatedJsonString)} size="small">
          <HStack align="center" spacing="xxxs">
            <ClipboardIcon width={16} height={16} /> <span>{tc('copy')}</span>
          </HStack>
        </Button>
      </HStack>
    </Stack>
  );
};

const copyToClipboard = async (textToCopy: string) => {
  try {
    await copyStringToClipboard(textToCopy);
    message.success(tc('copy_to_clipboard_success'));
  } catch (err) {
    message.error(tc('copy_to_clipboard_failure'));
  }
};
