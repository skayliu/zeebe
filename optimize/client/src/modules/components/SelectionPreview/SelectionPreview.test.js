/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {shallow} from 'enzyme';
import {Button} from '@carbon/react';

import SelectionPreview from './SelectionPreview';

it('should have an action button', () => {
  const node = shallow(<SelectionPreview />);

  expect(node.find(Button)).toExist();
});

it('should render child content', () => {
  const node = shallow(<SelectionPreview>Some child content</SelectionPreview>);

  expect(node.find('span')).toIncludeText('Some child content');
});

it('should add highlighted classname when highlighted property is set', () => {
  const node = shallow(<SelectionPreview highlighted />);

  expect(node).toHaveClassName('highlighted');
});

it('should call the onClick handler', () => {
  const spy = jest.fn();
  const node = shallow(<SelectionPreview onClick={spy}>Content</SelectionPreview>);

  node.find(Button).simulate('click');

  expect(spy).toHaveBeenCalled();
});

it('should pass the disabled prop to the child-button', () => {
  const node = shallow(<SelectionPreview disabled />);

  expect(node.find(Button)).toBeDisabled();
});
