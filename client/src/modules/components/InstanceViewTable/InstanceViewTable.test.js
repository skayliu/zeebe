/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {runLastEffect} from 'react';
import {shallow} from 'enzyme';

import {ReportRenderer} from 'components';
import {evaluateReport} from 'services';
import {useErrorHandling} from 'hooks';

import InstanceViewTable from './InstanceViewTable';

const props = {
  report: {data: {configuration: {xml: 'xml data'}}},
};

jest.mock('config', () => ({newReport: {new: {data: {configuration: {}}}}}));

jest.mock('services', () => ({
  ...jest.requireActual('services'),
  evaluateReport: jest.fn().mockReturnValue({}),
}));

jest.mock('hooks', () => ({
  useErrorHandling: jest.fn(() => ({
    mightFail: jest.fn((data, cb) => cb(data)),
  })),
}));

it('should contain ReportRenderer', () => {
  const node = shallow(<InstanceViewTable {...props} />);
  runLastEffect();
  expect(node.find(ReportRenderer)).toExist();
});

it('evaluate the raw data of the report on mount', () => {
  shallow(<InstanceViewTable {...props} />);
  runLastEffect();
  expect(evaluateReport).toHaveBeenCalledWith(
    {
      data: {
        configuration: {
          xml: 'xml data',
          sorting: {by: 'startDate', order: 'desc'},
        },
        groupBy: {type: 'none', value: null},
        view: {entity: null, properties: ['rawData']},
        visualization: 'table',
      },
    },
    [],
    undefined
  );
});

it('should pass the error to reportRenderer if evaluation fails', async () => {
  const testError = {message: 'testError', reportDefinition: {}, status: 400};
  useErrorHandling.mockImplementationOnce(() => ({
    mightFail: (promise, cb, err) => err(testError),
  }));

  const node = shallow(<InstanceViewTable {...props} />);
  runLastEffect();
  await flushPromises();

  expect(node.find(ReportRenderer).prop('error')).toEqual({status: 400, ...testError});
});

it('evaluate re-evaluate the report when called loadReport prop', () => {
  const node = shallow(<InstanceViewTable {...props} />);
  runLastEffect();

  const sortParams = {limit: '20', offset: 0};
  const report = {data: {configuration: {sorting: {by: 'startDate', order: 'asc'}}}};
  node.find(ReportRenderer).prop('loadReport')(sortParams, report);

  evaluateReport.mockClear();
  runLastEffect();

  expect(evaluateReport).toHaveBeenCalledWith(report, [], sortParams);
});
