<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# npm-update-dependencies Changelog

## [Unreleased]

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

[Unreleased]: https://github.com/WarningImHack3r/npm-update-dependencies/compare/v1.1.1...HEAD
[1.1.1]: https://github.com/WarningImHack3r/npm-update-dependencies/compare/v1.1.0...v1.1.1
[1.1.0]: https://github.com/WarningImHack3r/npm-update-dependencies/compare/v1.0.1...v1.1.0
[1.0.1]: https://github.com/WarningImHack3r/npm-update-dependencies/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/WarningImHack3r/npm-update-dependencies/commits/v1.0.0
