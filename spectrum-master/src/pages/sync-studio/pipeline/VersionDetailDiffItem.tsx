import { ColumnProps } from 'antd/lib/table';
import cx from 'classnames';
import * as Diff from 'diff';
import { useState } from 'react';

import { ReactComponent as ChevronDown } from 'assets/icons/chevron-down.svg';
import Table from 'components/Table';
import { TextTag } from 'components/text-tag';
import { TextTagColorOptions } from 'components/text-tag/TextTag';
import { Text, TranslatedText } from 'components/typography';
import { DiffValue, PipelineDiff, PipelineDiffOps } from 'store/pipeline/types';
import { tNamespaced } from 'utils/i18nUtil';

export interface VersionDetailDiffItemProps {
  diff: PipelineDiff;
}

const columns: ColumnProps<DiffValue>[] = [
  {
    title: `Configuration\u00A0Name`,
    dataIndex: 'label',
    key: 'label',
    width: 180,
  },
  {
    title: 'Previous Value',
    dataIndex: 'previousValue',
    key: 'previousValue',
    className: 'previous-value-column',
    render: (value: string, data: DiffValue) => {
      return <Text beDangerous={data.renderHtml}>{value}</Text>;
    },
  },
  {
    title: 'Current Value',
    dataIndex: 'value',
    key: 'value',
    render: (value: string, data: DiffValue) => {
      const diffList = Diff.diffWordsWithSpace(data.previousValue || '', value || '', {});

      return diffList.map((item, index) => {
        return (
          <Text
            key={index}
            beDangerous={data.renderHtml}
            className={cx(item.removed && 'removed-diff', item.added && 'added-diff')}>
            {item.value}
          </Text>
        );
      });
    },
  },
];

const opColorMap: Record<PipelineDiffOps, TextTagColorOptions> = {
  add: 'green',
  modified: 'blue',
  remove: 'red',
};

const opTextMap: Record<PipelineDiffOps, string> = {
  add: 'Creation',
  modified: 'Modification',
  remove: 'Delete',
};

const VersionDetailDiffItem = ({ diff }: VersionDetailDiffItemProps) => {
  const [isOpen, setIsOpen] = useState(false);
  const tn = tNamespaced('PipelineVersions');

  return (
    <div
      className={cx('compare-versions-diff-container', {
        'is-open': isOpen,
      })}>
      <div
        onClick={() => setIsOpen(!isOpen)}
        className={cx('compare-versions-diff-container__label-container', {
          'is-open': isOpen,
        })}>
        <div className="compare-versions-diff-container__label-container--label-item">
          <TranslatedText size="md" color="gray-800" text="node_type" />
          <Text size="lg" weight="semibold" color="gray-800">
            {diff.nodeType}
          </Text>
        </div>

        <div className="compare-versions-diff-container__label-container--label-item">
          <TranslatedText size="md" color="gray-800" text="name" />
          <Text size="lg" weight="semibold" color="gray-800">
            {diff.itemName === 'Predicate' ? tn('true_false') : diff.itemName}
          </Text>
        </div>

        <div className="compare-versions-diff-container__label-container--label-item">
          <TranslatedText size="md" color="gray-800" text="display_label" />
          <Text size="lg" weight="semibold" color="gray-800">
            {diff.displayName}
          </Text>
        </div>

        <div className="compare-versions-diff-container__label-container--label-item">
          <TranslatedText size="md" color="gray-800" text="change_type" />
          <TextTag text={opTextMap[diff.op]} color={opColorMap[diff.op]} />
        </div>

        <div className="compare-versions-diff-container__label-container--spacer" />

        <ChevronDown
          className={cx('compare-versions-diff-container__label-arrow', {
            'is-open': isOpen,
          })}
        />
      </div>
      <div style={{ display: isOpen ? 'initial' : 'none' }}>
        {isOpen && (
          <Table columns={columns} pagination={false} dataSource={diff.values} className="version-detail-diff" />
        )}
      </div>
    </div>
  );
};

export default VersionDetailDiffItem;
