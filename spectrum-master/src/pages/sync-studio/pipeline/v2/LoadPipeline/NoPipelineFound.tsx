import PipelineIcon from 'assets/icons/pipeline.svg';
import EmptyGraphContent from 'components/EmptyGraphContent';
import InlineSVG from 'components/icons/InlineSvg';
import AppConstants from 'utils/AppConstants';
import { tNamespaced } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';
import { navigateToGraphVersion } from 'utils/PipelineUtil';

import { useUpdateSelectedNodeIdsQueryParam } from '../../PipelineEditor.hooks';
import { PipelineEditorProps } from '../../PipelineEditor.types';

const { GRAPH_STATUS } = AppConstants;

const tn = tNamespaced('PipelineEditor');

export interface NoPipelineFoundProps extends PipelineEditorProps {}

const NoPipelineFound = (props: NoPipelineFoundProps) => {
  const updateSelectedNodeIdsQueryParam = useUpdateSelectedNodeIdsQueryParam();

  const _navigateToGraphVersion = ({
    graphVersion,
    nodeIds,
    replace = true,
  }: {
    graphVersion: string;
    nodeIds?: string[];
    replace?: boolean;
  }) => {
    navigateToGraphVersion({
      ...(props.isFieldPipeline && { fieldId: props.fieldId }),
      entityId: props.entityId,
      graphVersion,
      updateSelectedNodeIdsQueryParam,
      replace,
      nodeIds,
    });
  };

  const onCreateDraftClick = async () => {
    if (props.isFieldPipeline) {
      await props.createDraftFieldPipeline(props.fieldId as string);

      const newGraphVersion =
        props.graphVersion?.toUpperCase() === GRAPH_STATUS.DRAFT ? GRAPH_STATUS.NEW : GRAPH_STATUS.DRAFT;

      _navigateToGraphVersion({ graphVersion: newGraphVersion });
    } else if (!props.pipelineExists) {
      await props.createDraftEntityPipeline(props.entityId);
      props.getEntities();

      _navigateToGraphVersion({ graphVersion: GRAPH_STATUS.DRAFT });

      // If the EP doesn't have a published version, the draft status would be
      // /new before AND AFTER the draft is created. In this case we need to
      // manually remount the component to get the new pipeline.
      props.remountComponent();
    }
  };

  return (
    <EmptyGraphContent
      className="synri-full-width-content"
      icon={<InlineSVG title="Pipeline icon" src={PipelineIcon} />}
      onActionClick={onCreateDraftClick}
      actionText={tn('create_pipeline_draft')}
      actionPermission={AllPermissions.WRITE_STUDIO}>
      <span dangerouslySetInnerHTML={{ __html: tn('create_pipeline_draft_powerful') }} />
    </EmptyGraphContent>
  );
};

export default NoPipelineFound;
