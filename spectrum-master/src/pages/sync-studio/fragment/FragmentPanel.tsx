//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Dropdown, Menu, Modal } from 'antd';
import cx from 'classnames';
import { cloneDeep } from 'lodash';
import { useCallback, useEffect, useMemo, useState } from 'react';

import { ReactComponent as KebabIcon } from 'assets/icons/kebab.svg';
import Checkbox, { CheckboxChangeEvent } from 'components/Checkbox';
import EmptyGraphPanel from 'components/EmptyGraphPanel';
import GraphItemFilter from 'components/GraphItemFilter';
import { default as SIcon } from 'components/icons/Icon';
import { HIDDEN_TAG_ICON, NEW_FRAGMENT } from 'components/icons/Icons';
import InlineSVG from 'components/icons/InlineSvg';
import TabPanelSpin from 'components/TabPanelSpin';
import { useEnhancedDispatch } from 'hooks/redux';
import useToastForFetchStatusChange from 'hooks/useToastForFetchStatusChange';
import { PipelineContext } from 'pages/sync-studio/types';
import { getFragments } from 'store/fragment/thunks';
import { FragmentModel } from 'store/fragment/types';
import { FetchStatus } from 'store/types';
import AppConstants from 'utils/AppConstants';
import { tc, tNamespaced } from 'utils/i18nUtil';

import './FragmentPanel.less';

const tn = tNamespaced('FragmentPanel');

export interface FragmentPanelProps {
  fragments: FragmentModel[];
  onCreateFragment: () => void;
  showShareFragmentModal: (id: string, context: PipelineContext, visible?: boolean) => void;
  deleteFragment: (fragmentId: string, context: PipelineContext) => void;
  hideFragment: (fragmentId: string, context: PipelineContext) => void;
  showFragment: (fragmentId: string, context: PipelineContext) => void;
  context: PipelineContext;
  deleteFragmentStatus: FetchStatus;
  deleteFragmentErrorMessage?: string;
  hideFragmentStatus: FetchStatus;
  hideFragmentErrorMessage?: string;
  showFragmentStatus: FetchStatus;
  showFragmentErrorMessage?: string;
  getFragmentStatus: FetchStatus;
}

