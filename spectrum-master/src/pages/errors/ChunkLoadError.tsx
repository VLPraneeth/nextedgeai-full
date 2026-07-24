import Button from 'components/Button';
import { withI18n } from 'components/I18nProvider';
import { Stack } from 'components/layout';
import { TranslatedText } from 'components/typography';
import RouteConstants from 'utils/RouteConstants';

import Err from './Err';

const reload = () => window.location.assign(RouteConstants.HOME);

export const ChunkLoadErrorType = 'ChunkLoadError';
export const isChunkLoadErrorType = (err: Error) => err?.name === ChunkLoadErrorType;

const ChunkLoadError = () => {
  return (
    <Err>
      <Stack>
        <Stack>
          <TranslatedText color="syncari-blue" size="xl" text="title" />
          <TranslatedText color="gray-800" size="lg" weight="medium" text="description" />
        </Stack>
        <Button type="primary" onClick={reload}>
          <TranslatedText color="white" text="reload_btn" />
        </Button>
      </Stack>
    </Err>
  );
};

export default withI18n(ChunkLoadError, 'ChunkLoadError');
