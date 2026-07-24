//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Dropdown, Icon, Menu } from 'antd';
import Checkbox, { CheckboxChangeEvent } from 'antd/lib/checkbox';
import cx from 'classnames';
import { useCallback, useEffect, useMemo, useState } from 'react';

import { ReactComponent as KebabIcon } from 'assets/icons/kebab.svg';
import Button, { IconButton } from 'components/Button';
import DrawerPanel from 'components/DrawerPanel';
import EmptyGraphPanel from 'components/EmptyGraphPanel';
import GraphItemFilter from 'components/GraphItemFilter';
import { GraphItemModel } from 'components/GraphItems';
import HelpLink from 'components/HelpLink';
import { NODE_GRAPH_TEST, TEST_NEW } from 'components/icons/Icons';
import InlineSvg from 'components/icons/InlineSvg';
import Modal from 'components/Modal';
import TabPanelSpin from 'components/TabPanelSpin';
import { Text } from 'components/typography';
import { useEnhancedDispatch as useDispatch, useEnhancedSelector as useSelector } from 'hooks/redux';
import usePreviousValue from 'hooks/usePreviousValue';
import {
  deleteFieldTest,
  getFieldPipelineTests,
  resetSimulatedTestRun,
  setTestPanelView,
  showCreateTest,
  showRunTest,
} from 'store/test/actions';
import { selectedGetFieldPipelineTestsStatus, selectTestPanelView, selectTests } from 'store/test/selectors';
import { PipelineContextTypes, TestPanelView } from 'store/test/types';
import { ENTITY_DRAWER_HEIGHT_OFFSET, FIELD_DRAWER_HEIGHT_OFFSET } from 'styles/style.constants';
import AppConstants from 'utils/AppConstants';
import { nextEdgeHelpUrl } from 'utils/Branding';
import { tCommon as tc, tNamespaced } from 'utils/i18nUtil';

import TestRunSimulatedTitleModal from '../test-components/TestRunSimulatedTitleModal';
import './test-styles/TestRunSimulatedPanel.less';

const tn = tNamespaced('TestRunSimulatedPanel');

const { FETCH_STATUS } = AppConstants;

export interface TestPanelProps {
  pipelineId: string;
  pipelineContext: PipelineContextTypes;
  onSaveChanges: () => void;
  className?: string;
}

