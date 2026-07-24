import { Icon, message, Modal, Popover } from 'antd';
import { TooltipAlignConfig } from 'antd/lib/tooltip';
import cx from 'classnames';
import { useCallback, useEffect, useState } from 'react';

import { useEnhancedSelector } from 'hooks/redux';
import useScript from 'hooks/useScript';
import { get } from 'utils/AjaxUtil';
import DataUrlConstants from 'utils/DataUrlConstants';
import { tNamespaced } from 'utils/i18nUtil';
import { UserflowTags } from 'utils/UserflowTags';

import './HelpMenu.less';

const ZENDESK_API_KEY = process.env.REACT_APP_ZENDESK_API_KEY;
const ZENDESK_ATTRS = { id: 'ze-snippet' };

const BOTCO_AI_API_KEY = process.env.REACT_APP_BOTCO_AI_API_KEY;

const ts = tNamespaced('Support');
const WORKRAMP_ACADEMY_URL = process.env.REACT_APP_ACADEMY_URL || '/help';
const ZENDESK_URL = process.env.REACT_APP_ZENDESK_URL || '/help';

const BOTCO_AI_WIDGET_URL = 'https://widget.botco.ai/prod/latest/widget.js';

// wraper around zE to protect when it's not yet loaded
const ZE = (...args: any) => {
  if ((window as any).zE) {
    return (window as any).zE(...args);
  }

  console.warn('Zendesk widget not yet loaded.');
  return;
};

// Setup zendesk authentication
(window as any).zESettings = {
  webWidget: {
    position: {
      horizontal: 'right',
      vertical: 'bottom',
    },
    authenticate: {
      chat: {
        jwtFn(callback: (data: any) => void) {
          get(DataUrlConstants.GET_ZENDESK_JWT_TOKEN)
            .then((res) => {
              callback(res?.data);
            })
            .catch((error) => {
              console.error('Falied authenticating with zendesk: ', error);
            });
        },
      },
    },
  },
};

const PopoverMenu = ({ onRequestClose }: { onRequestClose: () => void }) => {
  const zendeskStatus = useScript(`https://static.zdassets.com/ekr/snippet.js?key=${ZENDESK_API_KEY}`, ZENDESK_ATTRS);
  const botcoAiStatus = useScript(BOTCO_AI_WIDGET_URL, { id: 'botcoai-widget' });
  const user = useEnhancedSelector((state) => state.user);
  const { email, id, firstName, lastName, currentInstanceNextEdgeId: instanceId } = user;
  const name = `${firstName} ${lastName}`;

  const openZendeskWidget = useCallback(() => {
    const currentState = ZE('webWidget:get', 'display');
    if (currentState === 'hidden' || currentState === 'launcher') {
      ZE('webWidget', 'open');
      ZE('webWidget', 'show');
      onRequestClose();
    }
  }, [onRequestClose]);

  // when the zendesk status updates AND it's successfully loaded,
  // send some commands to hide the webWidget and attach an event
  // listener for when the widget is closed - so we can hide it again
  useEffect(() => {
    if (zendeskStatus !== 'success') {
      return;
    }

    const hideZendeskWidget = () => {
      const isChatting = ZE('webWidget:get', 'chat:isChatting');
      // Show the launcher widget so the user can easily reopen the chat once
      // they've started chatting
      if (!isChatting) {
        ZE('webWidget', 'hide');
      }
    };

    // now that zendesk has loaded, zE is an object attached to window
    hideZendeskWidget();
    ZE('webWidget:on', 'close', hideZendeskWidget);
    ZE('webWidget:on', 'chat:unreadMessages', (unreadChatCount: number) => {
      if (unreadChatCount > 0) {
        openZendeskWidget();
      }
    });
  }, [zendeskStatus, openZendeskWidget]);

  const mountBotcoWebchat = useCallback(() => {
    try {
      const currentState = ZE('webWidget:get', 'display');
      if (currentState === 'launcher') {
        openZendeskWidget();
      } else if (!currentState || currentState === 'hidden') {
        if ((window as any).BotcoWebchat) {
          (window as any).BotcoWebchat.mount({
            apiKey: BOTCO_AI_API_KEY,
            open: true,
            closeOnClick: true,
            closeChatIcon: 'cross',
            attributes: {
              name,
              email,
              first_name: firstName,
              last_name: lastName,
              external_id: id,
              instance_id: instanceId,
            },
          });
        } else {
          Modal.error({
            title: ts('chat_support'),
            content: ts('chat_support_error'),
          });
        }
      }
    } catch (error) {
      // cross-origin errors can occur in dev environments
      message.error(ts('unable_to_load_chat'));
    }
    onRequestClose();
  }, [email, firstName, id, instanceId, lastName, name, onRequestClose, openZendeskWidget]);

  const menuItems = [
    <a href={ZENDESK_URL} target="_blank" rel="noopener noreferrer" onClick={onRequestClose}>
      {ts('help_docs')}
    </a>,
    botcoAiStatus !== 'error' && <button onClick={mountBotcoWebchat}>{ts('chat_support')}</button>,
    <a href={WORKRAMP_ACADEMY_URL} target="_blank" rel="noopener noreferrer" onClick={onRequestClose}>
      {ts('syncari_academy')}
    </a>,
  ].filter(Boolean);

  return (
    <div className="help-dropdown-menu">
      {menuItems.map((item, idx) => (
        <div className="help-dropdown-menu-item" key={idx}>
          {item}
        </div>
      ))}
    </div>
  );
};

const helpPopoverAlignment: TooltipAlignConfig = {
  offset: [15, 10],
};

const HelpMenu = () => {
  const [popoverOpen, setPopoverOpen] = useState(false);
  const closePopover = useCallback(() => setPopoverOpen(false), []);

  return (
    <Popover
      content={<PopoverMenu onRequestClose={closePopover} />}
      trigger="click"
      visible={popoverOpen}
      onVisibleChange={setPopoverOpen}
      overlayClassName="help-menu"
      placement="bottomLeft"
      align={helpPopoverAlignment}>
      <span className="help-menu header-menu-item" data-userflow-tag={UserflowTags.Header.HelpMenu}>
        <Icon type="question-circle" className={cx('header-icon', popoverOpen && 'active')} theme="filled" />
      </span>
    </Popover>
  );
};

export default HelpMenu;
