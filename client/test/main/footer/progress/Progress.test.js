import {expect} from 'chai';
import {mountTemplate, createMockComponent} from 'testHelpers';
import {jsx} from 'view-utils';
import sinon from 'sinon';
import {Progress, __set__, __ResetDependency__} from 'main/footer/progress/Progress';

describe('<Progress>', () => {
  let node;
  let update;
  let loadProgress;
  let Icon;
  let Tooltip;

  beforeEach(() => {
    loadProgress = sinon.spy();
    __set__('loadProgress', loadProgress);
    Icon = createMockComponent('Icon', true);
    __set__('Icon', Icon);
    Tooltip = createMockComponent('Tooltip', true);
    __set__('Tooltip', Tooltip);

    ({node, update} = mountTemplate(<Progress />));
  });

  afterEach(() => {
    __ResetDependency__('loadProgress');
    __ResetDependency__('Icon');
    __ResetDependency__('Tooltip');
  });

  it('should load progress', () => {
    expect(loadProgress.calledOnce).to.eql(true);
  });

  it('should show progres when it is below 100', () => {
    update({data: {progress: 10}});

    expect(node).to.contain.text('10%');
    expect(node).to.contain.text(Icon.text);
    expect(node).to.contain.text(Tooltip.text);
  });

  it('should create connected indicator', () => {
    update({
      data: {
        progress: 10,
        connectedToElasticsearch: true,
        connectedToEngine: true
      }
    });

    expect(Tooltip.getChildrenNode()).to.contain.text('Connected to engine and elastic search');
  });

  it('should create elasticsearch disconnected indicator', () => {
    update({
      data: {
        progress: 10,
        connectedToElasticsearch: false,
        connectedToEngine: true
      }
    });

    expect(Tooltip.getChildrenNode(1)).to.contain.text('Disconnected from elastic search');
  });

  it('should create engine disconnected indicator', () => {
    update({
      data: {
        progress: 10,
        connectedToElasticsearch: true,
        connectedToEngine: false
      }
    });

    expect(Tooltip.getChildrenNode(1)).to.contain.text('Disconnected from engine');
  });

  it('should create engine disconnected indicator', () => {
    update({
      data: {
        progress: 10,
        connectedToElasticsearch: false,
        connectedToEngine: false
      }
    });

    expect(Tooltip.getChildrenNode(1)).to.contain.text('Disconnected from elastic search and engine');
  });

  it('should not show progres when it is 100', () => {
    update({data: {progress: 100}});

    expect(node.innerText.trim()).to.eql('');
  });

  it('should not show import by default', () => {
    expect(node.innerText.trim()).to.eql('');
  });
});
