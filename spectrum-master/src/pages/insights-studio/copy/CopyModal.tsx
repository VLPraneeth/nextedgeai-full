//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { navigate } from '@reach/router';
import { Button, message } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { useMemo } from 'react';

import InlineMessage, { Types as InlineMessageTypes } from 'components/InlineMessage';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { ValidateStatuses } from 'components/inputs/types';
import Modal from 'components/Modal';
import {
  useCreateDataCardMutation,
  useCreateDatasetMutation,
  useGetAllDataCardsQuery,
  useGetDashboardsQuery,
  useGetDatasetsQuery,
} from 'store/insights-studio';
import { Dataset } from 'store/insights-studio/types';
import { getRtkQueryErrorMessage } from 'utils/getRtkQueryErrorMessage';
import { tc, tNamespaced } from 'utils/i18nUtil';
import RouteConstants from 'utils/RouteConstants';
import { generateUniqueName } from 'utils/StringUtil';
import { makeUrl } from 'utils/UrlUtil';

import { NewCardInfo } from '../context/DataCardAuthoringContext';
import { useInsightsViewContext } from '../context/InsightsViewContext';
import { useAddCardToDashboard } from '../utils/dashboardUtils';
import { useUnifiedDataCardNavigate } from '../utils/useUnifiedDataCardNavigate';

const tn = tNamespaced('InsightsStudio');

const UNIQUE_NAME_REGEX = /(\d+)$/;

