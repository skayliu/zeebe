/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {getFormattedLabels, getBodyRows, getCombinedTableProps} from './service';
import {uniteResults} from '../service';

export default function processCombinedData({formatter, report}) {
  const {labels, reportsNames, combinedResult, instanceCount} = getCombinedTableProps(
    report.result.data,
    report.data.reports
  );

  const {
    configuration: {hideAbsoluteValue, hideRelativeValue},
  } = report.data;
  const {view} = Object.values(report.result.data)[0].data;

  const displayRelativeValue = view.property === 'frequency' && !hideRelativeValue;
  const displayAbsoluteValue = !hideAbsoluteValue;

  const keysLabel = labels[0][0];

  const formattedLabels = getFormattedLabels(
    labels,
    reportsNames,
    displayRelativeValue,
    displayAbsoluteValue
  );

  const flowNodeNames = {};
  // get all unique keys of results of multiple reports and build flowNodesNames hash
  const allKeys = Array.from(
    new Set(
      combinedResult.flat(2).map(({key, label}) => {
        flowNodeNames[key] = label;
        return key;
      })
    )
  );

  // make all hash tables look exactly the same by filling empty keys with empty string
  const unitedResults = uniteResults(combinedResult, allKeys);

  // convert hashtables into a table rows array
  const rows = getBodyRows(
    unitedResults,
    allKeys,
    formatter,
    displayRelativeValue,
    instanceCount,
    displayAbsoluteValue,
    flowNodeNames
  );

  return {
    head: [keysLabel, ...formattedLabels],
    body: rows,
  };
}
