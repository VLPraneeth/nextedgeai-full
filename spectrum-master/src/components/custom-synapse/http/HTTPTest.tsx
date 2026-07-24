import { Button, Icon, message } from 'antd';
import { Editor, EditorChange } from 'codemirror';
import { useCallback, useState } from 'react';
import { Controlled as CodeMirror } from 'react-codemirror2';

import { ReactComponent as BodyIcon } from 'assets/icons/code.svg';
import { ReactComponent as HeadersIcon } from 'assets/icons/headers.svg';
import ActionHeader, { Header } from 'components/custom-action/ActionHeader';
import ActionRequestResponse from 'components/custom-action/ActionRequestResponse';
import { InlineTab, InlineTabs } from 'components/InlineTabs';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { SelectTextValue } from 'components/inputs/select-text/SelectText';
import { HStack, Spacer } from 'components/layout';
import { useHttpCustomSypapseTestMutation } from 'store/custom-synapse/http/api';
import AppConstants from 'utils/AppConstants';
import { getCodeMirrorOptions } from 'utils/CodeMirrorUtil';
import { getRtkQueryErrorMessage } from 'utils/getRtkQueryErrorMessage';
import { tc, tNamespaced } from 'utils/i18nUtil';
import { HTTP_URL } from 'utils/RegexUtil';

import { CustomSynapse } from '../types';
import 'codemirror/lib/codemirror.css';
import 'codemirror/mode/javascript/javascript';

const tn = tNamespaced('CustomSynapse.HttpCustomSynapse');

interface TestEndpointProps {
  httpSynapse: Partial<CustomSynapse>;
  setFormData: (newState: Partial<CustomSynapse>) => void;
}

const httpMethods = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE'];
export const httpMethodPiclistValues = httpMethods.map((method) => ({ label: method, value: method }));

export function HTTPTest({ httpSynapse, setFormData }: TestEndpointProps) {
  const [currentTab, setCurrentTab] = useState('headers');

  const [isValidUrl, setIsValidUrl] = useState(true);

  const [testHttpCustomSynapse, { data, isLoading }] = useHttpCustomSypapseTestMutation();

  const handleEndpointChange = useCallback(
    (value: SelectTextValue) => {
      if (value.textValue && !HTTP_URL.test(value.textValue)) {
        setIsValidUrl(false);
      } else {
        setIsValidUrl(true);
      }

      setFormData({
        endpoint: value.textValue,
        method: value.selectValue,
      });
    },
    [setFormData]
  );

  const handleHeadersChange = useCallback(
    (headers: Header[]) => {
      setFormData({
        headers: headers.reduce((acc, header) => {
          if (header.key && header.value) {
            acc[header.key] = header.value;
          }
          return acc;
        }, {} as Record<string, string>),
      });
    },
    [setFormData]
  );

  const handleBodyChange = useCallback(
    (editor: Editor, data: EditorChange, body: string) => {
      setFormData({
        body,
      });
    },
    [setFormData]
  );

  return (
    <div>
      <div className="http_custom_synapse_config_step__endpoint-test">
        <HStack align={isValidUrl ? 'end' : 'center'}>
          <InputWithLabel
            className="http_custom_synapse_config_step__endpoint-input"
            label={tc('endpoint')}
            datatype={AppConstants.INPUT_TYPE.SELECT_TEXT}
            value={
              {
                selectValue: httpSynapse.method,
                textValue: httpSynapse.endpoint,
              } as SelectTextValue
            }
            selectPicklistValues={httpMethodPiclistValues}
            onChange={handleEndpointChange}
            help={!isValidUrl ? tn('invalid_url') : ''}
            validateStatus={!isValidUrl ? 'error' : ''}
          />

          <Button
            type="default"
            loading={isLoading}
            disabled={!isValidUrl}
            onClick={() => {
              testHttpCustomSynapse({ ...httpSynapse, body: JSON.stringify(httpSynapse.body || '') })
                .unwrap()
                .catch((err) => message.error(getRtkQueryErrorMessage(err)));
            }}>
            {tc('test')}
          </Button>
        </HStack>
      </div>

      <InlineTabs selectedTab={currentTab} onChange={(tab) => setCurrentTab(tab)}>
        <InlineTab id="headers">
          <HStack align="center">
            <Icon component={(props) => <HeadersIcon {...props} width={16} height={16} />} />
            <span>{tc('headers')}</span>
          </HStack>
        </InlineTab>

        <InlineTab id="body">
          <HStack align="center">
            <Icon component={(props) => <BodyIcon {...props} width={16} height={16} />} />
            <span>{tc('body')}</span>
          </HStack>
        </InlineTab>
      </InlineTabs>

      {currentTab === 'headers' && (
        <ActionHeader
          defaultValue={Object.keys(httpSynapse.headers || {}).map((key) => ({
            key,
            value: httpSynapse.headers?.[key],
          }))}
          onChange={handleHeadersChange}
        />
      )}
      {currentTab === 'body' && (
        <>
          <Spacer y="lg" />
          <CodeMirror
            className="code-mirror-container"
            value={httpSynapse.body || ''}
            options={getCodeMirrorOptions()}
            onBeforeChange={handleBodyChange}
          />
        </>
      )}

      <ActionRequestResponse requestResponse={data} />
    </div>
  );
}
