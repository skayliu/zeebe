/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import styles from './styles.module.scss';
import sharedStyles from 'modules/styles/panelHeader.module.scss';
import {Button} from '@carbon/react';
import {SidePanelOpen, SidePanelClose, Filter} from '@carbon/react/icons';
import cn from 'classnames';
import {useSearchParams} from 'react-router-dom';
import {type TaskFilters} from 'modules/hooks/useTaskFilters';
import {SearchParamNavLink} from './SearchParamNavLink';
import {prepareCustomFiltersParams} from 'modules/custom-filters/prepareCustomFiltersParams';
import {getStateLocally} from 'modules/utils/localStorage';
import difference from 'lodash/difference';
import {useCurrentUser} from 'modules/queries/useCurrentUser';

function getNavLinkSearchParam(options: {
  currentParams: URLSearchParams;
  filter: TaskFilters['filter'];
  userId: string;
}): string {
  const CUSTOM_FILTERS_PARAMS = [
    'state',
    'followUpDateFrom',
    'followUpDateTo',
    'dueDateFrom',
    'dueDateTo',
    'assigned',
    'assignee',
    'taskDefinitionId',
    'candidateGroup',
    'candidateUser',
    'processDefinitionKey',
    'processInstanceKey',
    'tenantIds',
    'taskVariables',
  ] as const;
  const {filter, userId, currentParams} = options;
  const {sortBy, ...convertedParams} = Object.fromEntries(
    currentParams.entries(),
  );
  const values: Record<string, string> =
    sortBy === 'completion' || sortBy === undefined
      ? {filter}
      : {filter, sortBy};
  if (filter === 'custom') {
    const customFilters = getStateLocally('customFilters')?.custom;
    const customFiltersParams =
      customFilters !== undefined
        ? prepareCustomFiltersParams(customFilters, userId)
        : {};
    const updatedParams =
      Object.keys(customFiltersParams).length > 0
        ? new URLSearchParams({
            ...convertedParams,
            ...values,
            ...customFiltersParams,
          })
        : new URLSearchParams({
            ...convertedParams,
            ...values,
          });
    const paramsToDelete = difference(
      CUSTOM_FILTERS_PARAMS,
      Object.keys(customFiltersParams),
    );

    paramsToDelete.forEach((param) => {
      updatedParams.delete(param);
    });

    return updatedParams.toString();
  }

  const updatedParams = new URLSearchParams({
    ...convertedParams,
    ...values,
  });

  if (filter === 'completed') {
    updatedParams.set('sortBy', 'completion');
  }

  CUSTOM_FILTERS_PARAMS.forEach((param) => {
    updatedParams.delete(param);
  });

  return updatedParams.toString();
}

const CollapsiblePanel: React.FC = () => {
  const [isCollapsed, setIsCollapsed] = useState(true);
  const [searchParams] = useSearchParams();
  const customFilters = getStateLocally('customFilters')?.custom;
  const {data} = useCurrentUser();
  const userId = data?.userId ?? '';

  if (isCollapsed) {
    return (
      <div className={cn(styles.base, styles.collapsedContainer)}>
        <Button
          hasIconOnly
          iconDescription="Expand to show filters"
          tooltipPosition="right"
          onClick={() => {
            setIsCollapsed(false);
          }}
          renderIcon={SidePanelOpen}
          size="md"
          kind="ghost"
        />
        <Button
          hasIconOnly
          iconDescription="Custom filter"
          tooltipPosition="right"
          onClick={() => {}}
          renderIcon={Filter}
          size="md"
          kind="ghost"
        />
      </div>
    );
  }

  return (
    <div className={cn(styles.base, styles.expandedContainer)}>
      <span className={cn(styles.header, sharedStyles.panelHeader)}>
        <h1>Filters</h1>
        <Button
          hasIconOnly
          iconDescription="Collapse "
          tooltipPosition="right"
          onClick={() => {
            setIsCollapsed(true);
          }}
          renderIcon={SidePanelClose}
          size="md"
          kind="ghost"
        />
      </span>
      <SearchParamNavLink
        to={{
          search: getNavLinkSearchParam({
            currentParams: searchParams,
            filter: 'all-open',
            userId,
          }),
        }}
        activeParam={{
          key: 'filter',
          value: 'all-open',
        }}
        isActiveOnEmpty
      >
        All open tasks
      </SearchParamNavLink>
      <SearchParamNavLink
        to={{
          search: getNavLinkSearchParam({
            currentParams: searchParams,
            filter: 'assigned-to-me',
            userId,
          }),
        }}
        activeParam={{
          key: 'filter',
          value: 'assigned-to-me',
        }}
      >
        Assigned to me
      </SearchParamNavLink>
      <SearchParamNavLink
        to={{
          search: getNavLinkSearchParam({
            currentParams: searchParams,
            filter: 'unassigned',
            userId,
          }),
        }}
        activeParam={{
          key: 'filter',
          value: 'unassigned',
        }}
      >
        Unassigned
      </SearchParamNavLink>
      <SearchParamNavLink
        to={{
          search: getNavLinkSearchParam({
            currentParams: searchParams,
            filter: 'completed',
            userId,
          }),
        }}
        activeParam={{
          key: 'filter',
          value: 'completed',
        }}
      >
        Completed
      </SearchParamNavLink>
      {customFilters === undefined ? null : (
        <SearchParamNavLink
          to={{
            search: getNavLinkSearchParam({
              currentParams: searchParams,
              filter: 'custom',
              userId,
            }),
          }}
          activeParam={{
            key: 'filter',
            value: 'custom',
          }}
        >
          Custom
        </SearchParamNavLink>
      )}
    </div>
  );
};

export {CollapsiblePanel};
