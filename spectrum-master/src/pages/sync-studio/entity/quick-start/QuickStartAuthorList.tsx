//
// Copyright (c) 2019-Present Syncari All rights reserved.
//

import { Icon, message, Tooltip } from 'antd';
import Menu from 'antd/lib/menu';
import { sortBy } from 'lodash';
import moment from 'moment';
import { useCallback, useMemo, useState } from 'react';

import { ListItem, TextTag } from 'components';
import Button from 'components/Button';
import EmptyGraphPanel from 'components/EmptyGraphPanel';
import { useI18nContext } from 'components/I18nProvider';
import { default as SIcon } from 'components/icons/Icon';
import KebabMenu from 'components/KebabMenu';
import { HStack, Stack } from 'components/layout';
import Modal from 'components/Modal';
import SearchBox from 'components/SearchBox';
import TabPanelSpin from 'components/TabPanelSpin';
import { TextTagProps } from 'components/text-tag/TextTag';
import { TranslatedText } from 'components/typography';
import { useEnhancedDispatch as useDispatch, useEnhancedSelector as useSelector } from 'hooks/redux';
import useDimensions from 'hooks/useDimensions';
import { useUserHasPermission } from 'hooks/useUserHasPermission';
import { selectPublishedEntities } from 'store/entity/selectors';
import { showQuickStartPublish } from 'store/entity/thunks';
import {
  useCreateQuickStartDraftMutation,
  useDeleteQuickStartMutation,
  useDiscardQuickStartDraftMutation,
  useGetQuickStartAuthorListQuery,
  useLazyGetQuickStartDraftQuery,
} from 'store/quick-start/api';
import { QuickStart } from 'store/quick-start/types';
import DataUrlConstants from 'utils/DataUrlConstants';
import { SHORT_DATE_DISPLAY_FORMAT } from 'utils/DateUtil';
import { AllPermissions } from 'utils/PermissionsConstants';
import { copyStringToClipboard, filterItems } from 'utils/StringUtil';
import { makeUrl } from 'utils/UrlUtil';

const statusColorMap: Record<string, TextTagProps['color']> = {
  library: 'green',
  shared: 'blue',
  draft: 'orange',
};

const statusesForApprovedAction = ['approved', 'published_with_draft'];
const statusesForDraftAction = ['new', 'published_with_draft'];

export interface QuickStartAuthorListProps {
  setQuickStartVisible: (quickStart: QuickStart | null) => void;
  setQuickStartInstallVisible: (quickStart: QuickStart) => void;
}

