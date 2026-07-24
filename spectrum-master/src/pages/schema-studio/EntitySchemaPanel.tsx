import { Spin } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { connect, ConnectedProps } from 'react-redux';
import { bindActionCreators } from 'redux';

import DrawerPanel from 'components/DrawerPanel';
import Fieldset from 'components/Fieldset';
import InputWithLabel from 'components/inputs/InputWithLabel';
import PropertyPanelAction from 'components/PropertyPanelAction';
import { Status } from 'components/renderers/types';
import { ScrollableArea } from 'components/scrollable-area/ScrollableArea';
import { getEntityDetail } from 'store/schema/thunks';
import AppConstants from 'utils/AppConstants';
import { t, tNamespaced } from 'utils/i18nUtil';

import { RootState } from '../../reducers';

const tn = tNamespaced('EntitySchemaPanel');
const tnm = tNamespaced('EntitySchemaModal');
const INPUT_TYPE = AppConstants.INPUT_TYPE;

const connector = connect(
  (state: RootState) => ({
    getEntityDetailStatus: state.schema.getEntityDetailStatus,
    entityDetails: state.schema.entityDetails,
  }),
  (dispatch) =>
    bindActionCreators(
      {
        getEntityDetail,
      },
      dispatch
    )
);

interface EntitySchemaPanelProps {
  /**
   * Id of the entity that it is displaying
   */
  entityId?: string;
  /**
   * Handler to edit the entity
   */
  editEntity: (entityId: string) => void;
  /**
   * Callback when this panel is closed
   */
  onClose?: () => void;
  /**
   * Flag if we are viewing a syncari connnector
   */
  isSyncariConnector?: boolean;
}

type PanelPropsFromRedux = ConnectedProps<typeof connector>;

const EntitySchemaPanel = ({
  entityId,
  entityDetails,
  onClose,
  editEntity,
  getEntityDetailStatus,
  getEntityDetail,
  isSyncariConnector,
}: EntitySchemaPanelProps & PanelPropsFromRedux) => {
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    setVisible(!!entityId);
    if (!!entityId) {
      getEntityDetail(entityId);
    }
  }, [entityId, getEntityDetail]);

  // Only use entityDetails if it matches the current entityId to avoid showing stale data
  const currentEntityDetails = entityDetails?.id === entityId ? entityDetails : null;

  const close = () => {
    setVisible(false);
    onClose && onClose();
  };

  const disabledMessage = useMemo(() => {
    if (!isSyncariConnector) {
      return tn('synapse_cannot_modify') as string;
    } else if (currentEntityDetails?.draftStatus === Status.APPROVED) {
      return tn('approved_cannot_modify') as string;
    }
  }, [isSyncariConnector, currentEntityDetails]);

  return (
    <DrawerPanel onClose={close} title={currentEntityDetails?.displayName || ''} visible={visible} useLandingZone>
      <Spin spinning={getEntityDetailStatus === AppConstants.FETCH_STATUS.LOADING} tip={tn('loading_entity') as string}>
        <PropertyPanelAction
          actions={[
            {
              id: 'edit',
              name: tn('edit_entity'),
              icon: 'edit',
              // Disabled for approved and synapse entities
              disabled:
                !isSyncariConnector || (isSyncariConnector && currentEntityDetails?.draftStatus === Status.APPROVED),
              disabledMessage,
              handler: () => currentEntityDetails && editEntity(currentEntityDetails.id),
            },
          ]}
        />
        <Fieldset className="synri-entity-property-fieldset" title="Entity">
          <ScrollableArea>
            <InputWithLabel
              label={tnm('display_name')}
              datatype={INPUT_TYPE.STRING}
              value={currentEntityDetails?.displayName}
              disabled
            />
            <InputWithLabel label={tnm('api_name')} datatype="string" value={currentEntityDetails?.apiName} disabled />
            <InputWithLabel
              label={t('FieldSchemaModal.data_store_name')}
              datatype={INPUT_TYPE.STRING}
              value={currentEntityDetails?.dataStoreName}
              disabled
            />
            <InputWithLabel
              label={tnm('description')}
              datatype={INPUT_TYPE.TEXTAREA}
              value={currentEntityDetails?.description}
              disabled
            />
            <InputWithLabel
              label={tnm('tags')}
              datatype="tag"
              defaultValue={currentEntityDetails?.tags}
              disabled
              emptyPlaceholder={tn('no_tags')}
            />
            <InputWithLabel
              label={tn('run-dfi-label')}
              datatype="text"
              className="entity-rules-input"
              value={currentEntityDetails?.runDFI ?? false}
              disabled
            />
            <InputWithLabel
              label={tn('run-merge-label')}
              datatype="text"
              className="entity-rules-input"
              value={currentEntityDetails?.runMerge ?? false}
              disabled
            />
          </ScrollableArea>
        </Fieldset>
      </Spin>
    </DrawerPanel>
  );
};

export default connector(EntitySchemaPanel);
