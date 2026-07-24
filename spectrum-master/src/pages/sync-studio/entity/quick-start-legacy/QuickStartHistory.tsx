import { ColDef } from 'ag-grid-community';
import { Button } from 'antd';
import moment from 'moment';
import { useMemo } from 'react';

import { showNodeConfigModal } from 'actions/entityPipelineActions';
import AgTable, { ResizeColumnsCondition } from 'components/AgTable';
import DrawerPanel from 'components/DrawerPanel';
import { useI18nContext } from 'components/I18nProvider';
import { HStack, Stack } from 'components/layout';
import CenterLayout from 'components/layout/CenterLayout';
import { ConfigContext } from 'components/skull';
import Spinner from 'components/Spinner';
import { Text, TranslatedText } from 'components/typography';
import { useEnhancedDispatch } from 'hooks/redux';
import { EMPTY_ARRAY } from 'store/constants';
import { useGetQuickStartHistoryQuery } from 'store/quick-start-legacy/api';
import { QuickStartRunStatus } from 'store/quick-start-legacy/types';
import { SHORT_DATE_TIME_FORMAT } from 'utils/DateUtil';
import { tNamespaced } from 'utils/i18nUtil';
import { toTitleCase } from 'utils/StringUtil';

const tn = tNamespaced('SyncStudio');

const StatusCell = (props: { value: QuickStartRunStatus }) => {
  return (
    <HStack>
      <Text>{toTitleCase(props.value)}</Text>
      {/* Cancel button is being punted on for now. */}
      {/* {[QuickStartRunStatus.QUEUED, QuickStartRunStatus.PROCESSING].includes(props.value) && (
        <Button
          size="small"
          onClick={() => {
            console.log('here');
          }}>
          <TranslatedText text="cancel" />
        </Button>
      )} */}
    </HStack>
  );
};

export interface QuickStartHistoryProps {
  name: null | string;
  onClose: () => void;
}

const columnDefs: ColDef[] = [
  { headerName: tn('date_run'), field: 'dateRun' },
  { headerName: tn('initiated_by'), field: 'executedByName' },
  { headerName: tn('details'), field: 'details' },
  { headerName: tn('status'), field: 'status', cellRenderer: 'statusCell' },
];

const frameworkComponents = {
  statusCell: StatusCell,
};

const QuickStartHistoryLegacy = ({ onClose, name }: QuickStartHistoryProps) => {
  const { data: history, isLoading } = useGetQuickStartHistoryQuery(
    { quickStartName: name as string },
    {
      // If no name is provided then the panel is closed and we should skip the query
      skip: !name,
      refetchOnMountOrArgChange: true,
    }
  );

  const { tn } = useI18nContext();
  const dispatch = useEnhancedDispatch();

  const rowData = useMemo(() => {
    if (!history) {
      return EMPTY_ARRAY;
    }
    return history.runs.map((historyItem) => {
      return {
        ...historyItem,
        dateRun: historyItem.executedAt ? moment(historyItem.executedAt).format(SHORT_DATE_TIME_FORMAT) : '',
      };
    });
  }, [history]);

  const noData = useMemo(
    () => (
      <CenterLayout>
        <Stack center>
          <TranslatedText text="no_history_found" />
          <Button
            onClick={() => {
              onClose();
              dispatch(showNodeConfigModal(true, ConfigContext.QUICK_START, name));
            }}>
            <TranslatedText text="run_quick_start" args={{ quickStartName: history?.displayName }} />
          </Button>
        </Stack>
      </CenterLayout>
    ),
    [dispatch, history?.displayName, name, onClose]
  );

  return (
    <DrawerPanel
      title={tn('history_panel_title', { quickStartName: history?.displayName })}
      mask
      maskClosable
      width="full"
      visible={!!name}
      onClose={onClose}>
      {isLoading ? (
        <Spinner />
      ) : rowData.length > 0 ? (
        <AgTable
          domLayout="autoHeight"
          columnDefs={columnDefs}
          frameworkComponents={frameworkComponents}
          rowData={rowData}
          getRowNodeId={(data) => data.dateRun}
          sizeColumnsToFit={ResizeColumnsCondition.ALWAYS}
          suppressCellSelection
        />
      ) : (
        noData
      )}
    </DrawerPanel>
  );
};

export default QuickStartHistoryLegacy;