const CopyModal = () => {
  const [displayName, setDisplayName] = useState('');
  const [validation, setValidation] = useState<Record<string, string>>({});
  const {
    copyDataCardMatch,
    copyAddDataCardMatch,
    copyDatasetMatch,
    thoughtspotCopyDatasetMatch,
    navigateToCurrentDashboard,
  } = useUnifiedDataCardNavigate();
  const { isThoughtSpotView } = useInsightsViewContext();

  const { data: dataCards } = useGetAllDataCardsQuery();
  // When in thoughtspot routes, always use thoughtspot datasets (matching DatasetList behavior)
  const isThoughtspotRoute = window.location.pathname.toLowerCase().includes('insights-studio/ts/');
  const { data: datasets } = useGetDatasetsQuery(isThoughtspotRoute || isThoughtSpotView);

  const { data: dashboards } = useGetDashboardsQuery();
  const [createDataCard, { isLoading: isDatacardCreating }] = useCreateDataCardMutation();
  const [saveDataset, { isLoading: isDatasetCreating }] = useCreateDatasetMutation();

  const dashboardId = copyDataCardMatch?.dashboardId || copyAddDataCardMatch?.dashboardId;

  const dataCardId = copyDataCardMatch?.dataCardId || copyAddDataCardMatch?.dataCardId;
  const datasetId = copyDatasetMatch?.datasetId || thoughtspotCopyDatasetMatch?.datasetId;

  const dashboard = dashboards?.find((dashboard) => dashboard.id === dashboardId);
  const addCard = useAddCardToDashboard(dashboard?.draft?.id || dashboard?.id);

  const [errorMessage, setErrorMessage] = useState('');

  useEffect(() => {
    if (!Boolean(dataCardId || datasetId)) {
      setErrorMessage('');
    }
  }, [dataCardId, datasetId]);

  const formValidate = () => {
    if (!displayName) {
      setValidation({
        displayNameStatus: ValidateStatuses.ERROR,
        displayNameHelp: tc('cannot_be_empty', { name: tc('display_name') }),
      });
      return false;
    }
    setValidation({});
    return true;
  };

  const handleClose = useCallback(() => {
    if (isThoughtSpotView) {
      navigate(makeUrl(RouteConstants.INSIGHTS_STUDIO_TS_DATASETS));
    } else {
      navigateToCurrentDashboard();
    }
  }, [navigateToCurrentDashboard, isThoughtSpotView]);

  const copy = (evt: React.FormEvent) => {
    evt?.target && evt.preventDefault();
    if (!formValidate()) {
      return;
    }

    // Copying a data card
    if (dataCardId) {
      const dataCard = dataCards?.find((dataCard) => dataCard.id === dataCardId);
      if (dataCard?.contents?.configuration) {
        const { id, name, ...restDataCard } = dataCard;
        const newDataCard: NewCardInfo = {
          ...restDataCard,
          name: '',
          displayName,
          description: dataCard.description,
          tags: dataCard.tags,
        };
        createDataCard(newDataCard)
          .then((result) => {
            if ('data' in result && dataCard.contents) {
              message.success(tn('data_card_created'));
              if (copyAddDataCardMatch?.dashboardId && dashboard) {
                addCard(result.data.id);
              }
              handleClose();
            } else if ('error' in result) {
              setErrorMessage(getRtkQueryErrorMessage(result.error));
            }
          })
          .catch((error) => setErrorMessage(getRtkQueryErrorMessage(error)));
      }
    } else if (datasetId) {
      const dataset = datasets?.find((dataset) => dataset.id === datasetId);
      if (dataset) {
        const { id, name, ...datasetRest } = dataset;
        saveDataset({
          ...datasetRest,
          displayName,
        } as Dataset)
          .then((result) => {
            if ('data' in result) {
              message.success(tn('data_set_created'));
              handleClose();
            } else if ('error' in result) {
              setErrorMessage(getRtkQueryErrorMessage(result.error));
            }
          })
          .catch((error) => setErrorMessage(getRtkQueryErrorMessage(error)));
      }
    }
  };

  const title = useMemo(() => {
    let suggestedName = '';
    let displayName: string | undefined;
    if (dataCards && dataCardId) {
      ({ suggestedName, displayName } = suggestName(dataCards, dataCardId));
    } else if (datasets && datasetId) {
      ({ suggestedName, displayName } = suggestName(datasets, datasetId));
    }
    if (displayName) {
      const newNumber = suggestedName.match(UNIQUE_NAME_REGEX)?.[0];
      if (newNumber) {
        setDisplayName(tn('copy_sugg_name', { name: displayName, number: newNumber }));
      }
      return tn('duplicate_title', { name: displayName });
    }
    return tc('make_copy');
  }, [dataCardId, dataCards, datasetId, datasets]);

  const onTextChange = (evt: React.ChangeEvent<HTMLInputElement>) => setDisplayName(evt.target.value);

  return (
    <Modal
      title={title}
      centered
      visible={!!dataCardId || !!datasetId}
      footer={
        <>
          <Button key="cancel" onClick={handleClose}>
            {tc('cancel')}
          </Button>
          <Button key="ok" type="primary" onClick={copy} loading={isDatacardCreating || isDatasetCreating}>
            {tc('copy')}
          </Button>
        </>
      }
      onOk={handleClose}
      onCancel={handleClose}
      destroyOnClose>
      <div className="content-container">
        <InlineMessage type={InlineMessageTypes.ERROR} title={errorMessage}>
          {errorMessage}
        </InlineMessage>
        <form onSubmit={copy}>
          <InputWithLabel
            name="displayName"
            datatype="string"
            label={tc('display_name')}
            value={displayName}
            onChange={onTextChange}
            validateStatus={validation?.displayNameStatus}
            help={validation?.displayNameHelp}
          />
        </form>
      </div>
    </Modal>
  );
};

export default CopyModal;

function suggestName<T extends { id: string; displayName: string }>(
  items: T[],
  itemId: string
): { suggestedName: string; displayName?: string } {
  let suggestedName = '';
  const foundItem = items?.find((item) => item.id === itemId);
  if (foundItem) {
    suggestedName = generateUniqueName(foundItem.displayName, (newDisplayName) => {
      const newNumber = newDisplayName.match(UNIQUE_NAME_REGEX)?.[0];
      return !!items?.find(
        (newDataset) =>
          newDataset.displayName ===
          (newNumber ? tn('copy_sugg_name', { name: foundItem.displayName, number: newNumber }) : newDisplayName)
      );
    });
  }
  return { suggestedName, displayName: foundItem?.displayName };
}
