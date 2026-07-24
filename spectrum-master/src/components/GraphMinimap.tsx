//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Dropdown, Icon, Menu, Slider } from 'antd';
import { ClickParam } from 'antd/lib/menu';
import { SliderValue } from 'antd/lib/slider';
import cx from 'classnames';
import { useEffect, useMemo, useRef } from 'react';
import G6Editor from 'sg6-editor';

import { tc } from 'utils/i18nUtil';

import { zoomOptions } from './GraphEditor';
import { GraphModeSwitcher } from './GraphModeSwitcher';
import { GRAPH_MODE } from './GraphPage';

import './GraphMinimap.less';

const MINIMAP = {
  HEIGHT: 120,
  WIDTH: 200,
};

export interface GraphMiniMapProps {
  // TODO: Type SG Editor
  editor: any;
  currentZoom: number;
  changeZoom: (zoom: number) => void;
  minZoom: number;
  maxZoom: number;
  settings: React.ReactElement;
  graphMode: GRAPH_MODE;
  fitToScreen: () => void;
  hasToolbar?: boolean;
}

const GraphMinimap = ({
  editor,
  currentZoom,
  minZoom,
  maxZoom,
  changeZoom,
  settings,
  fitToScreen,
  graphMode,
  hasToolbar = false,
}: GraphMiniMapProps) => {
  let minimapContainer = useRef<HTMLDivElement>(null);
  const showModeSwitcher = graphMode === GRAPH_MODE.DEFAULT || graphMode === GRAPH_MODE.DRAG_SELECT;

  useEffect(() => {
    if (minimapContainer?.current && editor) {
      editor.add(
        new G6Editor.Minimap({
          container: minimapContainer.current,
          height: MINIMAP.HEIGHT,
          width: MINIMAP.WIDTH,
        })
      );
    }
    // Make sure to run only once otherwise the graph will freak out
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const sliderTipFormatter = (num: number) => {
    const zoom = Math.ceil(num * (maxZoom - minZoom) + minZoom * 100);
    return tc('percentage', { percent: zoom });
  };

  const sliderChange = (num: SliderValue) => {
    if (typeof num === 'number') {
      // Convert slider range 0-200 to graph allowed zoom level (Common graph min - max zoom value: 30% - 200%).
      changeZoom((num / 100) * (maxZoom - minZoom) + minZoom);
    }
  };

  const menu = useMemo(() => {
    return (
      <Menu
        onClick={(ev: ClickParam) => {
          const key = ev.item.props.eventKey;
          if (key === 'fitToScreen') {
            fitToScreen();
            changeZoom(1);
          } else {
            changeZoom(Number(key));
          }
        }}>
        {zoomOptions.map((zoom) => (
          <Menu.Item key={String(zoom / 100)}>{tc('percentage', { percent: String(zoom) })}</Menu.Item>
        ))}
        <Menu.Item key="fitToScreen">{tc('fit_to_screen')}</Menu.Item>
      </Menu>
    );
  }, [changeZoom, fitToScreen]);

  // Convert current zoom (Common graph min/max zoom value: 30% - 200%) to slider range of 0-200.
  const sliderValue = useMemo(() => ((currentZoom - minZoom) / (maxZoom - minZoom)) * 100, [
    currentZoom,
    maxZoom,
    minZoom,
  ]);

  return (
    <div className={cx('navigator-container', { 'with-settings': settings, 'has-toolbar': hasToolbar })}>
      <div className="navigator">
        <div className="minimap-container">
          {showModeSwitcher && <GraphModeSwitcher />}
          <div className="minimap" ref={minimapContainer} />
        </div>
        <div className="zoom-slider">
          <Slider
            value={sliderValue}
            className={cx('slider', showModeSwitcher && 'slider--sync-studio')}
            tipFormatter={sliderTipFormatter}
            onChange={sliderChange}
          />
          <Dropdown overlay={menu} trigger={['click']}>
            <a className="zoom-dropdown-btn">
              <span>{tc('percentage', { percent: Math.ceil(currentZoom * 100) })}</span>
              <Icon type="down" />
            </a>
          </Dropdown>
          {settings}
        </div>
      </div>
    </div>
  );
};

export default GraphMinimap;
