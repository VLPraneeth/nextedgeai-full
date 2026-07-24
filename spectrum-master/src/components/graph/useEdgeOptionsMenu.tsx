//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import Menu from 'antd/lib/menu';
import Text from 'antd/lib/typography/Text';
import { find, map } from 'lodash';

import { setNodeConfig } from 'actions/entityPipelineActions';
import { SWITCH_CASE_BUILT_IN_CASE } from 'components/switch-case/SwitchCase.contants';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { selectKebabMenuNode } from 'selectors/appSelectors';
import { selectAllPipelineFunctionsAsMap } from 'selectors/pipelineSelectors';
import { tc } from 'utils/i18nUtil';

export const EdgeType = {
  case: 'case',
};

const useEdgeOptionsMenu = () => {
  const kebabMenuNode = useEnhancedSelector(selectKebabMenuNode);
  const dispatch = useEnhancedDispatch();
  const pipelineFunctions = useEnhancedSelector(selectAllPipelineFunctionsAsMap);

  if (kebabMenuNode?.nodeType !== 'predicate-node') {
    return null;
  }

  const { node, sourceFunctionId } = kebabMenuNode;

  const sourceOptionsFunction = pipelineFunctions[sourceFunctionId];
  if (!sourceOptionsFunction) {
    return <div />;
  }

  const configuration = find(sourceOptionsFunction.configuration, { name: 'edgeOptions' });
  if (!configuration || !('options' in configuration)) {
    return <div />;
  }

  const addOptions: Record<string, string> =
    configuration?.edgeType === EdgeType.case
      ? {
          [SWITCH_CASE_BUILT_IN_CASE.DEFAULT]: tc('default'),
        }
      : {};

  let options = configuration.options;

  if (configuration.edgeType === EdgeType.case) {
    options = {};
    kebabMenuNode?.sourceConfiguration?.case?.cases?.forEach(({ caseName }) => {
      if (caseName) {
        options[caseName] = caseName;
        if (!addOptions[SWITCH_CASE_BUILT_IN_CASE.ANY]) {
          addOptions[SWITCH_CASE_BUILT_IN_CASE.ANY] = tc('any');
        }
      }
    });
  }

  return (
    <Menu
      style={{
        transform: 'scale(1.2)',
        minWidth: 90,
      }}
      onClick={(event) => {
        const baseNode = (node as any).metadata;
        const value = configuration.options[event.key] ?? event.key;

        dispatch(
          setNodeConfig({
            ...baseNode,
            configuration: { ...baseNode.configuration, value },
          })
        );
      }}>
      {map(options, (value, key) => {
        return (
          <Menu.Item key={key}>
            <Text>{key}</Text>
          </Menu.Item>
        );
      })}
      {map(addOptions, (value, key) => {
        return (
          <Menu.Item key={key}>
            <Text>{key}</Text>
          </Menu.Item>
        );
      })}
    </Menu>
  );
};

export default useEdgeOptionsMenu;
