import { useCallback } from 'react';
import { UnControlled as CodeMirror } from 'react-codemirror2';

import InputWithLabel from 'components/inputs/InputWithLabel';
import { Stack } from 'components/layout';
import { Text } from 'components/typography';
import { useGetHttpCustomSypapseAuthtypesQuery } from 'store/custom-synapse/http/api';
import { getCodeMirrorOptions } from 'utils/CodeMirrorUtil';
import { tc, tNamespaced } from 'utils/i18nUtil';

import { CustomSynapse } from '../types';
import { AdditionalMetadata } from './AdditionalMetadata';

const tn = tNamespaced('CustomSynapse.HttpCustomSynapse');

export function HTTPCustomSynapseDetails({ synapse }: { synapse: CustomSynapse | undefined }) {
  const { data: supportedAuthType } = useGetHttpCustomSypapseAuthtypesQuery();

  const getAuthConfig = useCallback(
    (authConfig: Record<string, string>) => {
      const getField = (name: string) => {
        for (const { fields } of supportedAuthType || []) {
          const field = fields.find((f) => f.name === name);
          if (field) {
            return field;
          }
        }
        return null;
      };

      return Object.entries(authConfig)
        .filter(([, value]) => value !== null)
        .map(([key, value]) => {
          const field = getField(key);
          return field ? { ...field, value } : null;
        })
        .filter((item) => item !== null);
    },
    [supportedAuthType]
  );
  if (!synapse) {
    return null;
  }
  return (
    <Stack>
      <InputWithLabel label={tc('display_name')} input={<Text>{synapse.displayName}</Text>} />
      <InputWithLabel label={tc('api_name')} input={<Text>{synapse.name}</Text>} />
      <InputWithLabel label={tn('authentication_type')} input={<Text>{synapse.authType}</Text>} />
      {!!synapse.authConfig &&
        getAuthConfig(synapse.authConfig)
          .map(
            (config) =>
              config?.value && (
                <InputWithLabel
                  key={config.value}
                  label={config.label}
                  disabled
                  value={config.value}
                  datatype={config.dataType}
                />
              )
          )
          .filter(Boolean)}
      {!!synapse.method && !!synapse.endpoint && (
        <InputWithLabel
          label={tc('endpoint')}
          input={
            <Text>
              {synapse.method}: {synapse.endpoint}
            </Text>
          }
        />
      )}
      {!!Object.keys(synapse.headers || {}).length && (
        <InputWithLabel
          label={tc('headers')}
          input={
            <Text>
              {Object.keys(synapse.headers || {}).map((key) => {
                return (
                  <div>
                    <Text>
                      {key}: {synapse.headers?.[key]}
                    </Text>
                  </div>
                );
              })}
            </Text>
          }
        />
      )}
      {!!synapse.body && (
        <InputWithLabel
          label={tc('body')}
          input={
            <Text>
              <CodeMirror className="code-mirror-container" value={synapse.body} options={getCodeMirrorOptions(true)} />
            </Text>
          }
        />
      )}
      {!!synapse.variables?.length && (
        <InputWithLabel
          label={tn('additional_metadata')}
          input={<AdditionalMetadata onChange={() => {}} defaultValue={synapse.variables} readonly />}
        />
      )}
      {!!synapse.variableValues?.length &&
        synapse.variableValues?.map((vari) => {
          return <InputWithLabel key={vari.name} label={vari.name} input={<Text>{vari.value}</Text>} />;
        })}
    </Stack>
  );
}
