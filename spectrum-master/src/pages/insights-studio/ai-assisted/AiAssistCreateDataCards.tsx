// import TextArea from 'antd/lib/input/TextArea';
import { Icon } from 'antd';
import cx from 'classnames';
import { useCallback, useState, useRef } from 'react';

import { ReactComponent as SyncAiIcon } from 'assets/icons/sync-ai.svg';
import { BouncingLoader } from 'components/BouncingLoader';
import { useI18nContext, withI18n } from 'components/I18nProvider';
import { Stack } from 'components/layout';
import { ScrollableArea } from 'components/scrollable-area/ScrollableArea';
import { Text } from 'components/typography';
import { Vizer } from 'components/vizer/Vizer';
import { useGetAllDataCardsQuery, useGetDatasetsQuery, usePreviewDataCardMutation } from 'store/insights-studio';
import { DataCard, DataCardWithData } from 'store/insights-studio/types';

import TitleBar from '../components/data-card/TitleBar';
import { useAddCardToDashboard } from '../utils/dashboardUtils';
import { useUnifiedDataCardNavigate } from '../utils/useUnifiedDataCardNavigate';

import './AiAssistCreateDataCards.scss';

const insightsGptTag = 'insightsgpt';

export interface AiRequests {
  request: string;
  answer?: DataCardWithData;
  isLoading: boolean;
}

// Small delay before showing the AI answer
const SHOW_ANSWER_DELAY = 3000;

