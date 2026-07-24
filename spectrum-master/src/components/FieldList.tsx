//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Dropdown, Icon, Menu } from 'antd';
import Tooltip, { TooltipTrigger } from 'antd/lib/tooltip';
import cx from 'classnames';
import { map } from 'lodash';

import { ReactComponent as EditIcon } from 'assets/icons/edit-pencil.svg';
import { ReactComponent as UnmappedIcon } from 'assets/icons/field-unmapped.svg';
import { ReactComponent as KebabIcon } from 'assets/icons/kebab.svg';
import { FieldDataType } from 'components/types';
import { Text, Truncation, TruncationTooltipMode } from 'components/typography';
import { useEnhancedSelector } from 'hooks/redux';
import { usePipelineError } from 'pages/sync-studio/pipeline-error/PipelineError.hooks';
import { countValidationResultsByFieldId } from 'store/validation/utils';
import { t, tc, tNamespaced } from 'utils/i18nUtil';

import FieldTypeBadge from './FieldTypeBadge';

import './FieldList.less';

const tn = tNamespaced('FieldList');

export const FIELD_ACTIONS = {
  EDIT_DRAFT: 'edit_draft',
  CREATE_DRAFT: 'create_draft',
  DISCARD_DRAFT: 'discard_draft',
  MARK_READY: 'mark_ready',
  HIDE: 'hide',
  SHOW: 'show',
  MARK_NOT_READY: 'mark_not_ready',
  NAVIGATE: 'navigate',
};

// Not exported by antd, pasting it here for now
export type Align = {
  points?: [string, string];
  offset?: [number, number];
  targetOffset?: [number, number];
  overflow?: {
    adjustX?: boolean;
    adjustY?: boolean;
  };
  useCssRight?: boolean;
  useCssBottom?: boolean;
  useCssTransform?: boolean;
};

const fieldDropdownAlignment: Align = {
  offset: [-5, 3],
};

const fieldItemDropdownTrigger: TooltipTrigger = 'click';

export interface FieldItemProps {
  id: string;
  title: string;
  subtitle?: string;
  description: string;
  dataType: FieldDataType;
  hasDraft?: boolean;
  hasPublishedPipeline?: boolean;
  isMapped?: boolean;
  numberOfErrors?: number;
  numberOfWarnings?: number;
  ready?: boolean;
  hidden?: boolean;
  url?: string;
  onClick: (action: string) => void;
}

