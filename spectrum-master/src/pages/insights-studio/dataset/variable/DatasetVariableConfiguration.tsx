import { Col, Icon, Row } from 'antd';
import Select, { OptionProps } from 'antd/lib/select';
import ObjectID from 'bson-objectid';
import React, { useCallback, useMemo } from 'react';
import { animated, useTransition } from 'react-spring';

import FieldTypeBadge, { DataTypeIcons } from 'components/FieldTypeBadge';
import { useI18nContext, withI18n } from 'components/I18nProvider';
import InfoBox from 'components/InfoBox';
import InputContainer from 'components/inputs/InputContainer';
import { Stack } from 'components/layout';
import Modal from 'components/Modal';
import { FieldDataType } from 'components/types';
import { TranslatedText } from 'components/typography';
import { useUnifiedDataCardAuthoring } from 'pages/insights-studio/utils/useUnifiedDataCardAuthoring';
import { DatasetVariable } from 'store/insights-studio/types';
import AppConstants from 'utils/AppConstants';
import { humanize } from 'utils/StringUtil';

import DatasetVariableValue from './DatasetVariableValue';

import './DatasetVariableConfiguration.less';

const { Option } = Select;

export interface DatasetVariableConfigurationProps {
  className?: string;
  formValues: any;
}

// Hand picked valid values for variable datatypes.
export const VariableDatatypes = {
  string: DataTypeIcons['string'],
  boolean: DataTypeIcons['boolean'],
  date: DataTypeIcons['date'],
  datetime: DataTypeIcons['datetime'],
  double: DataTypeIcons['double'],
  integer: DataTypeIcons['integer'],
  picklist: DataTypeIcons['picklist'],
  timestamp: DataTypeIcons['timestamp'],
};

