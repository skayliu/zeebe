/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState, useEffect} from 'react';
import classnames from 'classnames';

import {Labeled} from 'components';
import {t} from 'translation';
import {formatters} from 'services';
import {areTenantsAvailable} from 'config';

import './TenantInfo.scss';

export default function TenantInfo({
  tenant,
  useCarbonVariant,
}: {
  tenant: {id: string; name?: string};
  useCarbonVariant?: boolean;
}): JSX.Element | null {
  const [tenantsAvailable, setTenantsAvailable] = useState(false);

  useEffect(() => {
    (async () => {
      setTenantsAvailable(await areTenantsAvailable());
    })();
  }, []);

  if (!tenantsAvailable) {
    return null;
  }

  return (
    <div className={classnames('TenantInfo', {useCarbonVariant})}>
      {useCarbonVariant ? (
        <div className="cds--label">{t('common.tenant.label')}</div>
      ) : (
        <Labeled label={t('common.tenant.label')} />
      )}
      <p className="tenantName">{formatters.formatTenantName(tenant)}</p>
    </div>
  );
}
