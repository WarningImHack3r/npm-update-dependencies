# npm-update-dependencies

![Build](https://github.com/WarningImHack3r/npm-update-dependencies/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/com.github.warningimhack3r.npmupdatedependencies.svg)](https://plugins.jetbrains.com/plugin/com.github.warningimhack3r.npmupdatedependencies)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/com.github.warningimhack3r.npmupdatedependencies.svg)](https://plugins.jetbrains.com/plugin/com.github.warningimhack3r.npmupdatedependencies)

## Description

<!-- Plugin description -->
Update your npm dependencies with a single click.

This plugin will update all the dependencies in your `package.json` file to the latest version, or the satisfying
version depending on your choice.

## Features

- Update a dependency to the latest or satisfying version
- Keep comparators (e.g. `^`, `~`, `>`, `<`, `>=`, `<=`) when replacing versions
- Get notified of deprecated dependencies with a banner
- Detect and replace/remove deprecated dependencies
- Configure and spot dependencies that are likely unmaintained
- Batch update all dependencies (latest or satisfying) and/or all deprecated dependencies
- Support for custom registries and private packages
- Support of the `packageManager` field
- See your outdated dependencies at a glance in the status bar
- Exclude dependencies or versions from the scan
- Configure everything in the settings
- Manually invalidate the cache in case of issues
- ...and more!

## Usage

There are 3 ways to invoke the extension menu:

- Hover over an annotated dependency and click the action you want to perform
- Right click in the package.json file and select the extension from the context menu
- Use the Tools menu

Configuration options are available in the settings.

> Works by fetching [registry.npmjs.org](https://registry.npmjs.org).  
> Rewrite of the existing [npm-dependency-checker](https://github.com/unger1984/npm-dependency-checker) plugin.
<!-- Plugin description end -->

## Installation

- Using IDE built-in plugin system:

  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "
  npm-update-dependencies"</kbd> >
  <kbd>Install Plugin</kbd>

- Manually:

  Download the [latest release](https://github.com/WarningImHack3r/npm-update-dependencies/releases/latest) and install
  it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

---
Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template
