//
// Copyright (c) 2019-Present Syncari All rights reserved.
//

import { Radio } from 'antd';
import cx from 'classnames';
import { noop } from 'lodash/fp';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Controlled as CodeMirror } from 'react-codemirror2';

import { useI18nContext, withI18n } from 'components/I18nProvider';
import InlineMessage from 'components/InlineMessage';
import { HStack, Stack } from 'components/layout';
import { CustomActionTestingResponse } from 'store/custom-action/types';
import { base64DecodeUtf8 } from 'utils/StringUtil';

import 'codemirror/lib/codemirror.css';
import 'codemirror/mode/javascript/javascript';
import './ActionRequestResponse.less';

const INDENT = '    ';

export interface ActionRequestResponseProps {
  className?: string;
  errorMsg?: string;
  requestResponse?: CustomActionTestingResponse;
}

export type RequestContentType = 'payload' | 'response';

export function decodeResponseBody(base64EncodedBody: string) {
  let displayedBody = '';
  if (base64EncodedBody) {
    const rawBody = base64DecodeUtf8(base64EncodedBody || '');
    if (rawBody) {
      displayedBody = rawBody;
      try {
        // Note: Only support json for now. Fall back to plain for non json
        displayedBody = JSON.stringify(JSON.parse(rawBody), null, 4);
      } catch (error) {}
    }
  }

  return displayedBody;
}

const ActionRequestResponse = ({ className, errorMsg, requestResponse }: ActionRequestResponseProps) => {
  const { tn } = useI18nContext();
  const [requestContent, setRequestContent] = useState<RequestContentType>('response');
  const [requestResponseBody, setRequestResponseBody] = useState('');
  const requestOptions = useMemo(() => {
    return [
      {
        label: tn('payload'),
        value: 'payload',
      },
      {
        label: tn('response'),
        value: 'response',
      },
    ] as const;
  }, [tn]);

  const makeBodyHeader = useCallback(
    (reqRes: any) => {
      const bodyHeader = [];
      const headers = reqRes.responseHeaders ? reqRes.responseHeaders : reqRes.requestHeaders;
      if (headers) {
        for (const [key, val] of Object.entries(headers)) {
          bodyHeader.push(`${INDENT}${key}: ${val}`);
        }
      }
      return tn('req_res_headers', {
        name: reqRes.responseHeaders ? tn('response') : tn('request'),
        headers: bodyHeader.join('\n'),
      });
    },
    [tn]
  );

  useEffect(() => {
    const isRequest = requestContent === 'payload';
    const reqRes = isRequest ? requestResponse?.request : requestResponse?.response;
    if (reqRes) {
      const body = reqRes?.body;
      let code = '';
      if (reqRes?.httpStatusCode) {
        code = tn('response_code', { code: reqRes.httpStatusCode });
      }
      let url = '';
      if (reqRes && 'url' in reqRes) {
        url = `${tn('request_url')} ${reqRes.url}\n`;
      }

      let displayedBody = decodeResponseBody(body);

      const resp = tn('req_res_body', { name: requestContent === 'payload' ? tn('request') : tn('response') });

      setRequestResponseBody(`${url}${code}${makeBodyHeader(reqRes)}\n${resp}${displayedBody}`);
      return;
    }
    setRequestResponseBody('');
  }, [makeBodyHeader, requestContent, requestResponse, tn]);

  const options = useMemo(
    () => ({
      matchBrackets: true,
      lineWrapping: true,
      autoCloseBrackets: true,
      mode: 'javascript',
      lineNumbers: true,
    }),
    []
  );

  return (
    <Stack className={cx('synri-action-request-response', className)} spacing="md">
      <InlineMessage className="synri-action-request-response-error-msg" type={'error'} title={errorMsg} allowMultiline>
        {errorMsg}
      </InlineMessage>
      <HStack>
        <Radio.Group
          value={requestContent}
          className="synri-radio-container-flex"
          onChange={(e) => {
            setRequestContent(e.target.value);
          }}>
          {requestOptions.map((option) => {
            return (
              <Radio.Button key={option.value} value={option.value} className="synri-radio-option-flex">
                {option.label}
              </Radio.Button>
            );
          })}
        </Radio.Group>
      </HStack>
      <CodeMirror value={requestResponseBody} options={options} onBeforeChange={noop} />
    </Stack>
  );
};

export default withI18n(ActionRequestResponse, 'ActionSetup');
