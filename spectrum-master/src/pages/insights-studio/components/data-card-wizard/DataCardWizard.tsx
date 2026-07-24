import { useState } from 'react';

import DrawerPanel from 'components/DrawerPanel';
import { HStack } from 'components/layout';
import { Step, Steps } from 'components/steps';
import { useDataCardAuthoringContext } from 'pages/insights-studio/context/DataCardAuthoringContext';

import { BasicInfoForm } from './BasicInfoForm';
import { DataCardConfigStep } from './configuration-step/DataCardConfigStep';

import './DataCardWizard.less';

export interface DataCardWizardProps {
  close: () => void;
  visible?: boolean;
}

export const DataCardWizard = () => {
  const { resetAuthoring, showDataCardWizard } = useDataCardAuthoringContext();

  const [currentStep, setCurrentStep] = useState(0);

  const previousStep = () => setCurrentStep(currentStep - 1);

  const advanceStep = () => {
    setCurrentStep(currentStep + 1);
  };

  const closeAndReset = () => {
    setCurrentStep(0);
    resetAuthoring();
  };

  const content = [
    <BasicInfoForm onCancel={closeAndReset} onSuccess={advanceStep} />,
    <DataCardConfigStep onCancel={closeAndReset} onPrevious={previousStep} />,
  ];

  return (
    <DrawerPanel
      destroyOnClose
      maskClosable
      noPadding
      onClose={closeAndReset}
      title="Data Card Wizard"
      visible={showDataCardWizard}
      width="full">
      <HStack spacing="z" align="start" className="data-card-wizard">
        <Steps direction="vertical" current={currentStep} onChange={setCurrentStep}>
          <Step title="Basic info" />
          <Step title="Configuration" />
        </Steps>
        <div className="data-card-wizard__content">{content[currentStep]}</div>
      </HStack>
    </DrawerPanel>
  );
};
