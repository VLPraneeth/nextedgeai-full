import configureStore from 'store/configureStore';
import { getSelectedGraphNode } from 'store/entity-pipeline/fixtures';
import { getCurrentGraphFixture } from 'store/pipeline/fixtures';
import { render, screen } from 'tests/helpers';
import AppConstants from 'utils/AppConstants';
import { t } from 'utils/i18nUtil';

import InputProxy from '../InputProxy/InputProxy';
import TokenizableFieldGroup from '../TokenizableFieldGroup';

test.each([[true], [false]])(
  'TokenizedFieldGroup displays Data token picker when enabled (%s)',
  async (enableTokens) => {
    const helpText = 'How do you want to be greeted?';
    const labelText = 'Salutation';
    const handleChange = jest.fn();

    render(
      <TokenizableFieldGroup disableTokens={!enableTokens} helpText={helpText} label={labelText}>
        <InputProxy datatype={AppConstants.INPUT_TYPE.TEXT} onChange={handleChange} value="Hello World" />
      </TokenizableFieldGroup>,
      {
        store: configureStore({
          entityPipeline: {
            selectedGraphNode: getSelectedGraphNode(),
          },
          pipeline: {
            // @ts-ignore
            currentGraph: getCurrentGraphFixture(),
          },
        }),
      }
    );

    const input = await screen.findByLabelText(labelText);
    expect(input).toBeInTheDocument();

    expect(await screen.findByText(helpText)).toBeInTheDocument();

    if (enableTokens) {
      expect(await screen.findByText(t('Tokens.insert_token_trigger_label'))).toBeInTheDocument();
    } else {
      await expect(screen.findByText(t('Tokens.insert_token_trigger_label'))).rejects.toThrow();
    }
  }
);
