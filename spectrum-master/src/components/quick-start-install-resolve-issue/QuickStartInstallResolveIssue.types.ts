export type InstallResolutionTypes = 'service_credentials' | 'reference_data';
export type InstallServiceProviders = 'ClearBit';

export interface SelectMatchesOptions {
  id: string;
  label: string;
  optionPlaceholder: string;
  options: { label: string; value: string }[];
}

export type RefreshHandler = () => void;
export type NavigateToStepHandler = (stepNumber?: number) => void;

export interface SkullRenderTypeBaseProps {
  id: string;
  navigateToStep: NavigateToStepHandler;
  refreshStep: RefreshHandler;
  onChange: (data: unknown) => void;
}

export type MatchData = Record<string, string | null>;

export type QuickStartInstallResolveIssueTypes =
  | {
      type: 'issue_resolved';
      successTitle: string;
      successMessage: string;
    }
  | {
      type: 'service_credentials';
      serviceProvider: InstallServiceProviders;
    }
  | {
      type: 'create_synapse';
      synapseName: string;
      synapseType?: string;
      connectorMetadataName?: string;
    }
  | {
      type: 'select_matches';
      title: string;
      description: string;
      matches: SelectMatchesOptions[];
      defaultValue?: Record<string, string | null>;
    }
  | {
      type: 'reference_data';
      datasetTitle: string;
      columns: string[];
    };
