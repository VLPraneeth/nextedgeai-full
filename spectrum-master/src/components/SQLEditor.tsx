import { Button, Icon, Tooltip } from 'antd';
import CodeMirror, { Editor, EditorConfiguration, Position, ShowHintOptions } from 'codemirror';
import { useState } from 'react';
import { Controlled as CodeMirrorComponent } from 'react-codemirror2';

import { Text } from 'components/typography';
import { HStack } from 'components/layout';
import { useEnhancedDispatch } from 'hooks/redux';
import useDimensions from 'hooks/useDimensions';
import { useInsightsViewContext } from 'pages/insights-studio/context/InsightsViewContext';
import DatasetVariablePopoverForm from 'pages/insights-studio/dataset/variable/DatasetVariablePopoverForm';
import { useUnifiedDataCardAuthoring } from 'pages/insights-studio/utils/useUnifiedDataCardAuthoring';
import { getDataSourceFields, useGetDatasetAndEntityInfoQuery, useGetSchemaQuery } from 'store/insights-studio';
import { DataSource, DatasetVariable } from 'store/insights-studio/types';
import { tNamespaced } from 'utils/i18nUtil';

import { ScrollableArea } from './scrollable-area/ScrollableArea';
import {
  EDITOR_HEIGHT_BUFFER,
  VARIABLE_PREFIX,
  VARIABLE_SUFFIX,
  getVariablePattern,
  handleVariableCreate,
  handleVariableEdit,
  highlightVariables,
  isVariableContext,
  onEndCompletion,
  sqlHint,
} from './SQLEditor.util';
import { SQLShortcutsModal } from './SQLShortcutsModal';

import 'codemirror/addon/hint/show-hint';
import 'codemirror/addon/hint/show-hint.css';
import 'codemirror/addon/hint/sql-hint';
import 'codemirror/lib/codemirror.css';
import 'codemirror/mode/sql/sql';
import './SQLEditor.scss';

const tn = tNamespaced('InsightsStudio');

export interface SqlEditorProps {
  getDatasetPreview: () => Promise<void>;
}

