/**
 * Userflow tags are used for `data-userflow-tag` attributes on elements queried by Syncari's Userflow implementation.
 *
 * To avoid breaking Userflow functionality:
 * - Changes to the values of tags MUST correspond to changes in Userflow content flows.
 * - Refactors of components that use these tags MUST leave the tag in place on an equivalant element.
 *
 * Tags should be unigue to avoid duplicates in the DOM that could cause userflow UI to attach to the wrong elements.
 *
 */

export const UserflowTags = {
  SideNav: {
    Container: 'side-nav',
    Data: 'side-nav-data',
    DataQuality: 'side-nav-data-quality',
    Expand: 'side-nav-logs',
    ImportedFiles: 'imported-files-logs',
    Insights: 'side-nav-logs',
    Logs: 'side-nav-logs',
    Schema: 'side-nav-synapse',
    Settings: 'side-nav-logs',
    Synapse: 'side-nav-synapse',
    Sync: 'side-nav-sync',
  },
  SyncStudio: {
    Canvas: 'sync-studio-canvas',
    ConfigureNode: 'configure-node',
    PipelineValidMessage: 'pipeline-valid-message',
    PipelineVersionSelector: 'pipeline-version-selector',
    PublishDraft: 'publish-draft-pipeline',
    TestPipeline: 'test-pipeline-menu',
    ValidatePipeline: 'validate-pipeline',
    ViewDetails: 'sync-studio-view-details',
    ViewRelationships: 'sync-studio-view-relationships',
    ViewSelector: 'sync-studio-view-selector',
    EntityTab: 'synapse-entities-tab',
    Resync: 'resync-pipeline',
    ResyncMenu: 'resync-pipeline-menu-trigger',
    ReadyToPublish: 'ready-to-publish-toggle',
  },
  Header: {
    HelpMenu: 'header-help-trigger',
    Notifications: 'header-notifications-trigger',
    ProfileMenu: 'header-profile-menu-trigger',
  },
  Toolbar: {
    Back: 'toolbar-back-button',
    Selector: 'toolbar-selector-dropdown',
  },
  SynapseStudio: {
    ActivateSynapse: 'connect-synapse-activate',
    Finish: 'connect-synapse-finish',
    List: 'synapse-list',
    Next: 'connect-synapse-next',
    Steps: 'connect-synapse-steps',
  },
  SchemaStudio: {
    EntitySelector: 'entity-selector',
    EntityVersionSelector: 'entity-version-selector',
    SynapseSelector: 'synapse-selector',
  },
};
