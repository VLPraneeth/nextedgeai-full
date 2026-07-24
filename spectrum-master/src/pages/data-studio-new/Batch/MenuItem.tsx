import Dropdown from 'antd/lib/dropdown';
import message from 'antd/lib/message';
import { useState } from 'react';

import Button from 'components/Button';
import { DropdownDisclosureArrow } from 'components/dropdown-disclosure-arrow/DropdownDisclosureArrow';
import { useI18nContext } from 'components/I18nProvider';
import { HStack, Stack } from 'components/layout';
import ProgressBar from 'components/ProgressBar';
import { Text, TranslatedText } from 'components/typography';
import { useCancelBatchMutation, useGetBatchesForEntityQuery } from 'store/data-studio-batch';
import { Batch, BatchStatus } from 'store/data-studio-batch/types';
import { LONG_DATETIME_FORMAT_WITH_TZ, format } from 'utils/DateUtil';
import { numberFormat } from 'utils/i18nUtil';

import './MenuItem.less';

const POLLING_INTERVAL_MS = 15000;

const BatchProgressLineItem = ({
  batch: { id, initiatedAt, initiatedByUser, operation, recordsProcessed, recordsTotal, errors },
}: {
  batch: Batch;
}) => {
  const { tc, tn } = useI18nContext();
  const [cancelBatch, { isLoading: isCancelling }] = useCancelBatchMutation();

  const handleCancelBatch = async () => {
    try {
      const result = await cancelBatch({ batchId: id }).unwrap();

      if (result) {
        message.success(tn('batch_cancel_successful'));
      } else {
        throw new Error(tn('batch_cancel_failure'));
      }
    } catch (err) {
      message.error(err instanceof Error ? err.message : tc('generic_error'));
    }
  };

  return (
    <div className="batch-line-item">
      <HStack justify="space-between">
        <div>
          <div>
            <TranslatedText
              color="black"
              text={operation === 'delete' ? 'batch_deleting_records' : 'batch_updating_records'}
              args={{
                count: recordsTotal,
              }}
            />{' '}
            <TranslatedText
              color="gray-700"
              text="batch_progress"
              args={{ numerator: recordsProcessed, denominator: recordsTotal }}
            />
          </div>
          {errors?.length && <Text color="red-500">{errors.join(', ')}</Text>}
          <div>
            <TranslatedText
              color="gray-700"
              size="xs"
              text="batch_initiated_by"
              args={{
                initiatedBy: initiatedByUser,
                initiatedAt: format(initiatedAt, LONG_DATETIME_FORMAT_WITH_TZ),
              }}
            />
          </div>
        </div>
        <Button loading={isCancelling} type="danger" onClick={handleCancelBatch}>
          {tc('cancel')}
        </Button>
      </HStack>
    </div>
  );
};

export type DataStudioBatchProgressProps = { entityId: string };

const BatchProgressMenuItem = ({ entityId }: DataStudioBatchProgressProps) => {
  const [menuOpen, setMenuOpen] = useState(false);

  const { data: batches, error, isError, isLoading } = useGetBatchesForEntityQuery(
    { entityId },
    {
      pollingInterval: POLLING_INTERVAL_MS,
    }
  );

  if (isLoading || !batches || !batches.length) {
    return null;
  }

  if (isError) {
    if (process.env.NODE_ENV !== 'production') {
      console.error((error as any)?.data?.message || (error as Error)?.message || 'Encountered an issue');
    }

    return null;
  }

  const batchesInProgress = batches.filter(
    (batch) =>
      batch.status === BatchStatus.NEW ||
      batch.status === BatchStatus.PENDING ||
      batch.status === BatchStatus.PROCESSING
  );

  if (!batchesInProgress.length) {
    return null;
  }

  const batchesProgress = batchesInProgress.reduce(
    ({ processed, total }, batch) => ({
      processed: processed + batch.recordsProcessed,
      total: total + batch.recordsTotal,
    }),
    { processed: 0, total: 0 }
  );

  const progressPercentage = batchesProgress.total
    ? Math.ceil(100 * (batchesProgress.processed / batchesProgress.total))
    : 0;
  const formattedBatchCount = numberFormat(batchesInProgress.length);

  const batchItems = (
    <Stack divider spacing="z">
      {batchesInProgress.map((batch) => (
        <BatchProgressLineItem key={batch.id} batch={batch} />
      ))}
    </Stack>
  );

  return (
    <Dropdown
      visible={menuOpen}
      onVisibleChange={setMenuOpen}
      overlay={batchItems}
      overlayClassName="batch-list-overlay"
      trigger={['click']}>
      <div className="progress-menu-item-trigger">
        <HStack>
          <div>
            <Stack spacing="z" divider>
              <ProgressBar progress={progressPercentage} />
              <TranslatedText
                size="sm"
                text="batches_in_progress"
                args={{ count: batchesInProgress.length, formattedCount: formattedBatchCount, progressPercentage }}
              />
            </Stack>
          </div>
          <DropdownDisclosureArrow isOpen={menuOpen} />
        </HStack>
      </div>
    </Dropdown>
  );
};

export default BatchProgressMenuItem;
