---
# These are optional elements. Feel free to remove any of them.
status: accepted
date: 2022-11-17
deciders: juvor
---
# Programming Language

## Context and Problem Statement

Before starting development a PL needs to be decided on.

## Decision Drivers

* Familiarity
* Availability
* Applicability

## Considered Options

* Java
* Kotlin
* Scala

## Decision Outcome

Scala

<!-- This is an optional element. Feel free to remove. -->
### Consequences

* Good: familiair, conciseness, correctness
* Bad: smaller community, resource usage on laptop

## More Information

Akka Persistence (see 002-event-source-framework) is written in Scala and most (example) projects using Akka
are written in Scala.
