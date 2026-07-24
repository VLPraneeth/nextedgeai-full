import { useMatch } from '@reach/router';
import { Modal, Tooltip } from 'antd';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Controlled as CodeMirror } from 'react-codemirror2';

import Button from 'components/Button';
import { useI18nContext, withI18n } from 'components/I18nProvider';
import { HStack, Stack } from 'components/layout';
import { useLayoutContext } from 'pages/LayoutContext';
import { tags } from 'store/api';
import {
  useGetPipelineDocumentationQuery,
  useLazyGeneratePipelineDocumentationQuery,
  useSavePipelineDocumentationMutation,
  pipelineApiUtil,
} from 'store/pipeline/api';
import { useGetCustomPreferenceQuery, useSetCustomPreferenceMutation } from 'store/user/api';
import AppConstants from 'utils/AppConstants';
import { getPipelineDraftStatus } from 'utils/PipelineUtil';
import { safeDecodeBase64 } from 'utils/StringUtil';

import { Markdown } from './Markdown';
import 'codemirror/lib/codemirror.css';
import 'codemirror/mode/markdown/markdown';

import './PipelineDocumentation.scss';

const { GRAPH_STATUS } = AppConstants;

const PipelineDocumentation = () => {
  const [editMode, setEditMode] = useState(false);
  const [document, setDocument] = useState('');
  const layout = useLayoutContext();
  const [editor, setEditor] = useState<CodeMirror.Editor | null>(null);
  const pipelineDocumentationUrl = useMatch('/sync-studio/entity/:entityId/documentation/:graphVersion/*');
  const [savePipelineDocumetation] = useSavePipelineDocumentationMutation();
  const version = getPipelineDraftStatus(pipelineDocumentationUrl?.graphVersion?.toUpperCase() || GRAPH_STATUS.NEW);
  const { data: initialPipelineDocumentation } = useGetPipelineDocumentationQuery({
    syncariEntityId: pipelineDocumentationUrl?.entityId || '',
    version,
  });
  const [saved, setSaved] = useState(false);

  const { tn, tc } = useI18nContext();

  const [
    generatePipelineDocumentation,
    { data: pipelineDocumentation, isFetching, isLoading },
  ] = useLazyGeneratePipelineDocumentationQuery();

  const generatingDocument = isFetching || isLoading;

  useEffect(() => {
    return () => {
      if (pipelineDocumentationUrl?.entityId) {
        pipelineApiUtil.invalidateTags([
          tags.PipelineDocumentation(pipelineDocumentationUrl.entityId),
          tags.PipelineDocumentationList,
        ]);
      }
    };
  }, [pipelineDocumentationUrl?.entityId]);

  useEffect(() => {
    if (Number(document?.length) <= 0 && initialPipelineDocumentation?.content) {
      setDocument(safeDecodeBase64(initialPipelineDocumentation.content));
      setSaved(true);
    }
  }, [document?.length, initialPipelineDocumentation]);

  const options = useMemo(
    () => ({
      highlightFormatting: true,
      maxBlockquoteDepth: 0,
      fencedCodeBlockHighlighting: true,
      mode: 'markdown',
      lineNumbers: true,
    }),
    []
  );

  useEffect(() => {
    if (pipelineDocumentation?.content && !isFetching && !isLoading) {
      setDocument(safeDecodeBase64(pipelineDocumentation?.content));
      setSaved(false);
    }
  }, [isFetching, isLoading, pipelineDocumentation]);

  const updateSize = useCallback(
    (editor?: CodeMirror.Editor) => {
      editor?.setSize(layout.dimensions.content.width - 80, layout.dimensions.content.height);
    },
    [layout.dimensions.content.height, layout.dimensions.content.width]
  );

  useEffect(() => {
    editor && updateSize(editor);
  }, [editor, layout.dimensions.content.height, updateSize]);

  const [setCustomPreference] = useSetCustomPreferenceMutation();
  const { data: customPref, refetch: refetchCustomPreference } = useGetCustomPreferenceQuery();

  const generateDoc = useCallback(() => {
    generatePipelineDocumentation({
      syncariEntityId: pipelineDocumentationUrl?.entityId || '',
      version: getPipelineDraftStatus(pipelineDocumentationUrl?.graphVersion?.toUpperCase() || GRAPH_STATUS.NEW),
    });
  }, [generatePipelineDocumentation, pipelineDocumentationUrl?.entityId, pipelineDocumentationUrl?.graphVersion]);

  const isPublishedDocument = useMemo(() => version === GRAPH_STATUS.APPROVED, [version]);
  const disabledMessage = useMemo(() => isPublishedDocument && tn('cannot_edit_published'), [isPublishedDocument, tn]);

  return (
    <Stack fill className="pipeline-documentation">
      <HStack className="pipeline-documentation__actions">
        <Tooltip title={disabledMessage}>
          <div>
            <Button
              disabled={!Boolean(document) || generatingDocument || isPublishedDocument}
              onClick={() => setEditMode(!editMode)}>
              {editMode ? tc('view') : tc('edit')}
            </Button>
          </div>
        </Tooltip>
        <Button
          type="primary"
          disabled={saved || generatingDocument}
          onClick={() => {
            savePipelineDocumetation({
              syncariEntityId: pipelineDocumentationUrl?.entityId || '',
              content: btoa(document),
              version: getPipelineDraftStatus(
                pipelineDocumentationUrl?.graphVersion?.toUpperCase() || GRAPH_STATUS.NEW
              ),
            }).then(() => setSaved(true));
          }}>
          {tc('save')}
        </Button>
        <Tooltip title={disabledMessage}>
          <div>
            <Button
              type="primary"
              disabled={generatingDocument || isPublishedDocument}
              loading={generatingDocument}
              onClick={() => {
                if (customPref?.PipelineDocumentationAgreeTos !== true) {
                  Modal.confirm({
                    title: tn('generate'),
                    content: tn('tos'),
                    okText: tc('continue'),
                    cancelText: tc('cancel'),
                    onOk: () => {
                      setCustomPreference({ PipelineDocumentationAgreeTos: true })
                        .unwrap()
                        .then(() => {
                          generateDoc();
                          refetchCustomPreference();
                        });
                    },
                  });
                } else {
                  generateDoc();
                }
              }}>
              {tn('generate')}
            </Button>
          </div>
        </Tooltip>
      </HStack>
      <Stack fill className="pipeline-documentation__markdown">
        {!editMode ? (
          <Markdown style={{ maxHeight: layout.dimensions.content.height, overflow: 'auto' }}>{document}</Markdown>
        ) : (
          <CodeMirror
            value={document}
            options={options}
            onBeforeChange={(_, __, value) => {
              setDocument(value);
              setSaved(false);
            }}
            editorDidMount={(editor: CodeMirror.Editor) => {
              setEditor(editor);
              updateSize(editor);
            }}
          />
        )}
      </Stack>
    </Stack>
  );
};

export default withI18n(PipelineDocumentation, 'PipelineDocumentation');
