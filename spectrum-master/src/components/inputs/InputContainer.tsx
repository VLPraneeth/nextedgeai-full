//
// Copyright (c) 2019-Present Syncari - All rights reserved.
// Container/Factory for the inputs
//
import { Input as AntInput, Checkbox, Switch } from 'antd';
import cx from 'classnames';
import { createElement, forwardRef } from 'react';

import ConfirmationInfoBox from 'components/ConfirmationInfoBox';
import ActionBody from 'components/custom-action/ActionBody';
import CustomActionReview from 'components/custom-action/ActionReview';
import ActionSetup from 'components/custom-action/ActionSetup';
import { Entity } from 'components/custom-synapse/http/entity/Entity';
import HTTPCustomSynapse from 'components/custom-synapse/http/HTTPCustomSynapse';
import { HTTPTest } from 'components/custom-synapse/http/HTTPTest';
import SDKCustomSynapse from 'components/custom-synapse/sdk/SDKCustomSynapse';
import WebhookCustomSynapse from 'components/custom-synapse/webhook/WebhookCustomSynapse';
import { ImageUpload } from 'components/imageUpload';
import InfoBox from 'components/InfoBox';
import AutoComplete from 'components/inputs/AutoComplete';
import Composite from 'components/inputs/composite';
import Condition from 'components/inputs/condition';
import Filter from 'components/inputs/filter';
import FilterInputComposite from 'components/inputs/filter/FilterInputComposite';
import { InstancePicker } from 'components/inputs/InstancePicker';
import MultiValueText from 'components/inputs/MultiValueText';
import ReferenceDataInput from 'components/inputs/ReferenceDataInput';
import ScheduleInput from 'components/inputs/schedule';
import Select from 'components/inputs/Select';
import Tag from 'components/inputs/Tag';
import TokenInput from 'components/inputs/tokens';
import { JumpToStepLabel } from 'components/JumpToStepLabel';
import { MergeOptionsSimple } from 'components/merge-options';
import { PipelinePicker, PipelinePickerPreview } from 'components/pipeline-picker';
import { QuickStartInstallResolveIssue } from 'components/quick-start-install-resolve-issue';
import { QuickStartInstallReview } from 'components/quick-start-install-review';
import { QuickStartInstallSchemaMatcher } from 'components/quick-start-install-schema-matcher';
import { QuickStartPostInstallation } from 'components/quick-start-post-installation';
import { RichTextInput } from 'components/rich-text-input';
import { SkullColumns } from 'components/skull-columns';
import SkullTable from 'components/skull-table/SkullTable';
import { SwitchCaseComposite } from 'components/switch-case/SwitchCaseComposite';
import { SwitchCaseCompositeInput } from 'components/switch-case/SwitchCaseCompositeInput';
import Token from 'components/Token';
import Text, { TextProps } from 'components/typography/Text';
import { useUserHasPermission } from 'hooks/useUserHasPermission';
import { DataSourceFieldPicker } from 'pages/insights-studio/dataset/configuration/DataSourceFieldPicker';
import { DatasetGroupFieldPicker } from 'pages/insights-studio/dataset/configuration/sections/DatasetGroupFieldPicker';
import { SelectedFieldPicker } from 'pages/insights-studio/dataset/configuration/SelectedFieldPicker';
import DatasetVariablesConfiguration from 'pages/insights-studio/dataset/variable/DatasetVariableConfiguration';
import DatasetVariablePicker from 'pages/insights-studio/dataset/variable/DatasetVariablePicker';
import { FieldMergePolicyRetainField } from 'pages/sync-studio/entity-pipeline/field-merge-policy-retain-field/FieldMergePolicyRetainField';
import { EMPTY_ARRAY } from 'store/constants';
import { useTokensForSelectedNode } from 'store/tokens/hooks';
import AppConstants from 'utils/AppConstants';
import { tCommon } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';

import Input from './Input';
import { isSingleTokenEligible } from './InputProxy/utils';
import MultiSelectField from './MultiSelectField';
import { Radio } from './Radio';
import SelectText from './select-text/SelectText';
import SetValueField from './SetValueField';
import { valueSupportsTokens } from './tokens/utils';
import { InputDataType } from './types';

