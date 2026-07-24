import { Button, Icon, Upload } from 'antd';
import { RcFile } from 'antd/lib/upload';
import cx from 'classnames';
import { every, last } from 'lodash';
import { parse, ParseResult } from 'papaparse';
import { ComponentProps, useEffect, useState } from 'react';

import InputWithLabel from 'components/inputs/InputWithLabel';
import { HStack, Spacer, Stack } from 'components/layout';
import { Text } from 'components/typography';
import { EMPTY_ARRAY } from 'store/constants';
import { colors } from 'utils/LessConstants';

const { Dragger } = Upload;

type DraggerTypes = ComponentProps<typeof Upload.Dragger>;

// Removes any rows with only null values from the end of the array
export const filterEmptyCsvRows = (rowData: Record<string, any>[]) => {
  let hasEmptyRows = true;

  while (hasEmptyRows) {
    if (rowData.length === 0) {
      break;
    }
    const lastRow = last(rowData);
    const rowIsEmpty = every(lastRow, (value) => value === null);

    if (rowIsEmpty) {
      rowData.pop();
    } else {
      hasEmptyRows = false;
    }
  }

  return rowData;
};

export interface SingleFileUploadBoxProps extends Pick<DraggerTypes, 'onRemove' | 'beforeUpload'> {
  file?: RcFile;
  selectButtonText: string;
  helpText: string;
  className?: string;
  fileTypeRestriction?: string;
  showMetaData?: boolean;
  required?: boolean;
}

/**
 * An upload dragger component that's replaced with a read only single file
 * after a file is selected. The selected file has to be removed before
 * selecting a new file.
 */
const SingleFileUploadBox = ({
  selectButtonText,
  helpText,
  file,
  onRemove,
  beforeUpload,
  className,
  fileTypeRestriction,
  showMetaData,
  required = false,
}: SingleFileUploadBoxProps) => {
  const [fileMetaData, setFileMetaData] = useState({ rows: 0, columns: 0 });
  let content = (
    <Dragger
      {...{
        multiple: false,
        onRemove,
        beforeUpload,
        fileList: EMPTY_ARRAY,
        accept: fileTypeRestriction,
      }}>
      <p className="ant-upload-hint">Drag file here</p>
      <p className="ant-upload-hint">or</p>
      <Spacer y="md" />
      <Button>Select file</Button>
    </Dragger>
  );

  if (file) {
    // Simple solution for showing a readable view of the file size
    const size = file.size < 1000 ? file.size + ' bytes' : Math.round(file.size / 1000) + ' kb';

    content = (
      <HStack className="synri-custom-synapse-upload-box-file" justify="space-between">
        <HStack className="synri-custom-synapse-upload-box-content">
          <Icon type="file" style={{ fontSize: 18, color: colors.gray600 }} />
          <Stack spacing="xxs">
            <Text weight="semibold" color="gray-800">
              {file.name}
            </Text>
            {showMetaData ? (
              <Text color="gray-600">{`${fileMetaData.rows} rows, ${fileMetaData.columns} columns`}</Text>
            ) : (
              <Text color="gray-600">{size}</Text>
            )}
          </Stack>
        </HStack>
        <div className="synri-custom-synapse-file-remove" onClick={() => onRemove?.(file)}>
          <Icon type="close" style={{ fontSize: 16, color: colors.gray600 }} />
        </div>
      </HStack>
    );
  }

  useEffect(() => {
    if (file && showMetaData) {
      parse(file, {
        header: true,
        dynamicTyping: true,
        complete: (results: ParseResult<RcFile>) => {
          const columnCount = results.meta.fields?.length || 0;
          const rowCount = filterEmptyCsvRows(results.data).length;
          setFileMetaData({ columns: columnCount, rows: rowCount });
        },
      });
    }
  }, [file, showMetaData]);

  return (
    <InputWithLabel
      required={required}
      label={selectButtonText}
      tooltip={helpText}
      input={<div className={cx('synri-custom-synapse-upload-box-dragger', className)}>{content}</div>}
    />
  );
};

export default SingleFileUploadBox;
