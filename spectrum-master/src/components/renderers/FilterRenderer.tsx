import { conditionToString } from 'components/inputs/filter/utils';
import { LeftValue } from 'components/inputs/types';
import { FilterValue } from 'components/inputs/types';
import { Text, Truncation } from 'components/typography';
import { HTML_TAGS_REGEX } from 'utils/RegexUtil';
import { safeDecodeBase64 } from 'utils/StringUtil';

interface FilterCellProps {
  fieldValues: LeftValue[];
  filter: FilterValue;
}

const makeFilterCellRenderer = (fieldValues: FilterCellProps['fieldValues']) => (filter: string) => {
  try {
    const parsedFilter = JSON.parse(safeDecodeBase64(filter)) as FilterValue;
    const filterText = conditionToString(parsedFilter);

    return (
      <Truncation tooltipText={filterText.replace(HTML_TAGS_REGEX, '')} tooltipPlacement="topLeft">
        <Text beDangerous>{filterText}</Text>
      </Truncation>
    );
  } catch (err) {
    console.error(err);
    return null;
  }
};

export default makeFilterCellRenderer;