const FragmentPanel = ({
  fragments,
  onCreateFragment,
  showShareFragmentModal,
  deleteFragment,
  hideFragment,
  showFragment,
  context,
  deleteFragmentStatus,
  deleteFragmentErrorMessage,
  hideFragmentStatus,
  hideFragmentErrorMessage,
  showFragmentStatus,
  showFragmentErrorMessage,
  getFragmentStatus,
}: FragmentPanelProps) => {
  const dispatch = useEnhancedDispatch();

  const [showHidden, setShowHidden] = useState(false);

  const onShowHideFragment = (evt: CheckboxChangeEvent) => setShowHidden(evt.target.checked);

  useToastForFetchStatusChange(deleteFragmentStatus, {
    error: tn('delete_fragment_error', { errorMessage: deleteFragmentErrorMessage }),
    success: tn('delete_fragment_success'),
  });

  useToastForFetchStatusChange(hideFragmentStatus, {
    error: tn('hide_fragment_error', { errorMessage: hideFragmentErrorMessage }),
    success: tn('hide_fragment_success'),
  });

  useToastForFetchStatusChange(showFragmentStatus, {
    error: tn('show_fragment_error', { errorMessage: showFragmentErrorMessage }),
    success: tn('show_fragment_success'),
  });

  useEffect(() => {
    dispatch(getFragments(context));
  }, [context, dispatch]);

  const onDeleteFragment = useCallback(
    (fragment: FragmentModel) => {
      if (fragment.sharedWithInstances) {
        Modal.error({
          title: tn('delete_fragment'),
          content: (
            <span
              // Note: i18next sanitize the token for script injection
              dangerouslySetInnerHTML={{
                __html: tn('cannot_delete_shared_fragment_html', { name: fragment.displayName }),
              }}
            />
          ),
        });
      } else {
        Modal.confirm({
          title: tn('delete_fragment'),
          content: (
            <span
              // Note: i18next sanitize the token for script injection
              dangerouslySetInnerHTML={{
                __html: tn('delete_fragment_confirmation_html', { name: fragment.displayName }),
              }}
            />
          ),
          onOk: () => {
            fragment.id && deleteFragment(fragment.id, context);
          },
          onCancel() {},
        });
      }
    },
    [deleteFragment, context]
  );

  const itemList = useMemo(() => {
    return fragments
      ?.filter((fragment) => {
        if (showHidden) {
          return true;
        }
        if (!showHidden) {
          return !fragment.hidden;
        }
        return false;
      })
      .map((fragment) => {
        let subLabel;
        if (fragment.shared) {
          subLabel = tn('created_by', { name: `${fragment.ownerFirstName} ${fragment.ownerLastName}` });
        } else {
          subLabel = tn('created_by_you');
        }
        return {
          ...cloneDeep(fragment),
          label: fragment.displayName,
          title: fragment.displayName,
          tooltipMessage: fragment.description,
          subLabel,
          icon: fragment.iconPath && (
            <SIcon className="flow-item-prefix" src={fragment.iconPath} alt={tn('shared_fragment')} />
          ),
          suffix: (
            <>
              <InlineSVG
                className={cx('hidden-icon', { 'hidden-icon-visible': fragment.hidden })}
                title={tn('new_fragment')}
                src={HIDDEN_TAG_ICON}
              />
              <Dropdown
                trigger={['click']}
                overlay={
                  <Menu key={fragment.id}>
                    {!fragment.shared && (
                      <Menu.Item onClick={() => onDeleteFragment(fragment)}>{tn('delete_fragment')}</Menu.Item>
                    )}
                    {fragment.shared && !fragment.hidden && (
                      <Menu.Item onClick={() => fragment.id && hideFragment(fragment.id, context)}>
                        {tn('hide_fragment')}
                      </Menu.Item>
                    )}
                    {fragment.shared && fragment.hidden && (
                      <Menu.Item onClick={() => fragment.id && showFragment(fragment.id, context)}>
                        {tn('show_fragment')}
                      </Menu.Item>
                    )}
                    {!fragment.shared && (
                      <Menu.Item onClick={() => fragment?.id && showShareFragmentModal(fragment.id, context, true)}>
                        {tn('share_fragment')}
                      </Menu.Item>
                    )}
                  </Menu>
                }
                overlayClassName="kebab-menu-dropdown"
                placement="bottomRight"
                align={{ offset: [-5, 3] }}>
                <div className="kebab-menu-trigger" role="button">
                  <KebabIcon />
                </div>
              </Dropdown>
            </>
          ),
        };
      });
  }, [fragments, showHidden, context, showFragment, hideFragment, showShareFragmentModal, onDeleteFragment]);

  return (
    <TabPanelSpin delay={0} spinning={getFragmentStatus === AppConstants.FETCH_STATUS.LOADING} tip={tc('loading')}>
      <>
        {(!itemList || itemList?.length <= 0) && (
          <EmptyGraphPanel
            className="synri-create-draft-pipeline-panel"
            onActionClick={onCreateFragment}
            panelIcon={<InlineSVG title={tn('new_fragment')} src={NEW_FRAGMENT} />}
            actionText={tn('new_fragment_plus')}>
            <span dangerouslySetInnerHTML={{ __html: tn('create_new_fragment') }} />
          </EmptyGraphPanel>
        )}
        {itemList?.length > 0 && (
          <GraphItemFilter
            className="synri-fragment-graph-item-filter"
            filterPlaceHolder={tc('filter_label', { label: tn('fragments') })}
            items={itemList as any}
            graphItemClassName="synri-fragment"
            createHandler={onCreateFragment}
            filterChildren={<Checkbox onChange={onShowHideFragment}>{tn('show_hidden_fragments')}</Checkbox>}
          />
        )}
      </>
    </TabPanelSpin>
  );
};

export default FragmentPanel;
