@useoptic/ci-cli
===================



[![oclif](https://img.shields.io/badge/cli-oclif-brightgreen.svg)](https://oclif.io)
[![Version](https://img.shields.io/npm/v/@useoptic/ci-cli.svg)](https://npmjs.org/package/@useoptic/ci-cli)
[![Downloads/week](https://img.shields.io/npm/dw/@useoptic/ci-cli.svg)](https://npmjs.org/package/@useoptic/ci-cli)
[![License](https://img.shields.io/npm/l/@useoptic/ci-cli.svg)](https://github.com/opticdev/optic/blob/master/package.json)

<!-- toc -->
* [Usage](#usage)
* [Commands](#commands)
<!-- tocstop -->
# Usage
<!-- usage -->
```sh-session
$ npm install -g @useoptic/ci-cli
$ optic-ci COMMAND
running command...
$ optic-ci (-v|--version|version)
@useoptic/ci-cli/8.1.1 darwin-x64 node-v12.4.0
$ optic-ci --help [COMMAND]
USAGE
  $ optic-ci COMMAND
...
```
<!-- usagestop -->
# Commands
<!-- commands -->
* [`optic-ci capture:start`](#optic-ci-capturestart)
* [`optic-ci help [COMMAND]`](#optic-ci-help-command)

## `optic-ci capture:start`

describe the command here

```
USAGE
  $ optic-ci capture:start

OPTIONS
  --build-id=build-id            (required) a unique identifier representing the version of the code, build process, and
                                 build environment variables

  --config=config                (required) the path to your optic.yml file

  --deployment-id=deployment-id  (required) a unique identifier representing the version of the code, build process,
                                 build environment variables, and runtime environment variables

  --environment=environment      (required) the name of the environment you are deploying into

EXAMPLE
  $ optic-ci capture:start ???
```

_See code: [src/commands/capture/start.ts](https://github.com/useoptic/optic-package/blob/v8.1.1/src/commands/capture/start.ts)_

## `optic-ci help [COMMAND]`

display help for optic-ci

```
USAGE
  $ optic-ci help [COMMAND]

ARGUMENTS
  COMMAND  command to show help for

OPTIONS
  --all  see all commands in CLI
```

_See code: [@oclif/plugin-help](https://github.com/oclif/plugin-help/blob/v3.0.1/src/commands/help.ts)_
<!-- commandsstop -->
