import { Button, Select } from 'antd';
import { isEqual } from 'lodash';
import { ChangeEvent, SetStateAction, useEffect, useMemo, useState } from 'react';

import DrawerPanel from 'components/DrawerPanel';
import ClearFilterButton from 'components/filter-components/ClearFilterButton';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { EMPTY_ARRAY } from 'store/constants';
import AppConstants from 'utils/AppConstants';
import { tNamespaced } from 'utils/i18nUtil';

import { PipelineDetailsPanelNames, usePipelineDetailsSearchParameters } from '../PipelineDetails';
import { usePipelineDetailsFilterPanel } from './PipelineDetailsFilterPanel.hooks';
import { filterStateToSearchParam } from './PipelineDetailsFilterPanel.utils';

import './PipelineDetailsFilterPanel.scss';

const tc = tNamespaced('Common');
const tn = tNamespaced('PipelineDetailsFilterPanel');

export type PipelineStatusState = {
  draft: boolean;
  published: boolean;
  unmapped: boolean;
};

export const pipelineStatusInit: PipelineStatusState = {
  draft: false,
  published: false,
  unmapped: false,
};

export type PipelineStateState = {
  running: boolean;
  paused: boolean;
  resyncing: boolean;
  pausing: boolean;
  queued: boolean;
  error: boolean;
  warning: boolean;
};

export const pipelineStateInit: PipelineStateState = {
  running: false,
  paused: false,
  resyncing: false,
  pausing: false,
  queued: false,
  error: false,
  warning: false,
};

const stateHandlerFactory = (setState: SetStateAction<any>) => {
  return (event: ChangeEvent<HTMLInputElement>) => {
    setState((previousState: Record<string, any>) => ({
      ...previousState,
      [event.target.name]:
        event.target.type === AppConstants.INPUT_TYPE.CHECKBOX ? event.target.checked : event.target.value,
    }));
  };
};

