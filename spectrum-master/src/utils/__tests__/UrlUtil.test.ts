// @ts-nocheck
//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { getRoute } from 'utils/UrlUtil';

const noop = () => {};

test('Test valid route translation', () => {
  const routeObj = {
    route: 'ENTITY_PIPELINE',
    entityId: '123-2345-3433',
  };
  const path = getRoute(routeObj);
  expect(path).toEqual('/sync-studio/entity/123-2345-3433/pipeline');
});

test('Test invalid route translation', () => {
  const conSpy = jest.spyOn(console, 'error').mockImplementation(noop);
  const routeObj = {
    route: 'ENTITY_PIPELINEX',
    entityId: '123-2345-3433',
  };
  const path = getRoute(routeObj);
  expect(path).toEqual(undefined);
  expect(conSpy).toHaveBeenCalledWith(`route key ${routeObj.route} not found`);
});

test('Test missing route key', () => {
  const conSpy = jest.spyOn(console, 'error').mockImplementation(noop);
  const routeObj = {
    entityId: '123-2345-3433',
  };
  const path = getRoute(routeObj);
  expect(path).toEqual(undefined);
  expect(conSpy).toHaveBeenCalledWith('Invalid passed route object');
});

test('Test blank route object', () => {
  const conSpy = jest.spyOn(console, 'error').mockImplementation(noop);
  const path = getRoute();
  expect(path).toEqual(undefined);
  expect(conSpy).toHaveBeenCalledWith('Invalid passed route object');
});
