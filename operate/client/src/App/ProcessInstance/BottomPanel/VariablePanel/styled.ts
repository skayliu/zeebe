/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {AddVariableButton as BaseAddVariableButton} from '../Variables/Footer/AddVariableButton';

const EmptyMessageContainer = styled.div`
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
`;

const Content = styled.div`
  position: relative;
  height: 100%;
  .cds--loading-overlay {
    position: absolute;
  }
`;

const AddVariableButton = styled(BaseAddVariableButton)`
  align-self: flex-end;
`;

const Form = styled.form`
  height: 100%;
  display: flex;
  flex-direction: column;
`;

const VariablesContainer = styled.div`
  height: 100%;
  position: relative;
`;

export {
  Content,
  EmptyMessageContainer,
  AddVariableButton,
  Form,
  VariablesContainer,
};
