import { CustomSynapse } from 'components/custom-synapse/types';
import { TextTag } from 'components/text-tag';
import { TextTagProps } from 'components/text-tag/TextTag';
import { CustomSynapseDraftStatuses } from 'store/custom-synapse/types';
import { tNamespaced } from 'utils/i18nUtil';

interface CustomSynapseStatusTagsProps {
  customSynapse: CustomSynapse | undefined;
}

const statusColorMap: Record<string, TextTagProps['color']> = {
  draft: 'orange',
  pendingApproval: 'gray',
  approvalInProgress: 'green',
  published: 'blue',
};

const tn = tNamespaced('CustomSynapse');

export function CustomSynapseStatusTags({ customSynapse }: CustomSynapseStatusTagsProps) {
  const isPublished = customSynapse?.draftStatus === CustomSynapseDraftStatuses.APPROVED || !!customSynapse?.parentId;
  const isDraft = customSynapse?.draftStatus === CustomSynapseDraftStatuses.NEW;
  const isPendingApproval = customSynapse?.draftStatus === CustomSynapseDraftStatuses.SUBMIT_FOR_APPROVAL;
  const approvalInProgress = customSynapse?.draftStatus === CustomSynapseDraftStatuses.APPROVAL_IN_PROGRESS;

  const showPublishedTag = isPublished || ((isDraft || isPendingApproval) && !!customSynapse.parentId);
  const tags = [
    isPendingApproval && (
      <TextTag key="submit_for_review" color={statusColorMap.pendingApproval} text={tn('in_review')} />
    ),
    approvalInProgress && (
      <TextTag key="approval_in_process" color={statusColorMap.approvalInProgress} text={tn('approval_in_progress')} />
    ),
    showPublishedTag && <TextTag key="published" color={statusColorMap.published} text={tn('published')} />,
    isDraft && <TextTag key="draft" color={statusColorMap.draft} text={tn('draft')} />,
  ];

  return <div>{tags}</div>;
}
