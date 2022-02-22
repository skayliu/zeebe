/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {
  append as svgAppend,
  attr as svgAttr,
  create as svgCreate,
} from 'tiny-svg';

const OFFSET = 4;

function Outline(eventBus: DiagramJSEventBus) {
  eventBus.on('shape.added', (event) => {
    const element = event.element;
    const gfx = event.gfx;
    const outline = svgCreate('rect');

    svgAttr(outline, {
      fill: 'none',
      class: 'djs-outline',
      x: -OFFSET,
      y: -OFFSET,
      width: element.width + OFFSET * 2,
      height: element.height + OFFSET * 2,
    });

    svgAppend(gfx, outline);
  });
}

Outline.$inject = ['eventBus'];

const OutlineModule = {outline: ['type', Outline]};
export {OutlineModule};
