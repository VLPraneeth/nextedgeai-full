import { Button, Tooltip } from 'antd';
import cx from 'classnames';

import { ReactComponent as PanIcon } from 'assets/icons/pan-cursor.svg';
import { ReactComponent as SelectIcon } from 'assets/icons/select-cursor.svg';
import { Text } from 'components/typography';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { setDragSelectMode } from 'store/pipeline/actions';
import { tNamespaced } from 'utils/i18nUtil';

import './GraphModeSwitcher.scss';

const tn = tNamespaced('GraphModeSwitcher');

export const GraphModeSwitcher = () => {
  const dispatch = useEnhancedDispatch();

  const { dragSelectMode } = useEnhancedSelector((state) => state.pipeline);

  const handleSelectToggle = () => {
    dispatch(setDragSelectMode(true));
  };

  const handlePanToggle = () => {
    dispatch(setDragSelectMode(false));
  };

  return (
    <div className="graph-mode-switcher">
      <Tooltip
        title={
          <>
            <Text>{tn('select_mode')}</Text>
            <br />
            <Text style={{ color: '#aab6be' }}>{tn('select_mode_help')}</Text>
          </>
        }
        placement="left"
        mouseEnterDelay={1}>
        {/*
          We want these buttons to only trigger on mouse click so we use
          onMouseUp here instead. Pressing spacebar while focused on a button
          triggers an onClick event, which causes issues with the spacebar
          event listner for toggling the graph mode between pan & select.
        */}
        <Button
          name="selectModeToggle"
          onMouseUp={handleSelectToggle}
          className={cx('graph-mode-switcher__button', dragSelectMode && 'graph-mode-switcher__button--selected')}>
          <SelectIcon
            className={cx('graph-mode-switcher__icon', dragSelectMode && 'graph-mode-switcher__icon--selected')}
          />
        </Button>
      </Tooltip>
      <Tooltip title={tn('pan_mode')} placement="left" mouseEnterDelay={1}>
        <Button
          name="panModeToggle"
          onMouseUp={handlePanToggle}
          className={cx('graph-mode-switcher__button', !dragSelectMode && 'graph-mode-switcher__button--selected')}>
          <PanIcon
            className={cx('graph-mode-switcher__icon', !dragSelectMode && 'graph-mode-switcher__icon--selected')}
          />
        </Button>
      </Tooltip>
    </div>
  );
};
