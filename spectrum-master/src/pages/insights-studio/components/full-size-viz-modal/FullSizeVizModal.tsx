import Button from 'components/Button';
import Modal, { ModalProps } from 'components/Modal';
import { Vizer } from 'components/vizer/Vizer';
import { DataCardWithData } from 'store/insights-studio/types';

export interface FullSizeVizModalProps extends ModalProps {
  dataCard?: DataCardWithData;
}

export const FullSizeVizModal = ({ dataCard, onCancel, title, visible, ...rest }: FullSizeVizModalProps) => {
  const height = window.innerHeight - 300;
  const width = dataCard?.contents?.configuration?.vizType === 'METRIC' ? '50%' : '90%';
  return (
    <Modal
      centered
      destroyOnClose
      footer={<Button onClick={onCancel}>Close</Button>}
      maskClosable
      onCancel={onCancel}
      title={title}
      visible={visible}
      width={width}
      {...rest}>
      {dataCard && (
        <Vizer
          dataCardId={dataCard.id}
          dataCardContent={dataCard.contents}
          dataConfiguration={dataCard.configuration}
          graphHeight={height}
        />
      )}
    </Modal>
  );
};
