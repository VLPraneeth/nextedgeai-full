import { Button } from 'antd';
import { Editor, EditorChange } from 'codemirror';
import { useCallback } from 'react';
import { Controlled as CodeMirror } from 'react-codemirror2';

import { Stack } from 'components/layout';
import { HTTPCustomSynapseEntity } from 'store/custom-synapse/types';
import { getCodeMirrorOptions } from 'utils/CodeMirrorUtil';
import { tNamespaced } from 'utils/i18nUtil';

const tn = tNamespaced('CustomSynapse.HttpCustomSynapse.Entities');

export function EntitySchema({
  entity,
  setEntity,
  handleGenerateSchema,
  readOnly,
}: {
  entity: Partial<HTTPCustomSynapseEntity>;
  setEntity: (entity: Partial<HTTPCustomSynapseEntity>) => void;
  handleGenerateSchema: () => void;
  readOnly?: boolean;
}) {
  const handleSchemChange = useCallback(
    (editor: Editor, data: EditorChange, schema: string) => {
      setEntity({
        schema,
      });
    },
    [setEntity]
  );

  return (
    <Stack spacing="md">
      <CodeMirror
        className="code-mirror-container"
        value={entity.schema || ''}
        options={getCodeMirrorOptions(readOnly)}
        onBeforeChange={handleSchemChange}
      />

      <div className="entity-config-step__schema-testing-button">
        <Button disabled={readOnly} onClick={handleGenerateSchema}>
          {tn('use_response_from_testing')}
        </Button>
      </div>
    </Stack>
  );
}
