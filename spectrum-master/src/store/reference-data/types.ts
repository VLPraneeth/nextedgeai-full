import { FetchStatus } from 'store/types';

export interface ReferenceDataRecordEntityPipeline {
  id: string;
  path: string;
  name: string;
}

export interface ReferenceDataPreview {
  headerColumns: string[];
  rows: string[][];
}

export interface ReferenceDataRecord {
  accessKey: string | null;
  csvFile: string | null;
  id: string;
  importDetails: string | null;
  key: string;
  lastImported: string;
  location: string;
  name: string;
  secretKey: string | null;
  standard: boolean;
  status: 'ACTIVE' | 'NEW' | 'INACTIVE';
  totalRecords: string;
  type: string;
  usedInPipelines: ReferenceDataRecordEntityPipeline[];
  preview?: ReferenceDataPreview;
}

export interface ExtraStateShape {
  previewStatus: Record<string, FetchStatus>;
  previewError: Record<string, string>;
  upsertStatus: Record<string, FetchStatus>;
  upsertError: Record<string, string>;
}
