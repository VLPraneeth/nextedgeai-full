import { Tooltip } from 'antd';
import MenuItem from 'antd/lib/menu/MenuItem';
import { capitalize } from 'lodash';

import { useI18nContext, withI18n } from 'components/I18nProvider';
import KebabMenu from 'components/KebabMenu';
import { TextTag } from 'components/text-tag';
import Text from 'components/typography/Text';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { useUpdateSelectedNodeIdsQueryParam } from 'pages/sync-studio/pipeline/PipelineEditor.hooks';
import { useSyncStudioMatch } from 'pages/sync-studio/SyncStudio.hooks';
import { setIsGotoBetweenFieldPipelines, setWarnings, showValidationResultsPanel } from 'store/validation/slice';
import { ValidationResult, ValidationResultType } from 'store/validation/types';
import { getCoreNode, getNodeName } from 'store/validation/utils';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';
import { useGetRulesListQuery } from 'store/data-quality-v2/api';
import './ValidationResultsItem.less';

export interface ValidationResultsItemProps {
  entityId?: string;
  entityPipelineId?: string;
  result: ValidationResult;
  subtitle?: string;
}

export const ValidationResultsItem = withI18n(
  ({ entityId, entityPipelineId, result, subtitle }: ValidationResultsItemProps) => {
    const { tn } = useI18nContext();
    const dispatch = useEnhancedDispatch();
    const { entityPipeline } = useEnhancedSelector((state) => state.entityPipeline);
    const entityPipelineDraft = entityPipeline?.draft ?? null;
    const { warnings } = useEnhancedSelector((state) => state.validation);

    const { data: rules, isFetching } = useGetRulesListQuery(
      { syncariEntityId: entityId || '', version: 'NEW' },
      { skip: !entityId }
    );
    const updateSelectedNodeIdsQueryParam = useUpdateSelectedNodeIdsQueryParam();

    // For some errors, the backend returns a nodeId equal to the entityPipelineId
    // and a targetId equal to the entityId. The fuction below determines if this
    // is the case.
    const isCoreError = () => {
      if (entityId && entityPipelineId && result.nodeId && result.targetId) {
        if (result.targetId === entityId && result.nodeId === entityPipelineId) {
          return true;
        }
        return false;
      }

      return false;
    };

    const gotoCoreNode = isCoreError();

    // For some errors, the backend returns a nodeId equal to the entityPipelineId
    // and a targetId equal to the entityId. If this is the case, then the error scope
    // will need to be determined by looking for the name of the core entity node.
    const getScope = () => {
      if (!subtitle) {
        if (gotoCoreNode && entityPipeline) {
          const node = getCoreNode(entityPipeline.nodes);
          return node ? getNodeName(node.id, entityPipeline.nodes) : '';
        }

        return '';
      }

      return subtitle;
    };

    const scope = getScope();

    const handleDismiss = () => {
      let mutableWarnings = [...warnings];
      const index = mutableWarnings.findIndex(
        (error) =>
          error.nodeId === result.nodeId && error.targetId === result.targetId && error.message === result.message
      );
      mutableWarnings.splice(index, 1);
      dispatch(setWarnings(mutableWarnings));
    };

    const match = useSyncStudioMatch();

    const isGotoEnabled = !!result.nodeId && !!result.targetId;

    const handleGoto = () => {
      dispatch(showValidationResultsPanel(false));
      if (match) {
        const { entityId, graphVersion, fieldId } = match;
        const coreNode = getCoreNode(entityPipelineDraft ? entityPipelineDraft.nodes : entityPipeline?.nodes);
        const nodeId = gotoCoreNode && coreNode ? coreNode.id : result.nodeId;

        // Check if this is a DFI rule error
        const dfiRuleMatch = result.message.match(/DFI rule (\w+) has/);
        if (dfiRuleMatch && rules) {
          const ruleName = dfiRuleMatch[1];
          const rule = rules.find((r) => r.name === ruleName);
          if (rule) {
            const url = makeUrl(RouteConstants.DATA_QUALITY_RULE, {
              entityId,
              graphVersion: 'draft',
              ruleId: rule.id,
            });

            setTimeout(() => {
              updateSelectedNodeIdsQueryParam([nodeId], url, nodeId);
            });
            return;
          }
        }

        if (fieldId && result.targetId !== fieldId) {
          dispatch(setIsGotoBetweenFieldPipelines(true));
        }

        const url = makeUrl(
          result.level === 'ATTRIBUTE'
            ? RouteConstants.FIELD_PIPELINE_VALIDATION
            : RouteConstants.ENTITY_PIPELINE_VALIDATION,
          {
            entityId,
            graphVersion,
            fieldId: result.targetId,
          }
        );

        // For some reason if you try to navigate to a field pipeline this
        // will immediately redirect back to the entity page unless this is
        // wrapped in a setTimeout
        setTimeout(() => {
          updateSelectedNodeIdsQueryParam([nodeId], url, nodeId);
        });
      }
    };

    const menuItems = [
      <MenuItem key="goto" onClick={handleGoto} disabled={!isGotoEnabled}>
        {tn('goto')}
      </MenuItem>,
      result.type === ValidationResultType.WARNING ? (
        <MenuItem key="dismiss" onClick={handleDismiss}>
          {tn('dismiss')}
        </MenuItem>
      ) : null,
    ];

    return (
      <div className="validation-result-container">
        <div className="validation-result-header">
          <Tooltip title={<span dangerouslySetInnerHTML={{ __html: result.message }} />}>
            {/* BE will sanitze the error message so we're displaying it raw */}
            <Text className="validation-result-title" beDangerous size="md" weight="semibold" color="black">
              {result.message}
            </Text>
          </Tooltip>
          <KebabMenu menuItems={menuItems} />
        </div>
        <h2 className="validation-result-subtitle">{scope}</h2>
        <div>
          <TextTag
            text={capitalize(result.type)}
            color={result.type === ValidationResultType.ERROR ? 'red' : 'orange'}
            size="md"
          />
        </div>
      </div>
    );
  },
  'ValidationResultsItem'
);
