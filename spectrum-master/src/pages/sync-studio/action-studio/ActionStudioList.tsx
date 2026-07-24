//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { Icon, Dropdown } from 'antd';
import Menu from 'antd/lib/menu';
import { sortBy } from 'lodash';
import { useCallback, useEffect, useMemo, useState } from 'react';

import { ListItem, TextTag } from 'components';
import Button from 'components/Button';
import EmptyGraphPanel from 'components/EmptyGraphPanel';
import { useI18nContext, withI18n } from 'components/I18nProvider';
import { default as SIcon } from 'components/icons/Icon';
import KebabMenu from 'components/KebabMenu';
import { HStack, Stack } from 'components/layout';
import Modal from 'components/Modal';
import SearchBox from 'components/SearchBox';
import TabPanelSpin from 'components/TabPanelSpin';
import { TextTagProps } from 'components/text-tag/TextTag';
import { TranslatedText } from 'components/typography';
import { useEnhancedDispatch as useDispatch } from 'hooks/redux';
import useDimensions from 'hooks/useDimensions';
import { useUserHasPermission } from 'hooks/useUserHasPermission';
import {
  useCreateCustomActionDraftMutation,
  useGetCustomActionListQuery,
  useLazyGetCustomActionDraftQuery,
  useDiscardCustomActionDraftMutation,
} from 'store/custom-action/api';
import { showCustomActionWizard, showCustomActionShare } from 'store/custom-action/slice';
import { CustomActionPayload } from 'store/custom-action/types';
import { nextEdgeHelpUrl } from 'utils/Branding';
import { AllPermissions } from 'utils/PermissionsConstants';
import { filterItems } from 'utils/StringUtil';

import { useActionStudio } from './ActionStudio.hook';

import './ActionStudioList.less';

const statusColorMap: Record<string, TextTagProps['color']> = {
  published: 'green',
  draft: 'orange',
  shared: 'blue',
  global: 'purple',
};

const FILTER_RESERVED_HEIGHT = 24;
export const DEFAULT_ACTION_ICON = '/assets/icons/actions/custom-action-default.svg';

const statusesForApprovedAction = ['approved', 'published', 'published_with_draft'];
const statusesForDraftAction = ['new', 'published_with_draft'];

const CUSTOM_ACTION_SUPPORT_PAGE = nextEdgeHelpUrl('creating-custom-actions');

export interface ActionStudioListProps {
  showCustomAction: (visible: boolean, action?: CustomActionPayload) => void;
}