const QuickStartAuthorList = ({ setQuickStartVisible, setQuickStartInstallVisible }: QuickStartAuthorListProps) => {
  const { isLoading, data: authorQuickStarts } = useGetQuickStartAuthorListQuery();

  const publishedEnties = useSelector(selectPublishedEntities);
  const disableCreateQS = !Boolean(publishedEnties?.length);

  const [filterString, setFilterString] = useState('');
  const [deleteQuickStart] = useDeleteQuickStartMutation();
  const [createQuickStartDraft] = useCreateQuickStartDraftMutation();
  const [discardQuickStartDraft] = useDiscardQuickStartDraftMutation();
  const [quickStartId, setQuickStartId] = useState<string | null>(null);
  const dispatch = useDispatch();
  const { userHasPermission } = useUserHasPermission();

  const { tc, tn } = useI18nContext();

  const [measurementRef, dimensions] = useDimensions({ liveMeasure: true });

  const [fetchQuickStartDraft] = useLazyGetQuickStartDraftQuery({
    selectFromResult: (result) => {
      if (result.isSuccess && quickStartId) {
        setQuickStartVisible({
          ...result.data,
          dataFormatType: 'form',
        });
        setQuickStartId(null);
      }
    },
  });

  const onEdit = useCallback(
    (quickStartId: string) => {
      fetchQuickStartDraft({ quickStartId, action: 'editDraft' });
      setQuickStartId(quickStartId);
    },
    [fetchQuickStartDraft]
  );

  const confirmDeleteQuickStart = useCallback(
    (quickStartId: string, quickStartName: string) => {
      Modal.confirm({
        title: tn('delete_qs_title'),
        content: (
          <TranslatedText namespace="QuickStart" text="delete_qs_content" beDangerous args={{ name: quickStartName }} />
        ),
        onOk: () => deleteQuickStart({ quickStartId }),
        okText: tc('delete'),
        okType: 'danger',
        okButtonProps: { type: 'danger' },
      });
    },
    [deleteQuickStart, tc, tn]
  );

  const quickStartsList = useMemo(() => {
    if (!authorQuickStarts) {
      return null;
    }
    const filteredQuickStarts = filterItems(authorQuickStarts, filterString);

    return sortBy(filteredQuickStarts, ['installStatus', 'displayName']).map((quickStart) => {
      const isPublished = statusesForApprovedAction.includes((quickStart as any).status.toLowerCase());
      const hasDraft = statusesForDraftAction.includes((quickStart as any).status.toLowerCase());

      const deleteQS = (
        <Menu.Item key="delete" onClick={() => confirmDeleteQuickStart(quickStart.id, quickStart.displayName)}>
          <TranslatedText namespace="Common" text="delete" />
        </Menu.Item>
      );

      const editDraft = (
        <Menu.Item key="edit" onClick={() => onEdit(quickStart.id)}>
          <TranslatedText namespace="QuickStart" text={'edit_draft'} />
        </Menu.Item>
      );

      const discardDraft = (
        <Menu.Item key="discard_draft" onClick={() => discardQuickStartDraft({ quickStartId: quickStart.id })}>
          <TranslatedText namespace="Common" text="delete_draft" />
        </Menu.Item>
      );

      const publishSettings = userHasPermission([
        AllPermissions.QUICKSTART_SHARE,
        AllPermissions.QUICKSTART_ORG_SHARE,
      ]) ? (
        <Menu.Item key="publish" onClick={() => dispatch(showQuickStartPublish(true, quickStart.id))}>
          <TranslatedText namespace="QuickStart" text="publish_settings" />
        </Menu.Item>
      ) : null;

      // We'll add this to the dropdown list once we support canceling a run
      // eslint-disable-next-line @typescript-eslint/no-unused-vars
      const runQS = (
        <Menu.Item key="run" onClick={() => setQuickStartInstallVisible(quickStart)}>
          <TranslatedText namespace="QuickStart" text="run_quick_start" />
        </Menu.Item>
      );

      const editQuickStart = (
        <Menu.Item
          key="edit_quick_start"
          onClick={() => {
            createQuickStartDraft({ quickStartId: quickStart.id })
              .unwrap()
              .then(() => onEdit(quickStart.id));
          }}>
          <TranslatedText namespace="QuickStart" text="edit_quick_start" />
        </Menu.Item>
      );

      const copyPublishedId = (
        <Menu.Item
          key="copy_quick_start_id"
          disabled={!quickStart.publishedQuickStartId}
          onClick={() => {
            copyStringToClipboard(quickStart.publishedQuickStartId!);
            message.success(tn('id_copied_to_clipboard'));
          }}>
          <Tooltip trigger="hover" title={quickStart.publishedQuickStartId ? undefined : tn('publish_to_copy_id')}>
            <TranslatedText namespace="QuickStart" text="copy_quick_start_id" />
          </Tooltip>
        </Menu.Item>
      );

      let menuItems: (JSX.Element | null)[];

      if (isPublished) {
        menuItems = hasDraft
          ? [editDraft, discardDraft, publishSettings, copyPublishedId, deleteQS]
          : [editQuickStart, publishSettings, copyPublishedId, deleteQS];
      } else {
        menuItems = [editDraft, publishSettings, copyPublishedId, deleteQS];
      }

      menuItems = menuItems?.filter(Boolean);

      const publishedDate = moment(quickStart.lastPublishedAt).format(SHORT_DATE_DISPLAY_FORMAT);
      const isShared = !!(quickStart.shareWithInstances?.length || quickStart.shareWithOrg);
      const sharedTooltip =
        quickStart.shareWithInstances?.length && quickStart.shareWithOrg
          ? tn('shared_with_org_and_instance', { count: quickStart.shareWithInstances.length })
          : quickStart.shareWithOrg
          ? tn('shared_with_org')
          : tn('shared_with_instances', {
              count: quickStart.shareWithInstances?.length,
            });

      return (
        <ListItem
          key={quickStart.id}
          title={quickStart.displayName}
          titleTooltip={quickStart.displayName}
          icon={
            <SIcon
              className="synri-quick-start-icon"
              src={makeUrl(DataUrlConstants.QUICK_START_QUICK_START_ICON, {
                quickStartId: quickStart.id,
                status: quickStart.status,
              })}
              alt={quickStart.displayName}
            />
          }
          tags={[
            quickStart.publishToQuickStartLibrary === 'publish' && (
              <TextTag
                key="library"
                color={statusColorMap.library}
                text={tn('library')}
                tooltipText={
                  quickStart.lastPublishedAt && (
                    <TranslatedText beDangerous text="published_date" args={{ publishedDate }} />
                  )
                }
              />
            ),
            isShared && (
              <TextTag key="approved" color={statusColorMap.shared} text={tn('shared')} tooltipText={sharedTooltip} />
            ),
            hasDraft && <TextTag key="draft" color={statusColorMap.draft} text={tn('draft')} />,
          ]}
          rightContent={<KebabMenu menuItems={menuItems} />}
        />
      );
    });
  }, [
    authorQuickStarts,
    confirmDeleteQuickStart,
    createQuickStartDraft,
    discardQuickStartDraft,
    dispatch,
    filterString,
    onEdit,
    setQuickStartInstallVisible,
    tn,
    userHasPermission,
  ]);

  const hasQuickStarts = Boolean(authorQuickStarts?.length);

  const filterOrEmptyState = useMemo(() => {
    if (isLoading) {
      return null;
    }

    if (hasQuickStarts) {
      return (
        <HStack className="synri-quality-rules-filter">
          <SearchBox
            onChange={(event) => setFilterString(event.target.value)}
            placeholder={tc('filter')}
            className="synri-quick-start-filter"
          />
          <Tooltip title={disableCreateQS && tn('publish_pipeline_to_create_qs')} placement="left">
            <div>
              <Button
                disabled={disableCreateQS}
                onClick={() => setQuickStartVisible(null)}
                type="primary"
                size="default">
                <Icon type="plus" />
                <TranslatedText namespace="Common" text="new" />
              </Button>
            </div>
          </Tooltip>
        </HStack>
      );
    }

    return (
      <EmptyGraphPanel
        onActionClick={() => setQuickStartVisible(null)}
        actionDisabled={disableCreateQS}
        actionTooltip={disableCreateQS ? tn('publish_pipeline_to_create_qs') : ''}
        actionTooltipPlacement="bottom"
        actionText={
          <span className="synri-quick-start-panel-empty-action-text">
            <Icon type="plus" />
            {tn('new_quick_start')}
          </span>
        }>
        <TranslatedText text="empty_authoring_message" beDangerous />
      </EmptyGraphPanel>
    );
  }, [disableCreateQS, hasQuickStarts, isLoading, setQuickStartVisible, tc, tn]);

  return (
    <Stack>
      {filterOrEmptyState}
      <TabPanelSpin spinning={isLoading} tip={tn('loading_quick_starts')}>
        <div ref={measurementRef} />
        <Stack className="synri-qs-item-list" style={{ maxHeight: `calc(100vh - ${dimensions.bottom}px)` }}>
          {quickStartsList}
        </Stack>
      </TabPanelSpin>
    </Stack>
  );
};

export default QuickStartAuthorList;
