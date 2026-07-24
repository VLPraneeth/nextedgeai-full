import { Tooltip } from 'antd';
import { map } from 'lodash';
import { useState } from 'react';

import { ReactComponent as ClipboardIcon } from 'assets/icons/copy-clipboard.svg';
import { CopyToClipboard } from 'components/copy-to-clipboard/CopyToClipboard';
import { JsonRendererPopover } from 'components/JsonRendererPopover';
import { HStack, Stack } from 'components/layout';
import ShowWhiteSpaceChars from 'components/ShowWhiteSpaceChars';
import { Text } from 'components/typography';
import { useUtcTimeInUsersTimezone } from 'hooks/moment';
import { TestRunModel } from 'store/test/types';
import { tc, tNamespaced } from 'utils/i18nUtil';

import { useTestRunCriteria } from '../test-panels/test-hooks/TestResultPanel.hooks';

import './TestResultCriteria.scss';

const tn = tNamespaced('TestResultContent');

const PREVIEW_SIZE = 3;

interface Props {
  testRun: TestRunModel;
}

const TestResultCriteria = ({ testRun }: Props) => {
  const criteria = useTestRunCriteria(testRun);
  const formatUtcToConfiguredTime = useUtcTimeInUsersTimezone();

  const [showFullList, setShowFullList] = useState(false);

  switch (criteria.type) {
    case 'recordIds':
      const showPreview = criteria.idList.length > PREVIEW_SIZE + 1 && !showFullList;
      const visibleList = showPreview ? criteria.idList.slice(0, PREVIEW_SIZE) : criteria.idList;

      return (
        <div className="synri-test-result-criteria-container">
          <Stack spacing="xxs" className="synri-test-result-criteria-container__external_id">
            {map(visibleList, ({ label, id }) => (
              <HStack spacing="xxs">
                <div className="label">
                  <Tooltip key={id} title={`${label} - ${id}`}>
                    <Text>{label}:</Text>

                    <HStack spacing="xxxs">
                      <ShowWhiteSpaceChars>{id}</ShowWhiteSpaceChars>
                    </HStack>
                  </Tooltip>
                </div>
                <CopyToClipboard textToCopy={id} customClipboardIcon={ClipboardIcon} />
              </HStack>
            ))}
            {showPreview && (
              <a onClick={() => setShowFullList(true)}>
                {tn('more_external_ids', { count: criteria.idList.length - PREVIEW_SIZE })}
              </a>
            )}
          </Stack>
        </div>
      );

    case 'dateRange':
      const startDate = tn('start_date', {
        // startDate is in UTC, so create as UTC then convert to local and format
        date: formatUtcToConfiguredTime(criteria.startDate),
        interpolation: { escapeValue: false },
      });
      const endDate = tn('end_date', {
        // startDate is in UTC, so create as UTC then convert to local and format
        date: formatUtcToConfiguredTime(criteria.endDate),
        interpolation: { escapeValue: false },
      });

      return (
        <div className="synri-test-result-criteria-container">
          <Stack spacing="xxs">
            <Text>{startDate}</Text>
            <Text>{endDate}</Text>
            <Text>{tn('limit_count', { count: testRun.limit })}</Text>
          </Stack>
        </div>
      );
    case 'webhook':
      if (testRun.webhook) {
        const key = Object.keys(testRun.webhook)[0];

        return (
          <div className="synri-test-result-criteria-container">
            <HStack spacing="xxs">
              {<Text>{`${tc('payload')} - `}</Text>} <JsonRendererPopover jsonString={testRun.webhook[key].payload} />
            </HStack>
          </div>
        );
      }
      return <div>-</div>;
  }
};

export default TestResultCriteria;
