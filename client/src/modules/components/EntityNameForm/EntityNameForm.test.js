import React from 'react';
import {shallow} from 'enzyme';

import EntityNameForm from './EntityNameForm';
import {Input} from 'components';

it('should add isInvalid prop to the name input is name is empty', async () => {
  const node = await shallow(<EntityNameForm />);
  await node.instance().componentDidMount();

  await node.setState({
    name: ''
  });

  expect(node.find(Input).props()).toHaveProperty('isInvalid', true);
});

it('should provide name edit input', async () => {
  const node = await shallow(<EntityNameForm />);
  node.setState({name: 'test name'});

  expect(node.find(Input)).toBePresent();
});

it('should provide a link to view mode', async () => {
  const node = await shallow(<EntityNameForm />);

  expect(node.find('.save-button')).toBePresent();
  expect(node.find('.cancel-button')).toBePresent();
});

it('should invoke save on save button click', async () => {
  const spy = jest.fn();
  const node = await shallow(<EntityNameForm onSave={spy} />);
  node.setState({name: ''});

  node.find('.save-button').simulate('click');

  expect(spy).toHaveBeenCalled();
});

it('should disable save button if report name is empty', async () => {
  const node = await shallow(<EntityNameForm />);
  node.setState({name: ''});

  expect(node.find('.save-button')).toBeDisabled();
});

it('should update name on input change', async () => {
  const node = await shallow(<EntityNameForm />);
  node.setState({name: 'test name'});

  const input = 'asdf';
  node.find(Input).simulate('change', {target: {value: input}});
  expect(node.state().name).toBe(input);
});

it('should invoke cancel', async () => {
  const spy = jest.fn();
  const node = await shallow(<EntityNameForm onCancel={spy} />);

  await node.find('.cancel-button').simulate('click');
  expect(spy).toHaveBeenCalled();
});

it('should select the name input field if Report is just created', async () => {
  const node = await shallow(<EntityNameForm autofocus={true} />);

  const input = {focus: jest.fn(), select: jest.fn()};
  node.instance().inputRef(input);

  await node.instance().componentDidMount();

  expect(input.focus).toHaveBeenCalled();
  expect(input.select).toHaveBeenCalled();
});
