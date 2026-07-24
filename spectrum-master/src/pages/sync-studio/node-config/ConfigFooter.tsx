//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { Button } from 'antd';

import { SKULL_CUSTOM_FOOTER_PORTAL_ROOT_ID, transformGraphNodeValues, useSkullConfigContext } from 'components/skull';
import { tc } from 'utils/i18nUtil';

import { usePipelineEditor } from '../pipeline/v2/context/PipelineEditorV2.context';
import { PipelineNodeV2 } from '../pipeline/v2/types/BackendPipeline.types';
import { usePipelineEditorV2Enabled } from '../utils/usePipelineEditorV2Enabled';
import './ConfigFooter.less';

export interface ConfigFooterProps {
  onClose?: () => void;
}
const ConfigFooter = ({ onClose }: ConfigFooterProps) => {
  const {
    close,
    currentStep,
    finish,
    next,
    previous,
    steps,
    loadingNextStep,
    graphNodeValue,
    values,
    configInputs,
  } = useSkullConfigContext();

  const pipelineV2Enabled = usePipelineEditorV2Enabled();
  const { saveNodeConfiguration } = usePipelineEditor();

  if (steps?.[currentStep]?.customFooter) {
    // When customFooter is true then a component in the step content can mount
    // a custom footer using React.createPortal
    return <div id={SKULL_CUSTOM_FOOTER_PORTAL_ROOT_ID} className="synri-config-footer" />;
  }

  return (
    <div className="synri-config-footer">
      {!steps?.[currentStep]?.closeStep && (
        <Button key="cancel" onClick={() => (onClose ? onClose() : close())}>
          {steps?.[currentStep]?.cancel ? steps?.[currentStep]?.cancel?.buttonText : tc('cancel')}
        </Button>
      )}
      {currentStep > 0 && !steps?.[currentStep]?.closeStep && (
        <Button disabled={loadingNextStep} key="previous" onClick={previous}>
          {tc('previous')}
        </Button>
      )}
      {currentStep >= 0 && currentStep < steps?.length - 1 && (
        <Button disabled={loadingNextStep} key="next" type="primary" onClick={next}>
          {steps?.[currentStep]?.next ? steps?.[currentStep]?.next?.buttonText : tc('next')}
        </Button>
      )}
      {currentStep >= steps?.length - 1 && (
        <Button
          disabled={loadingNextStep}
          key="finish"
          type="primary"
          onClick={() => {
            finish();
            if (pipelineV2Enabled) {
              if (graphNodeValue) {
                const clonedGraphNodeValue = transformGraphNodeValues(values, configInputs, graphNodeValue);
                saveNodeConfiguration(clonedGraphNodeValue as Partial<PipelineNodeV2>);
              }
            }
          }}>
          {steps?.[currentStep]?.finish ? steps?.[currentStep]?.finish?.buttonText : tc('finish')}
        </Button>
      )}
    </div>
  );
};

export default ConfigFooter;
