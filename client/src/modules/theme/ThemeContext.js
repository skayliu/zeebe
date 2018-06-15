import React from 'react';
import {injectGlobal} from 'styled-components';

import {Colors} from './index';

const THEME_NAME = {
  LIGHT: 'light',
  DARK: 'dark'
};
// Create a context for the current theme
const ThemeContext = React.createContext();

// Wrapper that passes the theme as a prop
const ThemeConsumer = ThemeContext.Consumer;

// Top level component to pass down theme in the App
class ThemeProvider extends React.Component {
  constructor(props) {
    super(props);
    this.setBodyBackground();
  }

  // we start with the dark theme as default
  state = {theme: THEME_NAME.DARK, toggleTheme: this.toggleTheme};

  toggleTheme = () => {
    this.setState({
      theme:
        this.state.theme === THEME_NAME.DARK
          ? THEME_NAME.LIGHT
          : THEME_NAME.DARK
    });
  };

  setBodyBackground = () => {
    const {theme} = this.state;
    injectGlobal`
      body{
        background: ${
          theme === THEME_NAME.DARK ? Colors.uiDark01 : Colors.uiLight01
        }
      }
    `;
  };

  render() {
    return (
      <ThemeContext.Provider value={this.state}>
        {this.props.children}
      </ThemeContext.Provider>
    );
  }

  componentDidUpdate() {
    this.setBodyBackground();
  }
}

const themed = StyledComponent => {
  function Themed(props) {
    return (
      <ThemeConsumer>
        {({theme}) => <StyledComponent theme={theme} {...props} />}
      </ThemeConsumer>
    );
  }

  Themed.displayName = `Themed(${StyledComponent.displayName ||
    StyledComponent.name ||
    'Component'})`;

  return Themed;
};

const themeStyle = config => ({theme}) => config[theme];

export {ThemeConsumer, ThemeProvider, themed, themeStyle};