const TestRunSimulatedPanel = ({ pipelineId, pipelineContext, onSaveChanges, className }: TestPanelProps) => {
  const [checkedTests, setCheckedTests] = useState<Record<string, boolean>>({});
  const tests = useSelector(selectTests);
  const visible = useSelector(selectTestPanelView) === TestPanelView.SIMULATED_RUN;
  const dispatch = useDispatch();
  const previousVisible = usePreviousValue(visible);
  const getTestsStatus = useSelector(selectedGetFieldPipelineTestsStatus);

  const close = useCallback(() => {
    dispatch(setTestPanelView(TestPanelView.CLOSED));
  }, [dispatch]);

  useEffect(() => {
    if (visible && !previousVisible) {
      dispatch(getFieldPipelineTests({ pipelineContext, fieldPipelineId: pipelineId }));
    } else if (!visible && previousVisible) {
      setCheckedTests({});
      dispatch(resetSimulatedTestRun());
    }
  }, [dispatch, pipelineId, pipelineContext, visible, previousVisible]);

  const itemList: GraphItemModel[] = useMemo(() => {
    const onItemChecked = (e: CheckboxChangeEvent) => {
      e.target?.name &&
        setCheckedTests({
          ...checkedTests,
          [e.target.name]: e.target.checked,
        });
    };

    const items: GraphItemModel[] = [];
    tests.forEach((test) => {
      let subLabel: string;
      if (test.shared) {
        subLabel = tn('created_by', { name: `${test.ownerFirstName} ${test.ownerLastName}` });
      } else {
        subLabel = tn('created_by_you');
      }
      items.push({
        key: test.id,
        label: test.displayName,
        title: test.displayName,
        tooltipMessage: test.description,
        subLabel,
        icon: (
          <div
            className={cx('synri-simulated-test-panel__checkbox', {
              'synri-simulated-test-panel__checkbox--checked': test.id && checkedTests?.[test.id],
            })}>
            <Checkbox
              name={test.id}
              checked={test.id && checkedTests?.[test?.id] ? checkedTests?.[test.id] : false}
              onChange={onItemChecked}
            />
          </div>
        ),
        suffix: (
          <>
            <Dropdown
              placement="bottomRight"
              trigger={['click']}
              overlay={
                <Menu key={test.id}>
                  <Menu.Item
                    onClick={() => {
                      if (test.id) {
                        Modal.confirm({
                          title: (
                            <Text beDangerous>{tn('delete_simulated_test_title', { testName: test.displayName })}</Text>
                          ),
                          content: <Text beDangerous>{tn('delete_simulated_test')}</Text>,
                          okText: tc('delete'),
                          icon: <Icon type="exclamation-circle" />,
                          cancelText: tc('cancel'),
                          onOk: () => {
                            dispatch(
                              deleteFieldTest({ pipelineContext, fieldPipelineId: pipelineId, testId: test.id })
                            );
                          },
                        });
                      }
                    }}>
                    {tn('delete_test')}
                  </Menu.Item>
                  {/* // TODO: How do we handle non existing nodes when they edit? */}
                  <Menu.Item onClick={() => test.id && dispatch(showCreateTest(true, test.id))}>
                    {tn('edit_test')}
                  </Menu.Item>
                </Menu>
              }>
              <IconButton className="synri-simulated-test-panel__kebab" icon={KebabIcon} />
            </Dropdown>
          </>
        ),
      });
    });
    return items;
  }, [tests, checkedTests, dispatch, pipelineContext, pipelineId]);

  const run = async () => {
    await onSaveChanges();

    dispatch(
      showRunTest(
        true,
        Object.keys(checkedTests).filter((key) => checkedTests[key])
      )
    );
  };

  const selectAll = () => {
    const checked: Record<string, boolean> = {};
    tests.forEach((test) => {
      test.id && (checked[test.id] = true);
    });
    setCheckedTests(checked);
  };

  const checkedItemsCount = checkedTests && Object.keys(checkedTests).filter((k) => checkedTests[k]).length;

  const footerWithNoSelectedTests = (
    <>
      <div className="synri-simulated-test-panel__footer__left">{tn('select_one_more_tests')}</div>
      <Button type="link" onClick={selectAll}>
        {tn('select_all_tests')}
      </Button>
    </>
  );

  const footerWithSelectedTests = (
    <>
      <div className="synri-simulated-test-panel__footer__left">
        <InlineSvg src={NODE_GRAPH_TEST} title={tn('test')} />
        <span>{tn('test_ready_to_run', { count: checkedItemsCount })}</span>
      </div>
      <Button type="primary" icon="caret-right" onClick={run}>
        {tn('run')}
      </Button>
    </>
  );

  const footerToRender =
    itemList.length === 0 ? undefined : checkedItemsCount > 0 ? footerWithSelectedTests : footerWithNoSelectedTests;

  const footerClasses = cx('synri-simulated-test-panel__footer', {
    'synri-simulated-test-panel__footer--with-selection': checkedItemsCount && checkedItemsCount > 0,
  });

  return (
    <DrawerPanel
      absolutePositioning
      additionalHeightOffset={pipelineContext === 'entity' ? ENTITY_DRAWER_HEIGHT_OFFSET : FIELD_DRAWER_HEIGHT_OFFSET}
      className={cx('synri-simulated-test-panel', className)}
      footerClassName={footerClasses}
      footer={footerToRender}
      noPadding
      onClose={close}
      title={
        <div className="synri-simulated-test-panel__title">
          {tn('title')}{' '}
          <HelpLink href={nextEdgeHelpUrl('run-simulated-tests')} />
        </div>
      }
      visible={visible}>
      <TabPanelSpin spinning={getTestsStatus === FETCH_STATUS.LOADING} tip={tn('loading_tests')}>
        {itemList.length > 0 ? (
          <GraphItemFilter
            filterPlaceHolder={tc('filter_label', { label: tc('tests') })}
            items={itemList}
            graphItemType="check"
            createHandler={() => dispatch(showCreateTest(true))}
          />
        ) : (
          <EmptyGraphPanel
            onActionClick={() => dispatch(showCreateTest(true))}
            panelIcon={<InlineSvg size="2x" src={TEST_NEW} title={tn('new_test')} />}
            actionText={
              <span className="synri-simulated-test-panel__empty-action">
                <Icon type="plus" />
                {tn('new_test')}
              </span>
            }>
            <span>{tn('empty_message')}</span>
          </EmptyGraphPanel>
        )}
      </TabPanelSpin>
      <TestRunSimulatedTitleModal pipelineContext={pipelineContext} pipelineId={pipelineId} />
    </DrawerPanel>
  );
};

export default TestRunSimulatedPanel;