const SampleQuestions = [
  'Show me my top ten customers by product usage',
  'How many users have not logged in the past 20 days',
  'Weekly stage 2 opportunities in the last 3 months',
];
export const AiAssistCreateDataCards = withI18n(() => {
  const [request, setRequest] = useState('');
  const [requests, setRequests] = useState<AiRequests[]>([]);
  const { data: dataCards } = useGetAllDataCardsQuery();
  const [previewDataCard] = usePreviewDataCardMutation();
  const { data: datasets } = useGetDatasetsQuery();
  const { tn } = useI18nContext();

  const { aiAssistedMatch, navigateToCurrentDashboard } = useUnifiedDataCardNavigate();

  const answerEndRef = useRef<HTMLDivElement>(null);
  const addCard = useAddCardToDashboard(aiAssistedMatch?.dashboardId);

  const onEnter = useCallback(
    async (localRequest?: string) => {
      localRequest = localRequest || request;

      if (!localRequest) {
        return;
      }

      let result;
      let datacard: DataCard | null = null;
      let currentMatchCount = 0;
      dataCards?.forEach((dataCard) => {
        const tags = dataCard.tags?.map((tag) => tag.toLowerCase());
        const isInsightsGPT = tags?.includes(insightsGptTag);
        if (isInsightsGPT) {
          let tagMatchCount = tags.filter(
            (tag) => tag !== insightsGptTag && localRequest?.indexOf(tag?.toLowerCase()) !== -1
          ).length;
          if (currentMatchCount < tagMatchCount) {
            datacard = dataCard;
            currentMatchCount = tagMatchCount;
            console.log(
              'Selected ' + dataCard.id + ', last match count=' + currentMatchCount + ',current count=' + tagMatchCount
            );
          }
        }
      });

      if (datacard) {
        const dataset = datasets?.find((dataset) => dataset.id === datacard?.contents?.configuration.datasetId);
        if (dataset) {
          result = (await previewDataCard({ datacard, dataset })) as { data: DataCardWithData };
        }
      }

      setRequests([
        ...requests,
        {
          request: localRequest,
          answer: result?.data,
          isLoading: true,
        },
      ]);

      setRequest('');
      scrollIntoView();

      // Delay the answer to simulate answer from the server and scroll for great effect ;)
      window.setTimeout(() => {
        setRequests((current) => {
          return current.map((curr) => {
            return {
              ...curr,
              isLoading: false,
            };
          });
        });
        scrollIntoView();
      }, SHOW_ANSWER_DELAY);
    },
    [dataCards, datasets, previewDataCard, request, requests]
  );

  const scrollIntoView = () => {
    window.setTimeout(() => {
      if (answerEndRef?.current) {
        answerEndRef.current.scrollIntoView({ behavior: 'smooth' });
      }
    }, 500);
  };

  const addToDashboard = useCallback(
    (index: number) => {
      const dataCard = requests[index]?.answer;
      if (dataCard) {
        addCard(dataCard.id);
        navigateToCurrentDashboard();
      }
    },
    [addCard, navigateToCurrentDashboard, requests]
  );

  return (
    <div className="ai-assist-create-data-cards">
      <div className="ai-assist-create-data-cards__title-wrapper">
        <Icon component={(props) => <SyncAiIcon {...props} />} aria-label={tn('title')} role="button" />
        <div className="ai-assist-create-data-cards__title-wrapper__title">{tn('youre_chatting')}</div>
      </div>
      <ScrollableArea className="ai-assist-create-data-cards__ai-response-wrapper">
        <Stack spacing="sm" className="ai-assist-create-data-cards__ai-response-wrapper__suggestions">
          <Text color="gray-800" size="lg">
            {tn('welcome_ask_me')}
          </Text>
          {SampleQuestions.map((question) => (
            <Text color="gray-800" size="lg">
              <a onClick={() => onEnter(question)}>{question}</a>
            </Text>
          ))}
        </Stack>
        {Boolean(requests.length) && (
          <div className="ai-assist-create-data-cards__ai-response-wrapper__chat">
            {requests.length &&
              requests.map((req, index) => {
                const requestLoading = req.isLoading;
                return (
                  <div key={index}>
                    <div className="ai-assist-create-data-cards__ai-response-wrapper__chat__wrapper">
                      <div className="ai-assist-create-data-cards__ai-response-wrapper__chat__wrapper__bubble">
                        <Text color="green-300" size="lg">
                          {req.request}
                        </Text>
                      </div>
                    </div>
                    <div className="ai-answer-wrapper">
                      <div
                        className={cx(
                          'ai-answer-wrapper__datacard-wrapper',
                          requestLoading && 'ai-answer-wrapper__datacard-wrapper--loading'
                        )}>
                        {requestLoading ? (
                          <BouncingLoader />
                        ) : req.answer ? (
                          <div className="ai-answer-wrapper__datacard-wrapper__answer" style={{}}>
                            <TitleBar
                              name={req.answer?.displayName || ''}
                              description={req.answer?.description || ''}
                              // @ts-ignore
                              dataCard={req.answer}
                              showConfigButton={false}
                              showEditControls={false}
                            />
                            <Vizer
                              // @ts-ignore
                              dataCardContent={req.answer?.contents}
                              graphHeight={300}
                              dataConfiguration={{}}
                              key="test"
                            />
                            <div className="ai-answer-wrapper__datacard-wrapper__add-to-dashboard">
                              <Text color="gray-800" size="md">
                                <a
                                  onClick={() => {
                                    addToDashboard(index);
                                  }}>
                                  <Icon type="plus" />
                                  {tn('add_to_dashboard')}
                                </a>
                              </Text>
                            </div>
                          </div>
                        ) : (
                          <span className="ai-answer-wrapper__datacard-wrapper--unexpected-error">
                            {tn('unexpected_error')}
                          </span>
                        )}
                      </div>
                    </div>
                  </div>
                );
              })}
            <div className="ai-assist-create-data-cards__ai-response-wrapper__chat__end" ref={answerEndRef} />
          </div>
        )}
      </ScrollableArea>

      <div className="ai-assist-create-data-cards__question-wrapper">
        <textarea
          placeholder={tn('type_question')}
          className="ai-assist-create-data-cards__question-wrapper__question"
          onChange={(e) => {
            e.preventDefault();
            setRequest(e.target.value);
          }}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              onEnter();
              setRequest('');
              e.preventDefault();
            }
          }}
          value={request}
        />
      </div>
    </div>
  );
}, 'InsightsStudio.InsightsGPT');