export const PipelineDetailsFilterPanel = () => {
  const { values: searchParamValues, updateSearchParams } = usePipelineDetailsSearchParameters();
  const { sourceOptions, destinationOptions } = usePipelineDetailsFilterPanel();

  const visible = searchParamValues.panel === PipelineDetailsPanelNames.FILTER;

  const [pipelineStatus, setPipelineStatus] = useState<PipelineStatusState>(pipelineStatusInit);
  const [pipelineState, setPipelineState] = useState<PipelineStateState>(pipelineStateInit);
  const [sources, setSources] = useState<string[]>(EMPTY_ARRAY);
  const [destinations, setDestinations] = useState<string[]>(EMPTY_ARRAY);

  const [initalFilterState, setInitalFilterState] = useState({
    pipelineStatus,
    pipelineState,
    sources,
    destinations,
  });

  const applyDisabled = useMemo(() => {
    const currentFilterState = {
      pipelineStatus,
      pipelineState,
      sources,
      destinations,
    };

    return isEqual(initalFilterState, currentFilterState);
  }, [destinations, initalFilterState, pipelineState, pipelineStatus, sources]);

  // Set the initial filter state to use the values coming from the URL
  useEffect(() => {
    if (visible) {
      const pipelineState = searchParamValues.pipelineState;
      const pipelineStatus = searchParamValues.pipelineStatus;
      const sources = searchParamValues.sources;
      const destinations = searchParamValues.destinations;

      setPipelineState(pipelineState);
      setPipelineStatus(pipelineStatus);
      setSources(sources);
      setDestinations(destinations);
      setInitalFilterState({
        pipelineState,
        pipelineStatus,
        sources,
        destinations,
      });
    }
  }, [
    searchParamValues.destinations,
    searchParamValues.pipelineState,
    searchParamValues.pipelineStatus,
    searchParamValues.sources,
    visible,
  ]);

  const handleClose = () => {
    const params = [{ name: 'panel', value: '' }];

    updateSearchParams(params);
  };

  const handleApply = () => {
    const params = [
      { name: 'pipelineState', value: filterStateToSearchParam(pipelineState) },
      { name: 'pipelineStatus', value: filterStateToSearchParam(pipelineStatus) },
      { name: 'sources', value: sources.join(',') },
      { name: 'destinations', value: destinations.join(',') },
      { name: 'panel', value: '' },
    ];

    updateSearchParams(params);
  };

  const handlePipelineStatusChange = stateHandlerFactory(setPipelineStatus);
  const handlePipelineStateChange = stateHandlerFactory(setPipelineState);

  const handleSelectSearch = (inputValue: string, option: JSX.Element) => {
    return option.key?.toString().toLowerCase().includes(inputValue.toLowerCase()) ?? false;
  };

  const handleClearFilter = () => {
    setPipelineState(pipelineStateInit);
    setPipelineStatus(pipelineStatusInit);
    setDestinations(EMPTY_ARRAY);
    setSources(EMPTY_ARRAY);
  };

  const footer = (
    <>
      <Button onClick={handleClose}>{tc('cancel')}</Button>
      <Button onClick={handleApply} disabled={applyDisabled} type="primary">
        {tc('apply')}
      </Button>
    </>
  );

  return (
    <DrawerPanel
      className="pipeline-details-filter-panel"
      footer={footer}
      mask
      maskClosable
      maskStyle={{ backgroundColor: 'transparent' }}
      onClose={handleClose}
      title={tn('title')}
      visible={visible}>
      <h1 className="pipeline-details-filter-panel__section-header">{tn('pipeline_status')}</h1>
      <div className="pipeline-details-filter-panel__section">
        <InputWithLabel
          checked={pipelineStatus.draft}
          datatype={AppConstants.INPUT_TYPE.CHECKBOX}
          label={tn('draft')}
          name="draft"
          onChange={handlePipelineStatusChange}
        />
        <InputWithLabel
          checked={pipelineStatus.published}
          datatype={AppConstants.INPUT_TYPE.CHECKBOX}
          label={tn('published')}
          name="published"
          onChange={handlePipelineStatusChange}
        />
        <InputWithLabel
          checked={pipelineStatus.unmapped}
          datatype={AppConstants.INPUT_TYPE.CHECKBOX}
          label={tn('unmapped')}
          name="unmapped"
          onChange={handlePipelineStatusChange}
        />
      </div>

      <h1 className="pipeline-details-filter-panel__section-header">{tn('pipeline_state')}</h1>
      <div className="pipeline-details-filter-panel__section">
        <InputWithLabel
          checked={pipelineState.running}
          datatype={AppConstants.INPUT_TYPE.CHECKBOX}
          label={tn('running')}
          name="running"
          onChange={handlePipelineStateChange}
        />
        <InputWithLabel
          checked={pipelineState.paused}
          datatype={AppConstants.INPUT_TYPE.CHECKBOX}
          label={tn('paused')}
          name="paused"
          onChange={handlePipelineStateChange}
        />
        <InputWithLabel
          checked={pipelineState.resyncing}
          datatype={AppConstants.INPUT_TYPE.CHECKBOX}
          label={tn('resyncing')}
          name="resyncing"
          onChange={handlePipelineStateChange}
        />
        <InputWithLabel
          checked={pipelineState.pausing}
          datatype={AppConstants.INPUT_TYPE.CHECKBOX}
          label={tn('pausing')}
          name="pausing"
          onChange={handlePipelineStateChange}
        />
        <InputWithLabel
          checked={pipelineState.queued}
          datatype={AppConstants.INPUT_TYPE.CHECKBOX}
          label={tn('queued')}
          name="queued"
          onChange={handlePipelineStateChange}
        />
        <InputWithLabel
          checked={pipelineState.error}
          datatype={AppConstants.INPUT_TYPE.CHECKBOX}
          label={tn('error')}
          name="error"
          onChange={handlePipelineStateChange}
        />
        <InputWithLabel
          checked={pipelineState.warning}
          datatype={AppConstants.INPUT_TYPE.CHECKBOX}
          label={tn('warning')}
          name="warning"
          onChange={handlePipelineStateChange}
        />
      </div>

      <h1 className="pipeline-details-filter-panel__section-header">{tn('source')}</h1>
      <div className="pipeline-details-filter-panel__section">
        <Select
          mode="multiple"
          className="pipeline-details-filter-panel__select"
          placeholder={tn('source_placeholder')}
          value={sources}
          onChange={setSources}
          filterOption={handleSelectSearch}>
          {sourceOptions}
        </Select>
      </div>

      <h1 className="pipeline-details-filter-panel__section-header">{tn('destination')}</h1>
      <div className="pipeline-details-filter-panel__section">
        <Select
          mode="multiple"
          className="pipeline-details-filter-panel__select"
          value={destinations}
          placeholder={tn('destination_placeholder')}
          onChange={setDestinations}
          filterOption={handleSelectSearch}>
          {destinationOptions}
        </Select>
      </div>

      <ClearFilterButton onClear={handleClearFilter} />
    </DrawerPanel>
  );
};