const ActionStudioList = ({ showCustomAction }: ActionStudioListProps) => {
  const { tn, tc } = useI18nContext();
  const [measurementRef, dimensions] = useDimensions({ liveMeasure: true });
  const [filterString, setFilterString] = useState('');
  const { data: customActions } = useGetCustomActionListQuery();
  const [discardCustomActionDraft] = useDiscardCustomActionDraftMutation();

  const { deleteAction, publishAction } = useActionStudio();
  const { userHasPermission } = useUserHasPermission();

  const isLoading = false;
  const dispatch = useDispatch();

  const [createCustomActionDraft] = useCreateCustomActionDraftMutation();

  const [customActionId, setCustomActionId] = useState<null | string>(null);
  const [fetchCustomActionDraft] = useLazyGetCustomActionDraftQuery({
    selectFromResult: (result) => {
      if (result.isSuccess && !result.isFetching && customActionId) {
        showCustomAction(true, { ...result.data, id: customActionId });
        setCustomActionId(null);
      }
    },
  });

  useEffect(() => {
    return () => {
      dispatch(showCustomActionWizard({ visible: false }));
      dispatch(showCustomActionShare({ visible: false }));
    };
  }, [dispatch]);

  const onEdit = useCallback(
    (customActionId: string) => {
      fetchCustomActionDraft({ customActionId, action: 'editDraft' });
      setCustomActionId(customActionId);
    },
    [fetchCustomActionDraft]
  );

  const filterOrEmptyState = useMemo(() => {
    if (isLoading) {
      return null;
    }

    const menu = (
      <Menu>
        {userHasPermission(AllPermissions.ACTION_WRITE) && (
          <Menu.Item key="create_new" onClick={() => dispatch(showCustomActionWizard({ visible: true }))}>
            {tn('new_action')}
          </Menu.Item>
        )}
        <Menu.Item key="help" onClick={() => window.open(CUSTOM_ACTION_SUPPORT_PAGE, '_blank')}>
          {tc('help')}
        </Menu.Item>
      </Menu>
    );

    if (customActions?.length) {
      return (
        <HStack className="synri-quality-rules-filter">
          <SearchBox
            onChange={(event) => setFilterString(event.target.value)}
            placeholder={tc('filter')}
            className="synri-quick-start-filter"
          />
          <Dropdown overlay={menu} trigger={['click']}>
            <Button>
              {tc('actions')} <Icon type="down" />
            </Button>
          </Dropdown>
        </HStack>
      );
    }

    return (
      <EmptyGraphPanel
        onActionClick={() => dispatch(showCustomActionWizard({ visible: true }))}
        actionTooltipPlacement="bottom"
        actionText={
          <span className="synri-quick-start-panel-empty-action-text">
            <Icon type="plus" />
            {tn('new_action')}
          </span>
        }>
        <TranslatedText text="empty_authoring_message" beDangerous />
      </EmptyGraphPanel>
    );
  }, [customActions?.length, dispatch, isLoading, tc, tn, userHasPermission]);

  const confirmDeleteCustomAction = useCallback(
    (actionId: string, customActionName: string) => {
      Modal.confirm({
        title: tn('delete_title'),
        content: (
          <TranslatedText
            namespace="CustomAction"
            text="delete_custom_action_content"
            beDangerous
            args={{ name: customActionName }}
          />
        ),
        onOk: () => deleteAction(actionId),
        okText: tc('delete'),
        okType: 'danger',
        okButtonProps: { type: 'danger' },
      });
    },
    [deleteAction, tc, tn]
  );

  const actionStudioList = useMemo(() => {
    if (!customActions) {
      return null;
    }
    const filteredCustomActions = filterItems(customActions, filterString) as any;

    return sortBy(filteredCustomActions, ['installStatus', 'displayName']).map((customAction) => {
      const isPublished = statusesForApprovedAction.includes((customAction as any).status?.toLowerCase());
      const hasDraft = statusesForDraftAction.includes((customAction as any).status?.toLowerCase());
      const isShared = !!customAction.shareWithOrg;
      const isSharedGlobally = !!customAction.shareGlobally;

      const deleteCustomAction = userHasPermission(AllPermissions.ACTION_WRITE) ? (
        <Menu.Item
          key="delete"
          onClick={() => {
            confirmDeleteCustomAction(customAction.id, customAction.displayName);
          }}>
          <TranslatedText namespace="Common" text="delete" />
        </Menu.Item>
      ) : null;

      const publish = userHasPermission(AllPermissions.ACTION_WRITE) ? (
        <Menu.Item
          key="publish"
          onClick={() => {
            publishAction(customAction.id);
          }}>
          <TranslatedText namespace="Common" text="publish" />
        </Menu.Item>
      ) : null;

      const share = userHasPermission(AllPermissions.ACTION_SHARE) ? (
        <Menu.Item
          key="share"
          onClick={() => {
            dispatch(showCustomActionShare({ visible: true, customActionId: customAction.id }));
          }}>
          <TranslatedText namespace="Common" text="share" />
        </Menu.Item>
      ) : null;

      const editDraft = userHasPermission(AllPermissions.ACTION_WRITE) ? (
        <Menu.Item
          key="edit"
          onClick={() => {
            onEdit(customAction.id);
          }}>
          <TranslatedText text={'edit_draft'} />
        </Menu.Item>
      ) : null;

      const discardDraft = userHasPermission(AllPermissions.ACTION_WRITE) ? (
        <Menu.Item key="discard_draft" onClick={() => discardCustomActionDraft({ customActionId: customAction.id })}>
          <TranslatedText namespace="Common" text="delete_draft" />
        </Menu.Item>
      ) : null;

      const editCustomAction = userHasPermission(AllPermissions.ACTION_WRITE) ? (
        <Menu.Item
          key="edit_custom_action"
          onClick={() =>
            createCustomActionDraft({ customActionId: customAction.id })
              .unwrap()
              .then((draftCustomAction) => onEdit(draftCustomAction.id as string))
          }>
          <TranslatedText namespace="CustomAction" text="edit_custom_action" />
        </Menu.Item>
      ) : null;

      let menuItems: (JSX.Element | null)[];

      if (isPublished) {
        menuItems = hasDraft
          ? [editDraft, discardDraft, deleteCustomAction, publish]
          : [editCustomAction, share, deleteCustomAction];
      } else {
        menuItems = [editDraft, deleteCustomAction, publish];
      }

      return (
        <ListItem
          key={customAction.id}
          title={customAction.displayName}
          titleTooltip={customAction.displayName}
          icon={<SIcon className="synri-quick-start-icon" src={customAction.iconPath} alt={customAction.displayName} />}
          tags={[
            hasDraft && <TextTag key="draft" color={statusColorMap.draft} text={tn('draft')} />,
            isPublished && <TextTag key="published" color={statusColorMap.published} text={tn('published')} />,
            isShared && <TextTag key="shared" color={statusColorMap.shared} text={tn('shared')} />,
            isSharedGlobally && <TextTag key="global" color={statusColorMap.global} text={tn('global')} />,
          ]}
          rightContent={<KebabMenu menuItems={menuItems} />}
        />
      );
    });
  }, [
    customActions,
    filterString,
    userHasPermission,
    tn,
    confirmDeleteCustomAction,
    publishAction,
    dispatch,
    onEdit,
    discardCustomActionDraft,
    createCustomActionDraft,
  ]);

  return (
    <Stack className="synri-action-studio-list">
      <TabPanelSpin spinning={isLoading} tip={tn('loading_actions')}>
        <div ref={measurementRef} />
        <Stack spacing="md">
          {filterOrEmptyState}
          <Stack
            className="synri-qs-item-list"
            style={{ maxHeight: `calc(100vh - ${dimensions.bottom + FILTER_RESERVED_HEIGHT}px)` }}>
            {actionStudioList}
          </Stack>
        </Stack>
      </TabPanelSpin>
    </Stack>
  );
};

export default withI18n(ActionStudioList, 'ActionStudio');
