/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState} from 'react';

import PropTypes from 'prop-types';
import IncidentsBar from './IncidentsBar';
import IncidentsOverlay from './IncidentsOverlay';
import IncidentsTable from './IncidentsTable';

function IncidentsWrapper(props) {
  const {
    incidents,
    incidentsCount,
    instance,
    selectedIncidents,
    onIncidentOperation,
    onIncidentSelection
  } = props;
  const [isOpen, setIsOpen] = useState(false);
  function handleToggle() {
    setIsOpen(!isOpen);
  }

  return (
    <>
      <IncidentsBar
        id={instance.id}
        count={incidentsCount}
        onClick={handleToggle}
        isArrowFlipped={isOpen}
      />
      {isOpen && (
        <IncidentsOverlay>
          <IncidentsTable
            incidents={incidents}
            instanceId={instance.id}
            forceSpinner={props.forceSpinner}
            selectedIncidents={selectedIncidents}
            onIncidentOperation={onIncidentOperation}
            onIncidentSelection={onIncidentSelection}
          />
        </IncidentsOverlay>
      )}
    </>
  );
}

IncidentsWrapper.defaultProps = {
  incidents: PropTypes.array,
  incidentsCount: PropTypes.number.isRequired,
  instance: PropTypes.object.isRequired,
  onIncidentOperation: PropTypes.func.isRequired,
  forceSpinner: PropTypes.bool,
  selectedIncidents: PropTypes.array,
  onIncidentSelection: PropTypes.func.isRequired
};

export default IncidentsWrapper;
