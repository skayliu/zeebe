/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useEffect} from 'react';
import {Form, Field} from 'react-final-form';
import {useHistory} from 'react-router-dom';
import {isEqual} from 'lodash';
import {FiltersForm, Row, VariableRow} from './styled';
import {WorkflowField} from './WorkflowField';
import {WorkflowVersionField} from './WorkflowVersionField';
import {FlowNodeField} from './FlowNodeField';
import Textarea from 'modules/components/Textarea';
import {Input} from 'modules/components/Input';
import {CheckboxGroup} from './CheckboxGroup';
import Button from 'modules/components/Button';
import {AutoSubmit} from './AutoSubmit';
import {isFieldValid} from './isFieldValid';
import {Error, VariableError} from './Error';
import {
  submissionValidator,
  handleIdsFieldValidation,
  handleStartDateFieldValidation,
  handleEndDateFieldValidation,
  handleVariableValueFieldValidation,
  handleOperationIdFieldValidation,
} from './validators';
import {
  getFilters,
  updateFiltersSearchString,
  FiltersType,
} from 'modules/utils/filter';
import {storeStateLocally} from 'modules/utils/localStorage';

const Filters: React.FC = () => {
  const history = useHistory();
  const initialValues: FiltersType = {
    active: true,
    incidents: true,
  };

  function setFiltersToURL(filters: FiltersType) {
    history.push({
      ...history.location,
      search: updateFiltersSearchString(history.location.search, filters),
    });
  }

  useEffect(() => {
    storeStateLocally({
      filters: getFilters(history.location.search),
    });
  }, [history.location.search]);

  return (
    <Form<FiltersType>
      onSubmit={(values) => {
        const errors = submissionValidator(values);

        if (errors !== null) {
          return errors;
        }

        setFiltersToURL(values);
      }}
      initialValues={getFilters(history.location.search)}
    >
      {({handleSubmit, form, values}) => (
        <FiltersForm onSubmit={handleSubmit}>
          <AutoSubmit
            fieldsToSkipTimeout={[
              'workflow',
              'workflowVersion',
              'flowNodeId',
              'active',
              'incidents',
              'completed',
              'canceled',
            ]}
          />
          <Row>
            <WorkflowField />
          </Row>
          <Row>
            <WorkflowVersionField />
          </Row>
          <Row>
            <Field name="ids" validate={handleIdsFieldValidation}>
              {({input, meta}) => (
                <Textarea
                  {...input}
                  aria-invalid={!isFieldValid(meta)}
                  placeholder="Instance Id(s) separated by space or comma"
                />
              )}
            </Field>
          </Row>
          <Error name="ids" />
          <Row>
            <Field name="errorMessage">
              {({input}) => <Input {...input} placeholder="Error Message" />}
            </Field>
          </Row>
          <Row>
            <Field name="startDate" validate={handleStartDateFieldValidation}>
              {({input, meta}) => (
                <Input
                  {...input}
                  aria-invalid={!isFieldValid(meta)}
                  placeholder="Start Date YYYY-MM-DD hh:mm:ss"
                />
              )}
            </Field>
          </Row>
          <Error name="startDate" />
          <Row>
            <Field name="endDate" validate={handleEndDateFieldValidation}>
              {({input, meta}) => (
                <Input
                  {...input}
                  aria-invalid={!isFieldValid(meta)}
                  placeholder="End Date YYYY-MM-DD hh:mm:ss"
                />
              )}
            </Field>
          </Row>
          <Error name="endDate" />
          <Row>
            <FlowNodeField />
          </Row>
          <VariableRow>
            <Field name="variableName">
              {({input, meta}) => (
                <Input
                  {...input}
                  onChange={(event: React.ChangeEvent<HTMLInputElement>) => {
                    input.onChange(event);

                    if (event.target.value === '') {
                      form.submit();
                    }
                  }}
                  aria-invalid={!isFieldValid(meta)}
                  placeholder="Variable"
                />
              )}
            </Field>
            <Field
              name="variableValue"
              validate={handleVariableValueFieldValidation}
            >
              {({input, meta}) => (
                <Input
                  {...input}
                  onChange={(event: React.ChangeEvent<HTMLInputElement>) => {
                    input.onChange(event);

                    if (event.target.value === '') {
                      form.submit();
                    }
                  }}
                  aria-invalid={!isFieldValid(meta)}
                  placeholder="Value"
                />
              )}
            </Field>
          </VariableRow>
          <VariableError names={['variableName', 'variableValue']} />
          <Row>
            <Field
              name="operationId"
              validate={handleOperationIdFieldValidation}
            >
              {({input, meta}) => (
                <Input
                  {...input}
                  aria-invalid={!isFieldValid(meta)}
                  placeholder="Operation Id"
                />
              )}
            </Field>
          </Row>
          <Error name="operationId" />
          <Row>
            <CheckboxGroup
              groupLabel="Running Instances"
              items={[
                {
                  label: 'Active',
                  name: 'active',
                },
                {
                  label: 'Incidents',
                  name: 'incidents',
                },
              ]}
            />
          </Row>
          <Row>
            <CheckboxGroup
              groupLabel="Finished Instances"
              items={[
                {
                  label: 'Completed',
                  name: 'completed',
                },
                {
                  label: 'Canceled',
                  name: 'canceled',
                },
              ]}
            />
          </Row>
          <Row>
            <Button
              title="Reset Filters"
              size="small"
              disabled={isEqual(initialValues, values)}
              type="reset"
              onClick={() => {
                form.reset();
                setFiltersToURL(initialValues);
              }}
            >
              Reset Filters
            </Button>
          </Row>
        </FiltersForm>
      )}
    </Form>
  );
};

export {Filters};
