import { Link, RouteComponentProps } from '@reach/router';
import Icon from 'antd/lib/icon';
import { isEmpty, orderBy } from 'lodash';
import { useEffect, useMemo, useState } from 'react';

import { ReactComponent as ChevronRight } from 'assets/icons/chevron-right.svg';
import { ReactComponent as RestoreIcon } from 'assets/icons/restore-version.svg';
import { NoRowsOverlay } from 'components/AgTable';
import Button from 'components/Button';
import { withI18n } from 'components/I18nProvider';
import Select, { Option } from 'components/inputs/Select';
import { HStack, Stack } from 'components/layout';
import Spinner from 'components/Spinner';
import TextTag, { TextTagColorOptions } from 'components/text-tag/TextTag';
import { Text, TranslatedText } from 'components/typography';
import useUserLocalMoment from 'hooks/moment';
import { useEnhancedDispatch } from 'hooks/redux';
import {
  useGetPipelinesDiffQuery,
  useGetPipelinesForCompareQuery,
  useGetPipelineVersionListQuery,
} from 'store/pipeline/api';
import { showRestoreVersionModal } from 'store/pipeline/slice';
import { PipelineChangeTypes, PipelineVersion } from 'store/pipeline/types';
import { SHORT_DATE_TIME_TZ_DISPLAY_FORMAT } from 'utils/DateUtil';
import { tNamespaced } from 'utils/i18nUtil';
import { wrapIcon } from 'utils/IconUtils';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

import VersionDetailDiffItem from './VersionDetailDiffItem';

import './PipelineVersions.scss';

const tn = tNamespaced('PipelineVersions');

const changeColorMap: Record<PipelineChangeTypes, TextTagColorOptions> = {
  Modified: 'blue',
  Unchanged: 'gray',
  Deleted: 'red',
  Created: 'green',
};

export interface VersionDetailPageProps extends RouteComponentProps {
  path: string;
  entityId: string;
  versionOneId?: string;
  versionTwoId?: string;
}

const VersionDetailPage = ({ entityId, versionOneId, versionTwoId }: VersionDetailPageProps) => {
  const moment = useUserLocalMoment();
  const dispatch = useEnhancedDispatch();

  const [selectedPipelineId, setSelectedPipelineId] = useState('');

  const { data: versions } = useGetPipelineVersionListQuery(entityId);
  const versionOne = versions?.find((v) => v.versionId === versionOneId);
  const versionTwo = versions?.find((v) => v.versionId === versionTwoId);

  const { data } = useGetPipelinesForCompareQuery({
    syncariEntityId: entityId,
    versionOneId: versionOneId!,
    versionTwoId,
  });

  const entityPipeline = data?.find((pipeline) => pipeline.pipelineType === 'ENTITY');

  const { data: diffs, isFetching } = useGetPipelinesDiffQuery(
    {
      pipelineType: entityPipeline?.targetId === selectedPipelineId ? 'entityPipeline' : 'fieldPipeline',
      pipelineId: selectedPipelineId,
      versionOneId: versionOneId!,
      versionTwoId: versionTwoId!,
    },
    { skip: !selectedPipelineId }
  );

  useEffect(() => {
    if (entityPipeline) {
      setSelectedPipelineId(entityPipeline.targetId);
    }
  }, [entityPipeline]);

  const pipelines = useMemo(() => {
    return orderBy(data, 'pipelineType', 'desc');
  }, [data]);

  const selectOptions: JSX.Element[] = [];

  pipelines?.forEach((pipeline, index) => {
    if (index === 0) {
      const key = tn('entity_pipeline');

      selectOptions.push(
        <Option
          className="ant-select-dropdown-menu-item-group-title option-group"
          title={key}
          key={key}
          value={key}
          disabled>
          {key}
        </Option>
      );
    }
    if (index === 1) {
      const key = tn('field_pipelines_count', { count: pipelines.length - 1 });

      selectOptions.push(
        <Option
          className="ant-select-dropdown-menu-item-group-title option-group"
          title={key}
          key={key}
          value={key}
          disabled>
          {key}
        </Option>
      );
    }

    selectOptions.push(
      <Option
        key={pipeline.targetId}
        value={pipeline.targetId}
        title={pipeline.displayName}
        className="compare-versions-pipeline-picker__option">
        <Text>{pipeline.displayName}</Text>
        <TextTag text={pipeline.changeType} color={changeColorMap[pipeline.changeType]} />
      </Option>
    );
  });

  const versionsMap = [versionOne, versionTwo].filter(Boolean) as PipelineVersion[];

  return (
    <div className="compare-versions-container">
      <Link
        className="compare-versions-container__back_link"
        to={makeUrl(RouteConstants.ENTITY_VERSIONS, { entityId })}>
        <Icon
          style={{ fontSize: 24, transform: 'rotate(180deg)', marginRight: 4 }}
          component={wrapIcon(ChevronRight)}
        />
        <Text>{tn('back_to_versions_table')}</Text>
      </Link>
      <Stack className="compare-versions">
        <div className="compare-versions-summary">
          <TranslatedText text="comparing" color="gray-800" weight="semibold" />
          <table>
            <tbody>
              {versionsMap.map((version) => {
                return (
                  <tr key={version.versionId}>
                    <td>
                      <TranslatedText text="version_colon" color="gray-800" weight="semibold" />
                      {version.versionNumber && <Text color="gray-800">{version.versionNumber.toString()}</Text>}
                    </td>
                    <td>
                      <TranslatedText text="submitted_date" color="gray-800" weight="semibold" />
                      <Text color="gray-800">
                        {moment(version.createdAt).format(SHORT_DATE_TIME_TZ_DISPLAY_FORMAT)}
                      </Text>
                    </td>
                    <td>
                      <TranslatedText text="submitted_by" color="gray-800" weight="semibold" />
                      {version.createdBy && <Text color="gray-800">{version.createdBy}</Text>}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>

        <HStack className="compare-versions__select-pipeline">
          <div className="compare-versions__select-pipeline--left-content">
            <TranslatedText size="lg" text="choose_a_pipeline" />

            <Select
              className="compare-versions-pipeline-picker"
              value={selectedPipelineId}
              onChange={(value: string) => {
                setSelectedPipelineId(value);
              }}
              options={selectOptions}
              filterOption={(input, option) => {
                if (option.props.title) {
                  return option.props.title.toLowerCase().indexOf(input) >= 0;
                }
                return false;
              }}
            />

            {(isFetching || !selectedPipelineId) && <Spinner />}
          </div>

          <Button
            key="cancel"
            onClick={() => {
              dispatch(
                showRestoreVersionModal({
                  visible: true,
                  versionId: versionOneId,
                  name: versionOne?.name,
                  versionTwoId,
                  versionOneNumber: versionOne?.versionNumber,
                  versionTwoNumber: versionTwo?.versionNumber,
                })
              );
            }}
            type="primary">
            <Icon style={{ fontSize: 18, marginTop: 2 }} component={wrapIcon(RestoreIcon)} />
            {tn('restore_draft')}
          </Button>
        </HStack>

        {diffs?.map((diff, index) => (
          <VersionDetailDiffItem key={`${index}_${selectedPipelineId}`} diff={diff} />
        ))}

        {isEmpty(diffs) && Boolean(selectedPipelineId) && !isFetching && (
          <div className="compare-versions__empty-content">
            <NoRowsOverlay description={tn('no_difference_found')} />
          </div>
        )}
      </Stack>
    </div>
  );
};

export default withI18n(VersionDetailPage, 'PipelineVersions');
