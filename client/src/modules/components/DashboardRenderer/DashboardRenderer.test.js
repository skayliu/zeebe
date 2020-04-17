/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {shallow} from 'enzyme';

import DashboardRenderer from './DashboardRenderer';

const reports = [
  {
    position: {x: 0, y: 0},
    dimensions: {width: 3, height: 1},
    id: '1',
  },
  {
    position: {x: 2, y: 0},
    dimensions: {width: 1, height: 4},
    id: '2',
  },
  {
    position: {x: 3, y: 1},
    dimensions: {width: 2, height: 2},
    id: '3',
  },
];

it('should render a Dashboard Report for every Report in the props', () => {
  const node = shallow(<DashboardRenderer reports={reports} />);

  expect(node).toMatchSnapshot();
});
