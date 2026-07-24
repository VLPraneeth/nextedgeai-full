import { HTTPCustomSynapseDetails } from 'components/custom-synapse/http/HTTPCustomSynapseDetails';
import { CustomSynapse } from 'components/custom-synapse/types';
import DrawerPanel from 'components/DrawerPanel';

interface HTTPSynapseConfigDrawerProps {
  visible: boolean;
  handleVisibleChange: (visible: boolean) => void;
  synapse: CustomSynapse | undefined;
}
export function HTTPSynapseConfigDrawer({ visible, handleVisibleChange, synapse }: HTTPSynapseConfigDrawerProps) {
  return (
    <DrawerPanel
      keyboard={false}
      maskClosable={false}
      onClose={() => handleVisibleChange(false)}
      destroyOnClose
      title={synapse?.displayName}
      visible={visible}
      width="xlarge">
      <div className="ant-form-item">
        <HTTPCustomSynapseDetails synapse={synapse} />
      </div>
    </DrawerPanel>
  );
}
