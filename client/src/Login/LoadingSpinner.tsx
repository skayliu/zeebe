/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Loading} from '@carbon/react';

const LoadingSpinner: React.FC<
  Omit<React.ComponentProps<typeof Loading>, 'withOverlay'>
> = (props) => {
  return <Loading withOverlay={false} {...props} />;
};

export {LoadingSpinner};
