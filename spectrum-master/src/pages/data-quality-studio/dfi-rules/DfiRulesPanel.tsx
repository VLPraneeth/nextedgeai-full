import { Icon, message, Modal } from 'antd';
import produce from 'immer';
import { orderBy } from 'lodash';
import { useEffect, useMemo, useState } from 'react';

import { ReactComponent as TrashIcon } from 'assets/icons/Trash.svg';
import { ListItem, ListItemStatus, ProgressBar } from 'components';
import Button, { IconButton } from 'components/Button';
import Can from 'components/Can';
import DrawerPanel from 'components/DrawerPanel';
import I18nProvider from 'components/I18nProvider';
import BaselinePublishIcon from 'components/icons/BaselinePublishIcon';
import KebabMenu, { MenuItem } from 'components/KebabMenu';
import { HStack, Spacer, Stack } from 'components/layout';
import CenterLayout from 'components/layout/CenterLayout';
import SearchBox from 'components/SearchBox';
import Spinner from 'components/Spinner';
import { Text, TranslatedText } from 'components/typography';
import { useUserInputConfirmationModal } from 'hooks/modal';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { useUserHasPermission } from 'hooks/useUserHasPermission';
import { useDfiRulesForEntity, useSelectDfiRulesRecalculatingProgressForEntity } from 'store/data-quality/hooks';
import { showDfiRuleDetails } from 'store/data-quality/slice';
import { discardDfiRulesDraft, publishDfiRules, saveDfiRule } from 'store/data-quality/thunks';
import { selectEntityById } from 'store/entity/selectors';
import { tNamespaced } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';
import { filterItems } from 'utils/StringUtil';

import './DfiRulesPanel.less';

export interface DfiRulesPanelProps {
  open: boolean;
  selectedEntityId?: string | null;
  close: () => void;
}

const tc = tNamespaced('Common');
const tn = tNamespaced('DataQualityRules');

const ModifiedTag = () => {
  return (
    <div className="synri-dfi-rules-tag-modified">
      <TranslatedText size="xs" lineHeight="snug" text="modified" />
    </div>
  );
};

