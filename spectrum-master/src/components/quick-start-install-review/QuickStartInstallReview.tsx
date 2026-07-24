import { useMemo } from 'react';

import { withI18n } from 'components/I18nProvider';
import { FieldItem } from 'components/inputs/FieldOptions';
import { HStack } from 'components/layout';
import { PipelinePickerEntityField } from 'components/pipeline-picker/PipelinePicker.types';
import StatBlock from 'components/StatBlock';
import TreeSkeleton, { TreeItem } from 'components/tree-skeleton';
import { Text, TranslatedText } from 'components/typography';
import { UnreachableCaseError } from 'utils/TypeUtils';

import {
  QSInstallEntityPipelines,
  QSInstallEntityReplacement,
  QSInstallReviewMergePipeline,
} from './QuickStartInstall.types';

const borderOptions = { labelBottom: false, contentBottom: false };

export enum RenderInfoTypes {
  STRING_LIST = 'stringList',
  REPLACED_BY_PIPELINES = 'replacedByPipelines',
  MERGED_PIPELINES = 'mergedPipelines',
  FIELD_PIPELINES = 'fieldPipelines',
  ENTITY_PIPELINES = 'entityPipelines',
}

export type RenderInfoOptions =
  | {
      type: RenderInfoTypes.STRING_LIST;
      data: string[];
    }
  | {
      type: RenderInfoTypes.REPLACED_BY_PIPELINES;
      data: QSInstallEntityReplacement[];
    }
  | {
      type: RenderInfoTypes.MERGED_PIPELINES;
      data: QSInstallReviewMergePipeline[];
    }
  | {
      type: RenderInfoTypes.ENTITY_PIPELINES;
      data: QSInstallEntityPipelines[];
    }
  | {
      type: RenderInfoTypes.FIELD_PIPELINES;
      data: PipelinePickerEntityField[];
    };

export interface QuickStartReviewItem {
  label: string;
  count: number;
  renderInfo: RenderInfoOptions;
}

export interface QuickStartInstallReviewProps {
  items: QuickStartReviewItem[];
}

const getRenderContent = (renderInfo: RenderInfoOptions) => {
  const { type } = renderInfo;

  switch (type) {
    case RenderInfoTypes.STRING_LIST:
      return (
        <div className="synri-qs-install-review-tree-content">
          {renderInfo.data.map((value) => (
            <Text key={value} color="gray-1000">
              {value}
            </Text>
          ))}
        </div>
      );

    case RenderInfoTypes.FIELD_PIPELINES:
      return (
        <div className="synri-qs-install-review-tree-content">
          {renderInfo.data.map((field) => (
            <div key={field.id} className="synri-field-height-wrapper">
              <FieldItem {...field} />
            </div>
          ))}
        </div>
      );

    case RenderInfoTypes.ENTITY_PIPELINES:
      const items: TreeItem[] = renderInfo.data.map((entity) => {
        return {
          key: entity.id,
          label: entity.displayName,
          children: entity.fields.map((field) => (
            <div key={field.id} className="synri-field-height-wrapper">
              <FieldItem {...field} />
            </div>
          )),
        };
      });

      return <TreeSkeleton defaultOpen items={items} />;

    case RenderInfoTypes.REPLACED_BY_PIPELINES:
      const treeItems = renderInfo.data.map(
        (entity): TreeItem => {
          const fieldRows = entity.replacementFields.map((row) => {
            return (
              <tr key={row.field.id}>
                <td>
                  <FieldItem {...row.field} />
                </td>
                <td>
                  <TranslatedText color="gray-600" size="sm" text="replaced_by" />
                </td>
                <td>
                  <FieldItem {...row.replacementField} />
                </td>
              </tr>
            );
          });

          return {
            key: entity.apiName,
            label: entity.displayName,
            children: (
              <table className="synri-qs-install-review-table">
                <tbody>{fieldRows}</tbody>
              </table>
            ),
          };
        }
      );
      return <TreeSkeleton defaultOpen items={treeItems} />;

    case RenderInfoTypes.MERGED_PIPELINES:
      const mergeTreeItems = renderInfo.data.map(
        (entity): TreeItem => {
          const fieldRows = entity.fields.map((field) => {
            return (
              <tr key={field.id}>
                <td>
                  <FieldItem {...field} />
                </td>
                <td>
                  <TranslatedText color="gray-500" size="sm" text="source" />
                  <TranslatedText namespace="MergeOptions" color="gray-600" size="sm" text={field.source} />
                </td>
                <td>
                  <TranslatedText color="gray-500" size="sm" text="destination" />
                  <TranslatedText namespace="MergeOptions" color="gray-600" size="sm" text={field.destination} />
                </td>
              </tr>
            );
          });

          return {
            key: entity.apiName,
            label: entity.displayName,
            children: (
              <table className="synri-qs-install-review-table">
                <tbody>{fieldRows}</tbody>
              </table>
            ),
          };
        }
      );
      return <TreeSkeleton defaultOpen items={mergeTreeItems} />;

    default:
      throw new UnreachableCaseError(type);
  }
};

const QuickStartInstallReview = ({ items }: QuickStartInstallReviewProps) => {
  const reviewItems = useMemo(() => {
    return items.map((item) => {
      return {
        key: item.label,
        label: (
          <HStack spacing="xs">
            <StatBlock value={item.count} />
            <Text color="black">{item.label}</Text>
          </HStack>
        ),
        children: getRenderContent(item.renderInfo),
      };
    });
  }, [items]);

  return <TreeSkeleton defaultOpen items={reviewItems} borderOptions={borderOptions} expandIconsOffset={9} />;
};

export default withI18n(QuickStartInstallReview, 'QuickStart');