const FieldItem = ({
  id,
  title,
  subtitle,
  dataType,
  url = '',
  hasPublishedPipeline,
  hasDraft = false,
  isMapped = true,
  numberOfErrors = 0,
  numberOfWarnings = 0,
  ready = false,
  hidden = false,
  onClick,
}: FieldItemProps) => {
  // TODO: I18N, add Functionality to the menu for edit/view read-only
  const isDeleted = hasPublishedPipeline && !isMapped;

  const fieldMenu = (
    <Menu>
      {!isDeleted && (
        <Menu.Item onClick={() => onClick(hasDraft ? FIELD_ACTIONS.EDIT_DRAFT : FIELD_ACTIONS.CREATE_DRAFT)}>
          {tn(hasDraft ? 'edit_draft' : 'new_draft')}
        </Menu.Item>
      )}
      {isMapped && hasDraft && (
        <Menu.Item onClick={() => onClick(FIELD_ACTIONS.DISCARD_DRAFT)}>{tc('delete_draft')}</Menu.Item>
      )}
      {((isMapped && hasDraft) || isDeleted) && (
        <Menu.Item onClick={() => onClick(ready ? FIELD_ACTIONS.MARK_NOT_READY : FIELD_ACTIONS.MARK_READY)}>
          {tn(ready ? 'mark_not_ready' : 'mark_ready')}
        </Menu.Item>
      )}
      <Menu.Item onClick={() => onClick(hidden ? FIELD_ACTIONS.SHOW : FIELD_ACTIONS.HIDE)}>
        {tn(hidden ? 'unhide' : 'hide')}
      </Menu.Item>
    </Menu>
  );

  const hasErrorsOrWarnings = numberOfWarnings + numberOfErrors > 0;

  return (
    <li
      className={cx('field-item-container', {
        'field-pipeline-draft': hasDraft && !isDeleted && !hasErrorsOrWarnings,
        'field-pipeline-ready': ready && !hasErrorsOrWarnings,
        'field-pipeline-error': hasDraft && hasErrorsOrWarnings,
        'field-pipeline-deleted': hasPublishedPipeline && !isMapped,
      })}
      key={id}>
      <a
        className="field-item-link"
        href={url}
        onClick={(evt) => {
          evt.preventDefault();
          onClick(FIELD_ACTIONS.NAVIGATE);
        }}>
        <FieldTypeBadge dataType={dataType} />
        <Truncation
          className="field-item field-item-title"
          tooltipPlacement="left"
          tooltipDisplayMode={TruncationTooltipMode.ALWAYS}>
          <Text weight="bold">{title}</Text>
          {subtitle && <Text>{` (${subtitle})`}</Text>}
        </Truncation>
        {!isMapped && (
          <div className="unmapped-badge">
            <Tooltip title={tn('unmapped_field_tooltip')}>
              <UnmappedIcon role="img" aria-label={tn('unmapped_field_tooltip')} />
            </Tooltip>
          </div>
        )}
        {hasDraft && !ready && !isDeleted && (
          <div className="draft-badge">
            <EditIcon />
            {tn('draft')}
          </div>
        )}
        {ready && (
          <div className="ready-badge">
            <Icon type="check-circle" />
            {tn('ready')}
          </div>
        )}
        {numberOfErrors > 0 && (
          <div className="error-badge">{t('PipelineErrorState.count_error', { count: numberOfErrors })}</div>
        )}
        {numberOfWarnings > 0 && (
          <div className="warning-badge">{t('PipelineErrorState.count_warning', { count: numberOfWarnings })}</div>
        )}
        {isDeleted && <div className="error-badge">{tc('deleted')}</div>}
        {hidden && (
          <div className="hidden-badge">
            <Icon type="eye-invisible" />
          </div>
        )}
      </a>
      <Dropdown
        trigger={[fieldItemDropdownTrigger]}
        overlay={fieldMenu}
        overlayClassName="kebab-menu-dropdown"
        placement="bottomRight"
        align={fieldDropdownAlignment}>
        <div className="kebab-menu-trigger" role="button" aria-label={tn('kebab_aria_label')}>
          <KebabIcon />
        </div>
      </Dropdown>
    </li>
  );
};

type FieldListItem = {
  id: string;
  title?: string;
  subtitle?: string;
  dataType: FieldItemProps['dataType'];
  description: string;
  ready?: boolean;
  hasChanges?: boolean;
  hasPublishedPipeline?: boolean;
  isMapped?: boolean;
  hidden?: boolean;
  link?: string;
};

// TODO: add dynamic isMapped status to Fields
export interface FieldListProps<Item extends FieldListItem> {
  onFieldClick: (action: any, item: any) => void;
  items: Item[];
  className?: string;
}

const FieldList = <Item extends FieldListItem>({ onFieldClick, items, className }: FieldListProps<Item>) => {
  const { errors: validationErrors, warnings: validationWarnings } = useEnhancedSelector((state) => state.validation);

  const { errors: pipelineErrors, warnings: pipelineWarnings, isPipelineErrorVisible } = usePipelineError({});

  const makeClickHandler = (item: any) => {
    return (action: string) => onFieldClick(action, item);
  };

  return (
    <ul className={cx('field-list', className)}>
      {map(items, (item) => (
        <FieldItem
          key={item.id}
          id={item.id}
          title={item.title || ''}
          subtitle={item.subtitle}
          dataType={item.dataType}
          description={item.description}
          ready={item.ready}
          numberOfErrors={countValidationResultsByFieldId(
            isPipelineErrorVisible ? pipelineErrors : validationErrors,
            item.id
          )}
          numberOfWarnings={countValidationResultsByFieldId(
            isPipelineErrorVisible ? pipelineWarnings : validationWarnings,
            item.id
          )}
          // TODO: hasDraft should be updated to use draftStatus!=='APPROVED' once
          // SYN-2550 is completed
          hasDraft={item.hasChanges}
          hasPublishedPipeline={item.hasPublishedPipeline}
          isMapped={item.isMapped}
          hidden={item.hidden}
          url={item.link}
          onClick={makeClickHandler(item)}
        />
      ))}
    </ul>
  );
};

export default FieldList;
