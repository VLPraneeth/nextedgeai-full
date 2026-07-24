import { Button, Icon } from 'antd';

import DrawerPanel from 'components/DrawerPanel';
import EmptyGraphPanel from 'components/EmptyGraphPanel';
import { tc } from 'utils/i18nUtil';

import './PipelineErrorPanel.less';

interface PipelineErrorPanelProps {
  onClose: () => void;
  title: string;
  visible: boolean;
}

export const PipelineErrorPanel = ({ title, onClose, visible }: PipelineErrorPanelProps) => {
  return (
    <DrawerPanel
      absolutePositioning
      onClose={onClose}
      title={title}
      visible={visible}
      footer={
        <Button onClick={onClose} type="primary">
          {tc('ok')}
        </Button>
      }>
      <EmptyGraphPanel
        className="synri-pipeline-error-panel"
        panelIcon={<Icon type="exclamation-circle" theme="filled" />}>
        <span>{tc('error_pipeline')}</span>
      </EmptyGraphPanel>
    </DrawerPanel>
  );
};