const DatasetVariableConfiguration = ({ className, formValues }: DatasetVariableConfigurationProps) => {
  const { variables, setVariables } = useUnifiedDataCardAuthoring();
  const { t, tc, tn } = useI18nContext();

  const datatypeOptions = useMemo(() => {
    const options = Object.keys(VariableDatatypes).map((datatype) => (
      <Option value={datatype} key={datatype}>
        <div className="dataset-variable-configuration__datatype-option">
          <FieldTypeBadge dataType={datatype as FieldDataType} description={humanize(datatype)} disableTooltip />
          <span>{humanize(datatype)}</span>
        </div>
      </Option>
    ));

    return options;
  }, []);

  const filterOption = (input: string, option: React.ReactElement<OptionProps>) => {
    return option?.props?.value ? option.props.value.toString().toLowerCase().indexOf(input.toLowerCase()) >= 0 : false;
  };

  const deleteVariableHandler = useCallback(
    (variable: DatasetVariable) => {
      Modal.confirm({
        title: tn('delete_variable_title'),
        content: tn('delete_variable_description', { name: variable.displayName }),
        okText: tc('delete'),
        cancelText: tc('cancel'),
        onOk: () => {
          variable.apiName && variables && setVariables(variables.filter((vari) => vari.apiName !== variable.apiName));
        },
      });
    },
    [setVariables, tc, tn, variables]
  );

  const transitions = useTransition(variables, {
    keys: (item) => item?.apiName ?? ObjectID.generate(),
    from: { opacity: 0, height: 0 },
    enter: { opacity: 1, height: 'initial', marginBottom: 8 },
    leave: { opacity: 0, height: 0 },
    delay: 200,
  });

  if (!variables?.length) {
    return (
      <InfoBox
        message="You don't have any variables. You can make your dataset configurable by adding variables inside filters, groups and limits."
        type="info"
        showIcon
      />
    );
  }

  return (
    <>
      <Stack className="dataset-variable-configuration">
        <Stack>
          <Row key="header" gutter={8} type="flex" align="middle">
            <Col span={4}>
              <TranslatedText namespace="Common" weight="bold" text="display_name" />
            </Col>
            <Col span={4}>
              <TranslatedText namespace="Common" weight="bold" text="api_name" />
            </Col>
            <Col span={2}>
              <TranslatedText namespace="Common" weight="bold" text="required_text" />
            </Col>
            <Col span={2}>
              <TranslatedText namespace="Common" weight="bold" text="multi_value" />
            </Col>
            <Col span={4}>
              <TranslatedText namespace="Common" weight="bold" text="data_type" />
            </Col>
            <Col span={4}>
              <TranslatedText weight="bold" text="default_value" />
            </Col>
          </Row>

          {transitions((style, variable) => (
            <animated.div style={style}>
              {variable?.apiName && (
                <div>
                  <Row key={variable.apiName} gutter={8} type="flex" align="middle">
                    <Col span={4}>
                      <InputContainer
                        name="displayName"
                        datatype="string"
                        defaultValue={variable?.displayName}
                        onChange={(evt: React.ChangeEvent<HTMLInputElement>) => {
                          setVariables(
                            variables.map((config) => {
                              return {
                                ...config,
                                displayName:
                                  variable.apiName === config.apiName ? evt.target.value : config.displayName,
                              };
                            })
                          );
                        }}
                      />
                    </Col>
                    <Col span={4}>
                      <InputContainer name="apiName" datatype="string" disabled defaultValue={variable.apiName} />
                    </Col>
                    <Col span={2}>
                      <InputContainer
                        name="required"
                        datatype="checkbox"
                        defaultValue={variable.required}
                        onChange={(evt: React.ChangeEvent<HTMLInputElement>) => {
                          setVariables(
                            variables.map((config) => {
                              return {
                                ...config,

                                required: variable.apiName === config.apiName ? evt.target.checked : config.required,
                              };
                            })
                          );
                        }}
                      />
                    </Col>
                    <Col span={2}>
                      <InputContainer
                        name="multiValueField"
                        datatype="checkbox"
                        defaultValue={variable.multiValueField}
                        onChange={(evt: React.ChangeEvent<HTMLInputElement>) => {
                          setVariables(
                            variables.map((config) => {
                              return {
                                ...config,
                                multiValueField:
                                  variable.apiName === config.apiName ? evt.target.checked : config.multiValueField,
                              };
                            })
                          );
                        }}
                      />
                    </Col>
                    <Col span={4}>
                      <InputContainer
                        name="datatype"
                        datatype={AppConstants.INPUT_TYPE.PICKLIST}
                        defaultValue={variable.datatype}
                        options={datatypeOptions}
                        filterOption={filterOption}
                        onChange={(value: string) => {
                          setVariables(
                            variables.map((config) => {
                              const datatype = variable.apiName === config.apiName ? value : config.datatype;
                              return {
                                ...config,
                                datatype,
                                variableDefaultValue: {
                                  ...config.variableDefaultValue,
                                  datatype,
                                },
                              };
                            })
                          );
                        }}
                      />
                    </Col>
                    <Col span={4}>
                      <DatasetVariableValue
                        name={variable.apiName}
                        defaultValue={variable.variableDefaultValue}
                        multiValueField={variable.multiValueField}
                        placeholder={t('Dataset.VariablePicker.default_value_place_holder')}
                        onChange={(value) =>
                          setVariables(
                            variables.map((config) => ({
                              ...config,
                              variableDefaultValue:
                                variable.apiName === config.apiName ? value : config.variableDefaultValue,
                            }))
                          )
                        }
                      />
                    </Col>
                    <Col span={4}>
                      <div className="synri-delete-container">
                        <Icon
                          type="delete"
                          theme="filled"
                          onClick={() => {
                            deleteVariableHandler(variable);
                          }}
                        />
                      </div>
                    </Col>
                  </Row>
                </div>
              )}
            </animated.div>
          ))}
        </Stack>
      </Stack>
    </>
  );
};

export default withI18n(DatasetVariableConfiguration, 'Dataset.VariableConfiguration');
