//
// Copyright (c) 2019-Present Syncari All rights reserved.
//

import './ButtonImage.less';
import cx from 'classnames';

export interface ButtonImageProps {
  onClick: () => void;
  imageSrc: string;
  imageAlt: string;
}

const ButtonImage = ({ onClick, imageSrc, imageAlt }: ButtonImageProps) => {
  return <img className={cx('synri-button-image')} alt={imageAlt} onClick={onClick} src={imageSrc} />;
};

export default ButtonImage;
