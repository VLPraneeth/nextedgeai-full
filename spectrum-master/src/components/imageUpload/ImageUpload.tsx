//
// Copyright (c) 2019-Present Syncari All rights reserved.
//

import { Button, Icon } from 'antd';
import Upload, { UploadProps } from 'antd/lib/upload';
import { useState } from 'react';

import { withI18n } from 'components/I18nProvider';
import { HStack } from 'components/layout';
import { TranslatedText } from 'components/typography';
import './ImageUpload.less';

export interface ImageUploadProps {
  className?: string;
  onChange?: (file: File) => void;
  value?: string;
  defaultValue?: string;
  accept?: string[];
}

export const SUPPORTED_CUSTOM_SYNAPSE_ICON_FORMATS = ['.png', '.jpg', '.jpeg', '.svg'];

const ImageUpload = ({
  className,
  value,
  defaultValue = '',
  onChange,
  accept = ['.png', '.jpg', '.jpeg'],
}: ImageUploadProps) => {
  const [src, setSrc] = useState(defaultValue);
  const props = {
    accept: accept.join(','),
    listType: 'picture' as UploadProps['listType'],
    showUploadList: false,
    multiple: false,
    beforeUpload: (file: File) => {
      onChange?.(file);
      setSrc(URL.createObjectURL(file));
      return false;
    },
  };

  return (
    <HStack className="synri-image-upload">
      <div className="synri-image-upload-image-container">
        <img className="" src={src} alt="" />
      </div>
      <Upload {...props}>
        <Button>
          <Icon type="upload" /> <TranslatedText text="upload_image" />
        </Button>
      </Upload>
    </HStack>
  );
};

export default withI18n(ImageUpload, 'ImageUpload');
