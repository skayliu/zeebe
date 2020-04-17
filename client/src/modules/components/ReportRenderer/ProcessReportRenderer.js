/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {getFormatter, processResult} from './service';

import {Number, Table, Heatmap, Chart} from './visualizations';

export default class ProcessReportRenderer extends React.Component {
  render() {
    const {report} = this.props;
    const Component = this.getComponent();
    const props = {
      ...this.props,
      formatter: getFormatter(report.data.view.property),
      report: {...this.props.report, result: processResult(this.props.report)},
    };

    return (
      <div className="component">
        <Component {...props} />
      </div>
    );
  }

  getComponent = () => {
    switch (this.props.report.data.visualization) {
      case 'number':
        return Number;
      case 'table':
        return Table;
      case 'bar':
      case 'line':
      case 'pie':
        return Chart;
      case 'heat':
        return Heatmap;
      default:
        return;
    }
  };
}