export type InputContainerRef = HTMLElement;

export interface InputContainerProps {
  className?: string;
  datatype?: InputDataType;
  displayMode?: string;
  changeKey?: string;
  graphKey?: string;
  [key: string]: any;
}

const InputContainer = forwardRef<InputContainerRef, InputContainerProps>(
  ({ className, datatype, renderType, changeKey, graphKey, ...props }, ref) => {
    const { userHasPermission } = useUserHasPermission();
    const { displayMode } = props;

    let component: any;

    if (!props.key && (props.id || props.name)) {
      props.key = `input-container-${props.id || props.name}`;
    }

    switch (datatype) {
      case AppConstants.INPUT_TYPE.BOOLEAN:
        component = Switch;
        if (typeof props.value === 'boolean') {
          props.checked = props.value;
        }
        break;
      case AppConstants.INPUT_TYPE.CASE:
        component = SwitchCaseComposite;
        break;
      case AppConstants.INPUT_TYPE.CHECKBOX:
        component = Checkbox;

        // remove invalid prop
        if ('defaultChecked' in props) {
          delete props.defaultChecked;
        }
        props.checked = props.checked ?? props.defaultValue;

        break;

      case AppConstants.INPUT_TYPE.AUTOCOMPLETE:
      case AppConstants.INPUT_TYPE.PICKLIST_COMBO:
        component = AutoComplete;
        props.values = props.values || props.optionData || EMPTY_ARRAY;
        break;
      case AppConstants.INPUT_TYPE.INFOBOX:
        component = InfoBox;
        break;
      case AppConstants.INPUT_TYPE.CONFIRMATION_INFO_BOX:
        component = ConfirmationInfoBox;
        break;
      case AppConstants.INPUT_TYPE.PICKLIST:
      case AppConstants.INPUT_TYPE.REFERENCE:
      case AppConstants.INPUT_TYPE.MULTISELECT:
        props.disabled = props.disabled || displayMode === AppConstants.INPUT_DISPLAY_MODE.READONLY;
        props.displayMode = displayMode;

        // Note: Values here are option values not the
        // current value of the picklist
        if (props.values) {
          props.optionData = props.values;
        }
        props.placeholder = props.placeholder || '';
        props.dropdownMatchSelectWidth = false;
        component = Select;
        break;
      case AppConstants.INPUT_TYPE.MULTISELECT_FIELD:
        component = MultiSelectField;
        props.displayMode = displayMode;
        break;
      case AppConstants.INPUT_TYPE.RADIO:
        component = Radio;
        props.disabled = props.disabled || displayMode === AppConstants.INPUT_DISPLAY_MODE.READONLY;
        break;
      case AppConstants.INPUT_TYPE.CASE_PREDICATE:
        component = SwitchCaseCompositeInput;
        break;
      case AppConstants.INPUT_TYPE.STRING:
      case AppConstants.INPUT_TYPE.TEXT:
      case AppConstants.INPUT_TYPE.URL:
      case AppConstants.INPUT_TYPE.INTEGER:
      case AppConstants.INPUT_TYPE.DOUBLE:
        /* TODO: make integer/double use type='number' inputs? */
        props.autoComplete = AppConstants.INPUT_AUTOCOMPLETE_OPTIONS.OFF;
        props.displayMode = displayMode;
        component = Input;
        break;
      case AppConstants.INPUT_TYPE.PASSWORD:
        // we intentionally don't allow the user to see passwords
        // as some of these are auto filled with data that provides no value for the user
        props.autoComplete = AppConstants.INPUT_AUTOCOMPLETE_OPTIONS.OFF;
        component = AntInput.Password;

        break;
      case AppConstants.INPUT_TYPE.TEXTAREA:
        component = AntInput.TextArea;
        if (!props.rows) {
          props.rows = 4;
        }
        break;
      case AppConstants.INPUT_TYPE.DATE:
      case AppConstants.INPUT_TYPE.DATETIME:
        component = AntInput;
        break;
      case AppConstants.INPUT_TYPE.PREDICATE:
        component = Filter;
        props.displayMode = displayMode;
        props.changeKey = changeKey;
        props.fieldValues = props.fieldValues || props.values;
        break;
      case AppConstants.INPUT_TYPE.TAG:
        if (userHasPermission(AllPermissions.READ_TAG)) {
          component = Tag;
          props.disabled = props.disabled || displayMode === AppConstants.INPUT_DISPLAY_MODE.READONLY;
        } else {
          component = Text;
          props.children = tCommon('disabled_message');
          props.className = 'disabled-center-align';
        }
        break;
      case AppConstants.INPUT_TYPE.COMPOSITE:
        props.displayMode = displayMode;
        component = Composite;
        break;
      case AppConstants.INPUT_TYPE.CONDITION:
        props.displayMode = displayMode;
        component = Condition;
        break;
      case AppConstants.INPUT_TYPE.TABLE:
        props.displayMode = displayMode;
        component = SkullTable;
        break;
      case AppConstants.INPUT_TYPE.MULTIVALUETEXT:
        component = MultiValueText;
        props.displayMode = displayMode;
        break;
      case AppConstants.INPUT_TYPE.RICHTEXT:
        component = RichTextInput;
        props.displayMode = displayMode;
        break;
      case AppConstants.INPUT_TYPE.IMAGE:
        component = ImageUpload;
        break;
      case AppConstants.INPUT_TYPE.SELECT_TEXT:
        component = SelectText;
        props.disabled = props.disabled || displayMode === AppConstants.INPUT_DISPLAY_MODE.READONLY;
        break;
      case AppConstants.INPUT_TYPE.HTTP_TEST:
        component = HTTPTest;
        props.displayMode = displayMode;
        break;
      case AppConstants.INPUT_TYPE.REFERENCE_DATA:
        component = ReferenceDataInput;
        break;
      default:
        component = AntInput;
        break;
    }

    switch (renderType) {
      case AppConstants.SKULL_RENDER_TYPE.SET_VALUE_FIELD:
      case AppConstants.SKULL_RENDER_TYPE.SET_VALUE_FIELD1:
        props.isField = renderType === AppConstants.SKULL_RENDER_TYPE.SET_VALUE_FIELD1;
        component = SetValueField;
        break;
      case AppConstants.INPUT_RENDER_TYPE.DATASET_SELECTED_FIELD_PICKER:
        component = SelectedFieldPicker;
        break;
      case AppConstants.INPUT_RENDER_TYPE.TOKENS:
        if (valueSupportsTokens(props.value)) {
          props.displayMode = displayMode;
          if (
            isSingleTokenEligible(datatype) &&
            (props.readOnly || displayMode === AppConstants.INPUT_DISPLAY_MODE.READONLY)
          ) {
            component = ReadOnlyToken;
          } else {
            // Default showTokenSelector to true but allow override of false when
            // rendered inside a TokenizedFieldGroup
            props.showTokenSelector = props.showTokenSelector ?? true;
            component = TokenInput;
          }
        }
        break;
      case AppConstants.INPUT_RENDER_TYPE.SCHEDULE:
        component = ScheduleInput;
        break;
      case AppConstants.INPUT_RENDER_TYPE.COMPOSITE_INPUT:
        component = FilterInputComposite;
        props.displayMode = displayMode;
        break;
      case AppConstants.INPUT_RENDER_TYPE.FIELD_MERGE_POLICY_RETAIN_FIELD:
        props.displayMode = displayMode;
        component = FieldMergePolicyRetainField;
        break;
      case AppConstants.INPUT_RENDER_TYPE.DATA_SOURCE_FIELD_PICKER:
        component = DataSourceFieldPicker;
        break;
      case AppConstants.INPUT_RENDER_TYPE.DATASET_VARIABLE_PICKER:
        component = DatasetVariablePicker;
        props.displayMode = displayMode;
        break;
      case AppConstants.INPUT_RENDER_TYPE.DATASET_GROUP_FIELD_PICKER:
        component = DatasetGroupFieldPicker;
        break;
      // Skull Components
      case AppConstants.SKULL_RENDER_TYPE.DISPLAY_TEXT: {
        const { textProps } = props;
        return createElement(Text, textProps as TextProps);
      }
      case AppConstants.SKULL_RENDER_TYPE.INSTANCE_PICKER:
        component = InstancePicker;
        break;
      case AppConstants.SKULL_RENDER_TYPE.JUMP_TO_STEP_LABEL:
        return (
          <JumpToStepLabel
            text={props.text}
            stepNumber={props.stepNumber}
            buttonText={props.buttonText}
            navigateToStep={props.navigateToStep}
          />
        );
      case AppConstants.SKULL_RENDER_TYPE.PIPELINE_PICKER:
        component = PipelinePicker;
        break;
      case AppConstants.SKULL_RENDER_TYPE.PIPELINE_PICKER_PREVIEW:
        component = PipelinePickerPreview;
        break;
      case AppConstants.SKULL_RENDER_TYPE.QUICK_START_INSTALL_ERROR_RESOLUTION: {
        const { resolutionData, refreshStep, navigateToStep, onChange } = props;
        return (
          <QuickStartInstallResolveIssue
            refreshStep={refreshStep}
            navigateToStep={navigateToStep}
            onChange={onChange}
            {...resolutionData}
          />
        );
      }
      case AppConstants.SKULL_RENDER_TYPE.QUICK_START_INSTALL_REVIEW: {
        const { reviewItems } = props;
        return <QuickStartInstallReview items={reviewItems} />;
      }
      case AppConstants.SKULL_RENDER_TYPE.MERGE_OPTIONS:
        component = MergeOptionsSimple;
        break;
      case AppConstants.SKULL_RENDER_TYPE.QUICK_START_INSTALL_POST_INSTALLATION:
        return <QuickStartPostInstallation postInstallMessage={props.postInstallMessage} />;
      case AppConstants.SKULL_RENDER_TYPE.SCHEMA_MATCHER:
        component = QuickStartInstallSchemaMatcher;
        break;
      case AppConstants.SKULL_RENDER_TYPE.SKULL_COLUMNS:
        component = SkullColumns;
        break;
      case AppConstants.SKULL_RENDER_TYPE.ACTION_CONFIGURATION:
        component = ActionSetup;
        break;
      case AppConstants.SKULL_RENDER_TYPE.VARIABLES_CONFIGURATION:
        component = DatasetVariablesConfiguration;
        break;
      case AppConstants.SKULL_RENDER_TYPE.CUSTOM_ACTION_REVIEW:
        component = CustomActionReview;
        break;
      case AppConstants.SKULL_RENDER_TYPE.ACTION_BODY:
        component = ActionBody;
        props.displayMode = displayMode;
        break;
      case AppConstants.SKULL_RENDER_TYPE.SDK_CUSTOM_SYNAPSE:
        component = SDKCustomSynapse;
        props.displayMode = displayMode;
        break;
      case AppConstants.SKULL_RENDER_TYPE.HTTP_CUSTOM_SYNAPSE:
        component = HTTPCustomSynapse;
        props.displayMode = displayMode;
        break;
      case AppConstants.SKULL_RENDER_TYPE.HTTP_CUSTOM_SYNAPSE_ENTITY:
        component = Entity;
        props.displayMode = displayMode;
        break;
      case AppConstants.SKULL_RENDER_TYPE.WEBHOOK_CUSTOM_SYNAPSE:
        component = WebhookCustomSynapse;
        props.displayMode = displayMode;
        break;
    }

    return createElement(component, {
      className: cx('input-component', className),
      datatype,
      ref,
      ...props,
    });
  }
);

function ReadOnlyToken({ value }: { value: string }) {
  const { getToken } = useTokensForSelectedNode();
  const token = getToken(value);

  if (!token) {
    return null;
  }

  return <Token readOnly token={token} />;
}

export default InputContainer;
