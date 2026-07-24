// @ts-nocheck
//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import ObjectID from 'bson-objectid';

import {
  detailsFixture,
  detailsFixtureNoData,
  detailsFixtureNoStageWithSyncCycle,
} from 'components/EntityDetails.fixtures';
import { withI18n } from 'components/I18nProvider';
import { render, screen } from 'tests/helpers';
import { tNamespaced } from 'utils/i18nUtil';

import { EntityDetailsContent as EntityDetailsContentOriginal } from '../EntityDetails';

const EntityDetailsContent = withI18n(EntityDetailsContentOriginal, 'SyncStudio');

const tn = tNamespaced('SyncStudio');

describe('EntityDetailsContent messaging', () => {
  test('Show "select an entityt" message when no entityId provided', async () => {
    render(<EntityDetailsContent />);

    expect(screen.getByText(tn('select_an_entity'))).toBeInTheDocument();
  });

  test('Show message of no details found when no metrics provided', async () => {
    render(<EntityDetailsContent entityId={ObjectID.generate()} />);

    expect(screen.getByText(tn('no_sync_details'))).toBeInTheDocument();
  });

  test('Show message of no details found when metrics have no stages and emptyLastSync is false', async () => {
    render(<EntityDetailsContent entityId={ObjectID.generate()} hasError metrics={detailsFixture} />);

    expect(screen.getByText(tn('no_sync_details'))).toBeInTheDocument();
  });

  test('Show message of no details found when metrics have no stages', async () => {
    render(<EntityDetailsContent entityId={ObjectID.generate()} metrics={detailsFixtureNoData} />);

    expect(screen.getByText(tn('no_sync_details'))).toBeInTheDocument();
  });

  test('Show loading message when loading is true and an entityId is provided', async () => {
    render(<EntityDetailsContent entityId={ObjectID.generate()} loading />);

    expect(screen.getByText(tn('loading_details'))).toBeInTheDocument();
  });

  test('Show last sync cycle when emptyLastSync is true and stages is null', async () => {
    render(<EntityDetailsContent entityId={ObjectID.generate()} metrics={detailsFixtureNoStageWithSyncCycle} />);

    expect(screen.getByText(tn('no_new_records'))).toBeInTheDocument();
  });

  test('Show "last sync cycle" message and "most recent with changes" message when when emptyLastSync is true and stages has values', async () => {
    render(<EntityDetailsContent entityId={ObjectID.generate()} metrics={detailsFixture} />);

    expect(screen.getByText(tn('no_new_records'))).toBeInTheDocument();
    expect(screen.getByText(tn('most_recent_with_changes'))).toBeInTheDocument();
  });

  test('Do not show "last sync cycle" message or "most recent with changes" message when when emptyLastSync is false and stages has values', async () => {
    render(
      <EntityDetailsContent entityId={ObjectID.generate()} metrics={{ ...detailsFixture, emptyLastSync: false }} />
    );

    expect(screen.queryByText(tn('select_an_entity'))).not.toBeInTheDocument();
    expect(screen.queryByText(tn('no_sync_details'))).not.toBeInTheDocument();
    expect(screen.queryByText(tn('no_new_records'))).not.toBeInTheDocument();
    expect(screen.queryByText(tn('most_recent_with_changes'))).not.toBeInTheDocument();
  });
});

describe('EntityDetailsContent stages', () => {
  const entityName = 'Account';

  test('Show the stage titles', async () => {
    render(<EntityDetailsContent entityId={ObjectID.generate()} entityName={entityName} metrics={detailsFixture} />);

    detailsFixture.allStages?.forEach((stage) => {
      expect(screen.getByText(stage.title)).toBeInTheDocument();
    });
  });
});
