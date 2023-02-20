# npm-update-dependencies

![Build](https://github.com/WarningImHack3r/npm-update-dependencies/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/com.github.warningimhack3r.npmupdatedependencies.svg)](https://plugins.jetbrains.com/plugin/com.github.warningimhack3r.npmupdatedependencies)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/com.github.warningimhack3r.npmupdatedependencies.svg)](https://plugins.jetbrains.com/plugin/com.github.warningimhack3r.npmupdatedependencies)

## Upcoming features
- [ ] Add update check progress in status bar
- [ ] Keyboard shortcuts

## Description
<!-- Plugin description -->
Update your npm dependencies with a single click.

This plugin will update all the dependencies in your package.json file to the latest version, or the satisfying version depending on your choice.

## Usage

There are 3 ways to update your dependencies:
- Hover over the dependency and click the update button:
    <img src="assets/popup.png" style="max-width: 400px">
    <img src="assets/popup-details.png" style="max-width: 400px">
- Right click in the package.json file and select the extension from the context menu:
    <img src="assets/right-click-menu.png" style="max-width: 400px">
- Use the Tools menu:
    <img src="assets/tools-menubar.png" style="max-width: 400px">

> Works by fetching [registry.npmjs.org](https://registry.npmjs.org).  
> Rewrite of the existing [npm-dependency-checker](https://github.com/unger1984/npm-dependency-checker) plugin.
<!-- Plugin description end -->

## Installation

- Using IDE built-in plugin system:
  
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "npm-update-dependencies"</kbd> >
  <kbd>Install Plugin</kbd>
  
- Manually:

  Download the [latest release](https://github.com/WarningImHack3r/npm-update-dependencies/releases/latest) and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>


---
Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template
