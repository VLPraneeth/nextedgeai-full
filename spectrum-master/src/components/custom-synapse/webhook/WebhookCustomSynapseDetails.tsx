import { UnControlled as CodeMirror } from 'react-codemirror2';

import InputWithLabel from 'components/inputs/InputWithLabel';
import { Stack } from 'components/layout';
import { Text } from 'components/typography';
import { getCodeMirrorOptions } from 'utils/CodeMirrorUtil';
import { tc, tNamespaced } from 'utils/i18nUtil';

import { CustomSynapse } from '../types';

const tn = tNamespaced('CustomSynapse.WebhookCustomSynapse');

export function WebhookCustomSynapseDetails({ synapse }: { synapse: CustomSynapse | undefined }) {
  if (!synapse) {
    return null;
  }
  return (
    <Stack>
      <InputWithLabel label={tc('display_name')} input={<Text>{synapse.displayName}</Text>} />
      <InputWithLabel label={tc('api_name')} input={<Text>{synapse.name}</Text>} />
      <InputWithLabel label={tn('authentication_type')} input={<Text>{synapse.authType}</Text>} />

      {!!synapse.idSelector && <InputWithLabel label={tn('id_selector')} input={<Text>{synapse.idSelector}</Text>} />}
      {!!synapse.recordSelector && (
        <InputWithLabel label={tn('record_selector')} input={<Text>{synapse.recordSelector}</Text>} />
      )}
      {!!synapse.schema && (
        <InputWithLabel
          label={tc('schema')}
          input={
            <Text>
              <CodeMirror
                className="code-mirror-container"
                value={synapse.schema}
                options={getCodeMirrorOptions(true)}
              />
            </Text>
          }
        />
      )}
      {!!synapse.responseCode && (
        <InputWithLabel label={tn('http_status')} input={<Text>{synapse.responseCode}</Text>} />
      )}
      {!!synapse.responseTemplate && (
        <InputWithLabel
          label={tn('response_template')}
          input={
            <Text>
              <CodeMirror
                className="code-mirror-container"
                value={synapse.responseTemplate}
                options={getCodeMirrorOptions(true)}
              />
            </Text>
          }
        />
      )}
    </Stack>
  );
}