const SqlEditor = ({ getDatasetPreview }: SqlEditorProps) => {
  const [variableModalVisible, setVariableModalVisible] = useState(false);
  const [shortcutsModalVisible, setShortcutsModalVisible] = useState(false);
  const [variable, setVariable] = useState<DatasetVariable | undefined>();
  const { variables, sql, setSql } = useUnifiedDataCardAuthoring();
  const [cursorPosition, setCursorPosition] = useState<Position>();
  const [editorInstance, setEditorInstance] = useState<Editor | null>(null);
  const [isVariableButtonEnabled, setIsVariableButtonEnabled] = useState(false);
  const { isThoughtSpotView } = useInsightsViewContext();
  const { data: dataSources } = useGetDatasetAndEntityInfoQuery({
    isThoughtspot: isThoughtSpotView,
    withEntityInfo: true,
  });
  const { data: schema } = useGetSchemaQuery();
  const dispatch = useEnhancedDispatch();

  function handleSetVariable(variableName: string) {
    const vari = variables?.find((vari) => vari.apiName === variableName);
    setVariable(vari);
    setVariableModalVisible(true);
  }

  async function fetchDatasourceFields(datasource: DataSource) {
    const response = await dispatch(
      getDataSourceFields.initiate({
        dataSourceId: datasource?.datasetId,
        dataSourceType: datasource.datasetType,
        alias: datasource.alias || datasource.displayName || '',
      })
    );
    return response.data?.dataSourceFields;
  }

  const sqlEditorOptions: EditorConfiguration = {
    mode: 'sql',
    theme: 'default',
    lineNumbers: true,
    lineWrapping: true,
    extraKeys: {
      'Ctrl-N': (cm: Editor) =>
        cm.showHint({
          hint: (editor: Editor) => handleVariableCreate(editor),
        }),
      'Ctrl-Space': (cm: Editor) =>
        cm.showHint({
          hint: (editor: Editor, options: ShowHintOptions) =>
            sqlHint(editor, options, schema, variables, dataSources, fetchDatasourceFields),
        }),
      'Ctrl-E': (cm: Editor) =>
        cm.showHint({
          hint: (editor: Editor) => handleVariableEdit(editor, handleSetVariable),
        }),
      'Ctrl-P': (cm: Editor) =>
        cm.showHint({
          hint: (editor: Editor) => {
            getDatasetPreview();
            return null;
          },
        }),
    },
  };

  function handleNewVariableOpen(cursor: CodeMirror.Position, start: number) {
    setVariable(() => undefined);
    setCursorPosition({ line: cursor.line, ch: start });
    setVariableModalVisible(true);
  }

  const handleEditorDidMount = (editor: CodeMirror.Editor) => {
    if (!editorInstance) {
      setEditorInstance(editor);
      highlightVariables(editor);
    }
    editor.on('endCompletion', () => {
      onEndCompletion(editor, handleNewVariableOpen);
    });
    editor.on('cursorActivity', () => {
      setIsVariableButtonEnabled(isVariableContext(editor));
    });
  };

  const handleVariableInsertion = (variableName: string) => {
    if (variable) {
      return;
    }
    if (editorInstance && cursorPosition) {
      const from = { line: cursorPosition.line, ch: cursorPosition.ch };
      editorInstance.replaceRange(variableName, from, cursorPosition);
    }
  };

  const handleEditVariableButtonClick = () => {
    const pattern = getVariablePattern(editorInstance);
    if (pattern.startsWith(VARIABLE_PREFIX) && pattern.endsWith(VARIABLE_SUFFIX)) {
      const variableName = pattern.slice(2, -2).trim();
      const vari = variables?.find((vari) => vari.apiName === variableName);
      setVariable(vari);
    }
    setVariableModalVisible(true);
  };

  const handleCreateVariableButtonClick = () => {
    if (!editorInstance) {
      setVariableModalVisible(true);
      return;
    }
    const cursor = editorInstance.getCursor();
    setCursorPosition({ line: cursor.line, ch: cursor.ch });
    setVariable(undefined);
    setVariableModalVisible(true);
  };

  const [measurementRef, dimensions] = useDimensions({ liveMeasure: true });

  const editorHeight =
    dimensions.height -
    (document.querySelector('.dataset-sample-output')?.clientHeight || 0) -
    (document.querySelector('.sql-editor__create-variable-container')?.clientHeight || 0) -
    EDITOR_HEIGHT_BUFFER;

  return (
    <div className="sql-editor" ref={measurementRef}>
      <ScrollableArea>
        <div className="sql-editor__create-variable-container">
          <HStack justify="start" className="sql-editor__create-variable-container-title">
            <Text weight="semibold" color="gray-900" size="md">
              {tn('AdvanceDataset.sqle_editor_title')}
            </Text>
            <Tooltip title={tn('AdvanceDataset.sqle_editor_title_tooltip')}>
              <Icon type="question-circle" theme="filled" />
            </Tooltip>
          </HStack>
          <Tooltip title={!isVariableButtonEnabled ? tn('AdvanceDataset.variable_edit_hint') : null}>
            <Button onClick={handleEditVariableButtonClick} disabled={!isVariableButtonEnabled}>
              {tn('AdvanceDataset.edit_variable')}
            </Button>
          </Tooltip>
          <Button onClick={handleCreateVariableButtonClick}>{tn('AdvanceDataset.create_variable')}</Button>
          <Button onClick={() => setShortcutsModalVisible(true)}>
            <Icon type="question-circle" /> {tn('AdvanceDataset.view_shortcuts')}
          </Button>
        </div>
        <div
          style={{
            height: editorHeight,
          }}>
          <CodeMirrorComponent
            value={sql}
            options={sqlEditorOptions}
            onBeforeChange={(editor, data, value) => {
              setSql(value);
            }}
            onChange={(editor) => {
              setSql(editor.getValue());
              highlightVariables(editor);
            }}
            editorDidMount={handleEditorDidMount}
            onCursorActivity={(editor) => {
              isVariableContext(editor);
            }}
          />
        </div>
      </ScrollableArea>
      <DatasetVariablePopoverForm
        visible={variableModalVisible}
        setVisible={setVariableModalVisible}
        onChange={(value: string) => {
          handleVariableInsertion(value);
          setVariable(undefined);
        }}
        defaultValue={variable}
        formType="modal"
      />
      <SQLShortcutsModal visible={shortcutsModalVisible} handleVisibleChange={setShortcutsModalVisible} />
    </div>
  );
};

export default SqlEditor;