const DfiRulesPanel = ({ open, selectedEntityId, close }: DfiRulesPanelProps) => {
  const dispatch = useEnhancedDispatch();
  const { userHasPermission } = useUserHasPermission();

  const [filterString, setFilterString] = useState('');

  const { data, loading } = useDfiRulesForEntity(selectedEntityId ?? undefined);
  const entity = useEnhancedSelector((state) => selectEntityById(state, selectedEntityId || ''));

  useEffect(() => {
    // If we have no entity close the panel. This will happen if the user opens
    // the panel and then navigates to the Dashboard overview.
    if (!entity) {
      close();
    }
  }, [close, entity]);

  const showConfirmDeleteModal = useUserInputConfirmationModal();

  const openRuleModal = (ruleId?: string) => dispatch(showDfiRuleDetails({ visible: true, ruleId }));

  const { recalculating, progressPercentage } = useSelectDfiRulesRecalculatingProgressForEntity(entity?.id || '');

  const toggleRuleDisabled = (ruleId: string) => {
    if (data) {
      const updatedDfiRules = produce(data, (draft) => {
        draft.rules.forEach((rule) => {
          if (rule.id === ruleId) {
            rule.disabled = !rule.disabled;
            rule.modified = true;
          }
        });
      });
      dispatch(saveDfiRule(updatedDfiRules));
    }
  };

  const deleteRule = (ruleId: string) => {
    if (data) {
      const updatedDfiRules = produce(data, (draft) => {
        draft.rules = draft.rules.filter((rule) => rule.id !== ruleId);
        draft.deletedRuleIds = [...draft.deletedRuleIds, ruleId];
      });
      dispatch(saveDfiRule(updatedDfiRules)).then(() => {
        message.success(tn('rule_successfully_deleted'));
      });
    }
  };

  const discardDraft = () => {
    if (entity?.id) {
      dispatch(discardDfiRulesDraft(entity.id)).then((response) => {
        if (discardDfiRulesDraft.fulfilled.match(response)) {
          message.success(tn('draft_deleted'));
        } else if (response.payload) {
          message.error(response.payload.errorMessage);
        }
      });
    }
  };

  const confirmDeleteRule = (ruleId: string, ruleName: string) => {
    showConfirmDeleteModal({
      title: tn('delete_rule_title'),
      content: <Text beDangerous>{tn('delete_rule_content', { ruleName })}</Text>,
      onOk: () => deleteRule(ruleId),
      okText: tc('delete'),
      okType: 'danger',
      okButtonProps: { type: 'danger' },
    });
  };

  const confirmDiscardDraft = () => {
    Modal.confirm({
      title: tn('delete_draft_title'),
      content: <Text beDangerous>{tn('delete_draft_content', { entityName: entity?.displayName })}</Text>,
      onOk: discardDraft,
      okText: tc('delete'),
      okType: 'danger',
      okButtonProps: { type: 'danger' },
    });
  };

  // Sort alphabetically with disabled last
  const rules = useMemo(() => orderBy(data?.rules, (rule) => [rule.disabled, rule.name.toLowerCase()]), [data?.rules]);

  const confirmPublish = () => {
    Modal.confirm({
      type: '',
      title: tn('publish_rules'),
      content: (
        <TranslatedText
          namespace="DataQualityRules"
          beDangerous
          text="publish_rules_description"
          args={{ entity: entity?.displayName }}
        />
      ),
      onOk: () => {
        if (data) {
          dispatch(publishDfiRules(data)).then((response) => {
            if (publishDfiRules.fulfilled.match(response)) {
              message.success(tn('rules_published_successfully'));
            } else if (response.payload) {
              message.error(response.payload.errorMessage);
            }
          });
        }
      },
      okText: tn('publish'),
      okType: 'primary',
    });
  };

  const countOfModifiedRules = rules.filter((rule) => rule.modified).length + (data?.deletedRuleIds.length || 0);

  return (
    <I18nProvider namespace="DataQualityRules">
      <DrawerPanel
        className="synri-dfi-rules-drawer-panel"
        title={tn('manage_rules_entity', { entityName: entity?.displayName })}
        onClose={close}
        visible={open}>
        {countOfModifiedRules > 0 && (
          <HStack className="synri-dfi-rules-publish-container">
            <TranslatedText
              namespace="DataQualityRules"
              text="count_rules_modified"
              args={{ count: countOfModifiedRules }}
              weight="semibold"
              color="orange-700"
            />
            <Spacer flex />
            {/* TODO: only disable these elements once the tooltip issues are resolved. */}
            {userHasPermission(AllPermissions.WRITE_DATA_STUDIO) && (
              <>
                <Button onClick={confirmPublish} type="primary">
                  <BaselinePublishIcon /> {tn('publish')}
                </Button>
                <IconButton
                  className="synri-dfi-rule-trash-button"
                  size="small"
                  icon={TrashIcon}
                  onClick={confirmDiscardDraft}
                />
              </>
            )}
          </HStack>
        )}
        <div className="synri-dfi-rules-drawer-body-padding">
          {loading && (
            <CenterLayout>
              <Spinner key="loading-spin" />
            </CenterLayout>
          )}

          {!loading && !rules.length && (
            <CenterLayout>
              <Stack className="synri-quality-rules-empty-center">
                <TranslatedText text="no_rules_exist" />
                <Can permission={AllPermissions.WRITE_DATA_STUDIO}>
                  <Button type="primary" onClick={() => openRuleModal()}>
                    <Icon type="plus" />
                    <TranslatedText text="new_rule" />
                  </Button>
                </Can>
              </Stack>
            </CenterLayout>
          )}

          {recalculating && (
            <Stack className="synri-quality-rules-progress-container" spacing="xs">
              <ProgressBar progress={progressPercentage} />
              <TranslatedText
                color="gray-800"
                weight="semibold"
                text="progress"
                args={{ percentage: progressPercentage }}
              />
            </Stack>
          )}

          {!loading && !!rules.length && (
            <HStack className="synri-quality-rules-filter">
              <SearchBox onChange={(event) => setFilterString(event.target.value)} placeholder={tn('filter_rules')} />
              {/* TODO: only disable these elements once the tooltip issues are resolved. */}
              {userHasPermission(AllPermissions.WRITE_DATA_STUDIO) && (
                <Button onClick={() => openRuleModal()} type="primary" size="large">
                  <Icon type="plus" /> {tn('new')}
                </Button>
              )}
            </HStack>
          )}

          {filterItems(rules, filterString).map((rule) => {
            const editRule = () => openRuleModal(rule.id);

            const fieldCount = tn('field_count', { count: rule.selectedFields.length });
            const conditionsCount = tn('conditions_count', { count: rule.conditions.length });
            const description = rule.disabled ? tn('disabled') : `${fieldCount} · ${conditionsCount}`;

            return (
              <ListItem
                key={rule.id}
                title={rule.name}
                tags={rule.modified && <ModifiedTag />}
                description={description}
                onClick={editRule}
                status={rule.disabled ? ListItemStatus.disabled : undefined}
                rightContent={
                  <KebabMenu
                    menuItems={[
                      <Can permission={AllPermissions.WRITE_DATA_STUDIO}>
                        <MenuItem key="edit" onClick={editRule}>
                          {tc('edit')}
                        </MenuItem>
                      </Can>,
                      <Can permission={AllPermissions.WRITE_DATA_STUDIO}>
                        <MenuItem key="enable" onClick={() => toggleRuleDisabled(rule.id)}>
                          {tc(rule.disabled ? 'enable' : 'disable')}
                        </MenuItem>
                      </Can>,
                      <Can permission={AllPermissions.WRITE_DATA_STUDIO}>
                        <MenuItem key="delete" onClick={() => confirmDeleteRule(rule.id, rule.name)}>
                          {tc('delete')}
                        </MenuItem>
                      </Can>,
                    ]}
                  />
                }
              />
            );
          })}
        </div>
      </DrawerPanel>
    </I18nProvider>
  );
};

export default DfiRulesPanel;
