[![GitHub license](https://img.shields.io/github/license/metaborg/jenkins.pipeline)](https://github.com/metaborg/gitonium/blob/master/LICENSE)
[![GitHub actions](https://img.shields.io/github/workflow/status/metaborg/jenkins.pipeline/Build?label=GitHub%20actions)](https://github.com/metaborg/common/actions/workflows/build.yml)
[![Jenkins](https://img.shields.io/jenkins/build/https/buildfarm.metaborg.org/job/metaborg/job/jenkins.pipeline/job/master?label=Jenkins)](https://buildfarm.metaborg.org/job/metaborg/job/jenkins.pipeline/job/master/lastBuild)

# Jenkins pipelines

Jenkins pipeline library for our buildfarm.

## Development

### Building

This repository is built with Gradle, which requires a JDK of at least version 8 to be installed. Higher versions may work depending on [which version of Gradle is used](https://docs.gradle.org/current/userguide/compatibility.html).

To build this repository, run `./gradlew buildAll` on Linux and macOS, or `gradlew buildAll` on Windows.

### Automated Builds

All branches and tags of this repository are built on:
- [GitHub actions](https://github.com/metaborg/jenkins.pipeline/actions/workflows/build.yml) via `.github/workflows/build.yml`.
- Our [Jenkins buildfarm](https://buildfarm.metaborg.org/view/Devenv/job/metaborg/job/jenkins.pipeline/) via `Jenkinsfile` which uses our [Jenkins pipeline library](https://github.com/metaborg/jenkins.pipeline/).

### Publishing

This repository is published automatically by our buildfarm.
Simply push commits to the `develop` branch to publish them.
New builds on our buildfarm always use the latest `develop` branch of this repository at the time the build starts.

## Copyright and License

Copyright © 2018-2021 Delft University of Technology

The files in this repository are licensed under the [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0).
You may use the files in this repository in compliance with the license.
