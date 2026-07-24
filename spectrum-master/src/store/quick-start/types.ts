//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { SkullConfig } from 'components/skull';
import { FetchStatus } from 'store/types';

export enum QuickStartMode {
  AUTHOR = 'AUTHOR',
  INSTALL = 'INSTALL',
}

export type PublishOptions = 'dontPublish' | 'publish';

export enum QuickStartStatus {
  PUBLISHED_WITH_DRAFT = 'PUBLISHED_WITH_DRAFT',
  APPROVED = 'APPROVED',
  NEW = 'NEW',
}

export interface QuickStartModel {
  id: string;
  displayName: string;
  description?: string;
  tags?: string[];
  postInstallationInstruction?: string;
  status?: QuickStartStatus;
  requiredSynapses?: string[];
  shareWithInstances?: string[];
  publishToQuickStartLibrary?: PublishOptions;
  lastPublishedAt?: string;
  iconPath?: string;
  config?: SkullConfig;
  shareWithOrg?: boolean;
  publishedQuickStartId?: string | null;
}

export interface DataFormatType {
  // Form format is Record<string, string | number | boolean | ...>
  // Graph format is nested Form format
  dataFormatType?: 'graph' | 'form';
}

export interface QuickStart extends QuickStartModel, DataFormatType {}

export interface QuickStartInstall
  extends Pick<QuickStart, 'id' | 'displayName' | 'requiredSynapses' | 'config' | 'iconPath'> {
  installStatus: 'INPROGRESS' | null;
}

export type QuickStartInstalls = QuickStartInstall[];

export type QuickStarts = QuickStart[];

export interface QuickStartInstance {
  subscriptionName: string;
  instanceName: string;
  value: string;
}

export type QuickStartInstances = QuickStartInstance[];

export interface QuickStartState {
  serverInstallStatus: Record<string, FetchStatus>;
}

export interface SaveQuickStartRejected {
  message: string;
}

export type SaveQuickStartResponse = QuickStart;

export type QuickStartDynamicStep = Record<string, any>;
