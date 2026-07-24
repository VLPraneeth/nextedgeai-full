import { RouteComponentProps } from '@reach/router';

import IFrameEmbed from 'components/IFrameEmbed';
import { useLayoutContext } from 'pages/LayoutContext';
import { getArcadeUiUrl } from 'utils/AppUtil';

const DebugFlag = (props: RouteComponentProps) => {
  const layout = useLayoutContext();

  return (
    <IFrameEmbed
      src={getArcadeUiUrl('/arcade/ui/debug')}
      title="Debug Flag"
      height={layout.dimensions.content.height}
    />
  );
};

export default DebugFlag;
