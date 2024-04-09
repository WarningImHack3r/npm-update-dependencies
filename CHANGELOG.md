<!-- Keep a Changelog guide -> https://keepachangelog.com -->
<!-- Types of changes memo:
— “Added” for new features.
— “Changed” for changes in existing functionality.
— “Deprecated” for soon-to-be removed features.
— “Removed” for now removed features.
— “Fixed” for any bug fixes.
— “Security” in case of vulnerabilities.
-->

# npm-update-dependencies Changelog

## [Unreleased]

## [2.1.5] - 2024-04-09

### Fixed

- Fix missing crash fix for newer versions (2024.1+) (#88)

## [2.1.4] - 2024-03-15

### Changed

- Improve caching introduced in 2.1.3

### Fixed

- Fix support for newer versions (2024.1+)

## [2.1.3] - 2024-02-01

### Added

- Add a button to create prefilled GitHub issues from crashes

### Fixed

- Fix support for multiple open projects/windows

## [2.1.2] - 2023-12-23

### Added

- Add progress indicator to the status bar widget for more granularity (#59)

### Changed

- Revert updates not being checked on deprecated packages
- Indent the status bar mode setting to make it clearer that it's a sub-setting

### Fixed

- Prevent concurrent updates or deprecations scans from running at the same time

## [2.1.1] - 2023-12-20

### Fixed

- Fix running the npm command on Windows

## [2.1.0] - 2023-12-15

### Added

- Add support for private packages (#27)

### Fixed

- Fix support for custom registries
- Fix a crash when parsing x-range versions (#51)
- Fix a potential error when fetching package info from npm registry

## [2.0.1] - 2023-10-24

### Changed

- Improve replacement package detection algorithm for deprecated dependencies

### Fixed

- Fix a rare crash due to an old dependency (#41)

## [2.0.0] - 2023-10-08

### Added

- Add support for 2023.3 IDEs

### Removed

- Remove support for 2021 IDEs

### Fixed

- Fix the deprecation banner displaying all actions, no matter if they were actually available or not

## [1.3.0] - 2023-05-12

### Added

- Automatically fetch package's NPM registry info on scan, adding support for custom registries (#7)

### Fixed

- Fix changelog for 1.2.0 release, visit [v1.2.0 release](https://github.com/WarningImHack3r/npm-update-dependencies/releases/tag/v1.2.0) for the actual changes

## [1.2.0] - 2023-04-23

### Added

- Add the option to remove a deprecated dependency
- Add a banner to notify of deprecated dependencies
- Add the ability to automatically reorder dependencies after replacing deprecated ones
- Add a status bar widget indicating the scan progress and the list of outdated and deprecated dependencies
- Add an option to auto-fix on save (applying the default update type and deprecation action)
  - A tooltip will be displayed when the file is modified by auto-fix
- Add settings (in `Settings > Tools`) related to the new features but also to:
  - Choose default update type
  - Choose default deprecation action
- Add keyboard shortcuts for menu actions

### Changed

- All write actions performed by the plugin now have a label in the undo stack
- Globally improve updates scanning engine
- Improve satisfying version detection

### Fixed

- Fix a bug where the plugin would prevent other annotators from working (#6)

## [1.1.2] - 2023-03-20

### Fixed

- Fix a rare crash occurring when scanning for updates

## [1.1.1] - 2023-03-11

### Fixed

- Fix updated dependencies still being marked as outdated

## [1.1.0] - 2023-03-10

### Added

- Add detection of deprecated dependencies, with an option to replace them by recommended ones
- Add an option to manually invalidate the cache (both for updates and deprecations)

### Changed

- **Rework the plugin's icon and asset**
- Redesign the menu group actions
- Drastically improve speed of scans
- Improve performance of batch updates (when using “Update all” buttons)

### Fixed

- Fix 'Update All (Satisfying)' button replacing versions with “null”
- Fix cache not always being used when it should
- Fix some typos

## [1.0.1] - 2023-03-06

### Added

- Enlarge plugin compatibility range

### Fixed

- Fix plugin crash when failing to fetch package info from npm registry
- Fix blocked UI when fetching package info from npm registry
- Fix satisfying version being the same as the current one

## [1.0.0] - 2023-02-20

### Added

- Initial release

[Unreleased]: https://github.com/WarningImHack3r/npm-update-dependencies/compare/v2.1.5...HEAD
[2.1.5]: https://github.com/WarningImHack3r/npm-update-dependencies/compare/v2.1.4...v2.1.5
[2.1.4]: https://github.com/WarningImHack3r/npm-update-dependencies/compare/v2.1.3...v2.1.4
[2.1.3]: https://github.com/WarningImHack3r/npm-update-dependencies/compare/v2.1.2...v2.1.3
[2.1.2]: https://github.com/WarningImHack3r/npm-update-dependencies/compare/v2.1.1...v2.1.2
[2.1.1]: https://github.com/WarningImHack3r/npm-update-dependencies/compare/v2.1.0...v2.1.1
[2.1.0]: https://github.com/WarningImHack3r/npm-update-dependencies/compare/v2.0.1...v2.1.0
[2.0.1]: https://github.com/WarningImHack3r/npm-update-dependencies/compare/v2.0.0...v2.0.1
[2.0.0]: https://github.com/WarningImHack3r/npm-update-dependencies/compare/v1.3.0...v2.0.0
[1.3.0]: https://github.com/WarningImHack3r/npm-update-dependencies/compare/v1.2.0...v1.3.0
[1.2.0]: https://github.com/WarningImHack3r/npm-update-dependencies/compare/v1.1.2...v1.2.0
[1.1.2]: https://github.com/WarningImHack3r/npm-update-dependencies/compare/v1.1.1...v1.1.2
[1.1.1]: https://github.com/WarningImHack3r/npm-update-dependencies/compare/v1.1.0...v1.1.1
[1.1.0]: https://github.com/WarningImHack3r/npm-update-dependencies/compare/v1.0.1...v1.1.0
[1.0.1]: https://github.com/WarningImHack3r/npm-update-dependencies/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/WarningImHack3r/npm-update-dependencies/commits/v1.0.0
