import { useEffect, useMemo } from 'react';

import Button from 'components/Button';
import { withI18n } from 'components/I18nProvider';
import Select from 'components/inputs/Select';
import { HStack, Stack } from 'components/layout';
import { QuickStartIssueResolution } from 'components/quick-start-issue-resolution';
import { Text, TranslatedText } from 'components/typography';
import { UnreachableCaseError } from 'utils/TypeUtils';
import useSetState from 'utils/useSetState';

import {
  MatchData,
  QuickStartInstallResolveIssueTypes,
  RefreshHandler,
  SelectMatchesOptions,
  SkullRenderTypeBaseProps,
} from './QuickStartInstallResolveIssue.types';

export type QuickStartInstallResolveIssueProps = QuickStartInstallResolveIssueTypes & SkullRenderTypeBaseProps;

const openPathFunction = (path: string) => () => window.open(window.location.origin + path);

const getServiceCredentialsUi = (serviceProvider: string, refreshStep: RefreshHandler) => {
  return (
    <Stack spacing="lg">
      <Stack spacing="xs">
        <TranslatedText
          text="missing_service_credentials"
          size="lg"
          weight="semibold"
          args={{ provider: serviceProvider }}
        />
        <TranslatedText text="add_service_credentials_description" beDangerous args={{ provider: serviceProvider }} />
      </Stack>

      <HStack>
        <Button type="primary" onClick={openPathFunction('/settings/credential')}>
          <TranslatedText text="open_service_credential_settings" />
        </Button>
        <Button type="primary" onClick={refreshStep}>
          <TranslatedText text="verify" />
        </Button>
      </HStack>
    </Stack>
  );
};

const getCreateSynapseUi = (
  synapseName: string,
  synapseType: string | undefined,
  connectorMetadataName: string | undefined,
  refreshStep: RefreshHandler
) => {
  return (
    <Stack spacing="lg">
      <Stack spacing="xs">
        <TranslatedText
          color="gray-900"
          text="missing_synapse"
          size="lg"
          weight="semibold"
          args={{
            name: synapseName,
            connectorMetadataName: connectorMetadataName || synapseName,
            synapseType: synapseType || synapseName,
          }}
        />
        <TranslatedText
          text="create_synapse_description"
          beDangerous
          args={{ name: synapseName, synapseType: synapseType || synapseName }}
        />
      </Stack>

      <HStack>
        <Button type="primary" onClick={openPathFunction('/synapses')}>
          <TranslatedText text="open_synapses" />
        </Button>
        <Button type="primary" onClick={refreshStep}>
          <TranslatedText text="verify" />
        </Button>
      </HStack>
    </Stack>
  );
};

interface SelectMatchesUiProps {
  title: string;
  description: string;
  matches: SelectMatchesOptions[];
  onChange: (data: MatchData) => void;
  defaultValue?: MatchData;
}
const SelectMatchesUi = ({ title, description, matches, onChange, defaultValue }: SelectMatchesUiProps) => {
  const [formData, setFormData] = useSetState(() => {
    if (defaultValue) {
      return defaultValue;
    }

    const matchData: MatchData = {};
    return matches.reduce((prev, curr) => {
      prev[curr.id] = null;
      return prev;
    }, matchData);
  });

  useEffect(() => {
    onChange(formData);
  }, [formData, onChange]);

  return (
    <Stack spacing="lg">
      <Stack spacing="xs">
        <Text color="gray-900" size="lg" weight="semibold">
          {title}
        </Text>
        <Text color="gray-850">{description}</Text>
      </Stack>

      <Stack spacing="lg">
        {matches.map((match) => {
          return (
            <Stack spacing="xs">
              <Text key={match.label} color="gray-750">
                {match.label}
              </Text>
              <Select
                className="synri-resolve-issue-selector"
                placeholder={match.optionPlaceholder}
                optionData={match.options}
                value={formData[match.id] || undefined}
                onChange={(val: string) => {
                  setFormData({ [match.id]: val });
                }}
              />
            </Stack>
          );
        })}
      </Stack>
    </Stack>
  );
};

const getReferenceDataUi = (datasetTitle: string, columns: string[], refreshStep: RefreshHandler) => {
  return (
    <Stack spacing="lg">
      <Stack spacing="xs">
        <TranslatedText color="gray-900" text="missing_reference_data" size="lg" weight="semibold" />
        <TranslatedText text="add_reference_data_description" beDangerous />
      </Stack>

      <Stack spacing="xxxs">
        <TranslatedText text="dataset_title" color="gray-750" />
        <Text color="black">{datasetTitle}</Text>
      </Stack>

      <Stack spacing="xxxs">
        <TranslatedText text="columns" color="gray-750" />
        <Text color="black">{columns.join(', ')}</Text>
      </Stack>

      <HStack>
        <Button type="primary" onClick={openPathFunction('/data-studio')}>
          <TranslatedText text="open_data_studio" />
        </Button>
        <Button type="primary" onClick={refreshStep}>
          <TranslatedText text="verify" />
        </Button>
        {/* <Button type="primary">
          <TranslatedText text="download_csv_template" />
        </Button> */}
      </HStack>
    </Stack>
  );
};

const QuickStartInstallResolveIssue = (props: QuickStartInstallResolveIssueProps) => {
  const content = useMemo(() => {
    const { type, refreshStep } = props;
    switch (type) {
      case 'service_credentials':
        return getServiceCredentialsUi(props.serviceProvider, refreshStep);

      case 'create_synapse':
        return getCreateSynapseUi(props.synapseName, props.synapseType, props.connectorMetadataName, refreshStep);

      case 'reference_data':
        return getReferenceDataUi(props.datasetTitle, props.columns, refreshStep);

      case 'select_matches':
        return <SelectMatchesUi {...props} />;

      case 'issue_resolved':
        return (
          <QuickStartIssueResolution
            successTitle={props.successTitle}
            successMessage={props.successMessage}
            navigateToStep={() => props.navigateToStep()}
          />
        );

      default:
        throw new UnreachableCaseError(type);
    }
  }, [props]);

  return content;
};

export default withI18n(QuickStartInstallResolveIssue, 'QuickStart');
