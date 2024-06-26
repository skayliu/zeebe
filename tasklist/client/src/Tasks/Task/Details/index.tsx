/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Section} from '@carbon/react';
import {TurnOnNotificationPermission} from './TurnOnNotificationPermission';
import {Aside} from './Aside';
import {Header} from './Header';
import styles from './styles.module.scss';
import {Outlet, useMatch} from 'react-router-dom';
import {CurrentUser, Task} from 'modules/types';
import {useCurrentUser} from 'modules/queries/useCurrentUser';
import {useTask} from 'modules/queries/useTask';
import {useTaskDetailsParams, pages} from 'modules/routing';
import {DetailsSkeleton} from './DetailsSkeleton';
import {TabListNav} from './TabListNav';

type OutletContext = [Task, CurrentUser, () => void];

const Details: React.FC = () => {
  const {id} = useTaskDetailsParams();
  const {data: task, refetch} = useTask(id, {
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  });
  const {data: currentUser} = useCurrentUser();
  const onAssignmentError = () => refetch();
  const tabs = [
    {
      key: 'task',
      title: 'Task',
      label: 'Show task',
      selected: useMatch(pages.taskDetails()) !== null,
      href: pages.taskDetails(id),
    },
  ];

  if (task === undefined || currentUser === undefined) {
    return <DetailsSkeleton data-testid="details-skeleton" />;
  }

  return (
    <div className={styles.container} data-testid="details-info">
      <Section className={styles.content} level={4}>
        <TurnOnNotificationPermission />
        <Header
          task={task}
          user={currentUser}
          onAssignmentError={onAssignmentError}
        />
        <TabListNav label="Task Details Navigation" items={tabs} />
        <Outlet
          context={[task, currentUser, refetch] satisfies OutletContext}
        />
      </Section>
      <Aside task={task} user={currentUser} />
    </div>
  );
};

export {Details as Component};
export type {OutletContext};
