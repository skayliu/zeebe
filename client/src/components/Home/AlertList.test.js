/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {EntityList} from 'components';
import {loadReports} from 'services';

import AlertListWithErrorHandling from './AlertList';
import AlertModal from './modals/AlertModal';
import {loadAlerts} from './service';
import {getWebhooks} from 'config';

const AlertList = AlertListWithErrorHandling.WrappedComponent;

jest.mock('services', () => {
  const rest = jest.requireActual('services');
  return {
    ...rest,
    loadReports: jest.fn().mockReturnValue([
      {id: '1', data: {visualization: 'table', view: {property: 'frequency'}}, name: 'Report 1'},
      {id: '2', data: {visualization: 'number', view: {property: 'duration'}}, name: 'Report 2'},
      {combined: true, id: '3', data: {visualization: 'number'}, name: 'Report 3'},
    ]),
  };
});

jest.mock('config', () => ({getWebhooks: jest.fn().mockReturnValue(['webhook1', 'webhook2'])}));
jest.mock('./service', () => ({
  loadAlerts: jest.fn().mockReturnValue([
    {
      id: 'alertID',
      email: 'test@hotmail.com',
      name: 'Some Alert',
      lastModifier: 'Admin',
      lastModified: '2017-11-11T11:11:11.1111+0200',
      reportId: '2',
      webhook: null,
    },
  ]),
  addAlert: jest.fn(),
  editAlert: jest.fn(),
  removeAlert: jest.fn(),
}));

const props = {
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
};

it('should load existing reports', () => {
  shallow(<AlertList {...props} />);

  expect(loadReports).toHaveBeenCalled();
});

it('should load existing webhooks', () => {
  shallow(<AlertList {...props} />);

  expect(getWebhooks).toHaveBeenCalled();
});

it('should only save single number reports', async () => {
  const node = shallow(<AlertList {...props} />);

  expect(node.state('reports').map((report) => report.id)).toEqual(['2']);
});

it('should format durations with value and unit', async () => {
  loadAlerts.mockReturnValueOnce([
    {
      id: 'a1',
      name: 'New Alert',
      email: 'test@hotmail.com',
      reportId: '2',
      thresholdOperator: '>',
      threshold: {
        value: '12',
        unit: 'seconds',
      },
      checkInterval: {
        value: '10',
        unit: 'minutes',
      },
      reminder: null,
      fixNotification: false,
    },
  ]);

  const node = shallow(<AlertList {...props} />);

  expect(node.find(EntityList).prop('data')[0].meta[1]).toContain('12s');
});

it('should set the loading', () => {
  const node = shallow(<AlertList {...props} />);

  node.setState({alerts: null}); // simulate missing response from alert query

  expect(node.find(EntityList).prop('isLoading')).toBe(true);
});

it('should load data', () => {
  shallow(<AlertList {...props} />);

  expect(loadReports).toHaveBeenCalled();
});

it('should show information about alerts', async () => {
  const node = shallow(<AlertList {...props} />);

  expect(node.find(EntityList).prop('data')[0].name).toBe('Some Alert');
});

it('should show create Alert button', () => {
  const node = shallow(<AlertList {...props} />);

  expect(node.find(EntityList).prop('action')).toMatchSnapshot();
});

it('should Alert to Deleter', async () => {
  const node = shallow(<AlertList {...props} />);

  node.find(EntityList).prop('data')[0].actions[1].action();

  expect(node.state('deleting')).toEqual({
    id: 'alertID',
    email: 'test@hotmail.com',
    name: 'Some Alert',
    lastModifier: 'Admin',
    lastModified: '2017-11-11T11:11:11.1111+0200',
    reportId: '2',
    webhook: null,
  });
});

it('should open a modal when editing an alert', async () => {
  const node = shallow(<AlertList {...props} />);

  node.find(EntityList).prop('data')[0].actions[0].action();

  expect(node.find(AlertModal)).toExist();
  expect(node.find(AlertModal).prop('initialAlert')).toEqual({
    id: 'alertID',
    email: 'test@hotmail.com',
    name: 'Some Alert',
    lastModifier: 'Admin',
    lastModified: '2017-11-11T11:11:11.1111+0200',
    reportId: '2',
    webhook: null,
  });
});

it('should show warning if alert is inactive due to missing webhoook', () => {
  getWebhooks.mockReturnValueOnce(['webhook1']);
  loadAlerts.mockReturnValueOnce([
    {
      id: 'alertID',
      email: '',
      name: 'Some Alert',
      lastModifier: 'Admin',
      lastModified: '2017-11-11T11:11:11.1111+0200',
      reportId: '2',
      webhook: 'nonExistingWebhook',
    },
  ]);
  const node = shallow(<AlertList {...props} />);

  expect(node.find('EntityList').prop('data')[0].warning).toBe('Alert inactive');
});
