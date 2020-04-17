/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';
import LineChartConfig from './LineChartConfig';

const configuration = {
  showInstanceCount: false,
  color: '#1991c8',
  pointMarkers: true,
  hideRelativeValue: false,
  hideAbsoluteValue: false,
  xLabel: '',
  yLabel: '',
  targetValue: {active: false},
};

const lineReport = {
  combined: false,
  data: {visualization: 'line', view: {property: 'frequency'}, configuration},
};

it('it should display correct configuration for linechart', () => {
  const node = shallow(<LineChartConfig report={lineReport} />);
  expect(node).toMatchSnapshot();
});
