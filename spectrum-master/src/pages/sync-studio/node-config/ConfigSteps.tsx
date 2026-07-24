//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Steps as ASteps } from 'antd';

import { Step, Steps } from 'components';
import { useSkullConfigContext } from 'components/skull';

import './ConfigSteps.less';

const { Step: AStep } = ASteps;

export interface ConfigStepsProps {
  direction?: string;
}

const ConfigSteps = ({ direction }: ConfigStepsProps) => {
  const { steps, currentStep } = useSkullConfigContext();

  return direction === 'vertical' ? (
    <Steps current={currentStep} direction="vertical">
      {steps.map((step) => (
        <Step title={step.stepName} key={`config-steps-${step.stepName}`} />
      ))}
    </Steps>
  ) : (
    <div className="synri-config-steps-container">
      <ASteps current={currentStep}>
        {steps.map((step) => (
          <AStep title={step.stepName} key={`config-steps-${step.stepName}`} />
        ))}
      </ASteps>
    </div>
  );
};

export default ConfigSteps;
