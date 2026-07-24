import { useState } from 'react';

import { ReactComponent as FullSizeIcon } from 'assets/icons/full-size.svg';
import { IconButton } from 'components/Button';
import { DataCardWithData } from 'store/insights-studio/types';
import { tNamespaced } from 'utils/i18nUtil';

import { FullSizeVizModal } from '../full-size-viz-modal/FullSizeVizModal';

import './FullSizeVizButton.scss';

const tn = tNamespaced('InsightsStudio');
export interface TitleBarProps {
  dataCard?: DataCardWithData;
}

export const FullSizeVizButton = ({ dataCard }: TitleBarProps) => {
  const [showFullSize, setFullSize] = useState(false);

  const closeFullSize = () => setFullSize(false);

  return (
    <div className="full-size-viz-button">
      <IconButton
        aria-label={tn('view_full_size')}
        icon={FullSizeIcon}
        onClick={() => setFullSize(true)}
        title={tn('view_full_size')}
      />
      <FullSizeVizModal
        dataCard={dataCard}
        onCancel={closeFullSize}
        title={dataCard?.displayName}
        visible={showFullSize}
      />
    </div>
  );
};
