# Retrospection

## Why?

This project is the backend for the frontend exam/assignment.

It replaces the existing [Noviaal](https://github.com/jvorhauer/noviaal) backend. The main differences are:

1. Scala iso Java
2. Akka Event Sourced iso Spring CRUD
3. State in memory
4. Graph DB for state iso relational db

The chosen chain of steps to implement Event Sourcing are:

```
json -> Request -> validate -> Command + State -> Event(s) -> persist -> State' -> Response
```

The `Command -> Event` step is the implementation of the Domain Driven Design (DDD) **Aggregate design** that models the flow of events through a domain model.
This also corresponds to the BiSL definition of Business Actions. Here the Action is the translation from Command + State to Event(s).

The events are persisted using Akka Persistence so that these events can be replayed for debugging or recovery. This is what makes Event Sourced so damn attractive:
fix bugs in how the State is composed and just restart the whole app; this will rebuild the State from the stored Events, but now with less bugs!

By using k8s I can also guarantee that there will be no downtime during an upgrade of the backend service(s).

## Why did it take so long to write a very basic CRUD backend for Users with Tasks?

A lot of reasons:

1. I can only work on this in my spare time, which isn't to abundant. Due to work and private circumstances I'm really happy when I get a couple of hourse every week to work on this
2. Getting the State implementation in place was quite a journey: Maps, relational db with Slick and finally TinkerPop + TinkerGraph.
3. Akka is great but requires a complete switch from what I use in my day-to-day job.

## Happy

Scala makes me happy!

Akka makes me happy!

Event Sourced makes me happy!

Working on this backend is the most satisfying work I'm doing. My work-job is boring and slow. This stuff is exiting and is moving as fast as I can manage.

One man team makes me happy!

## Rant

Just like the two loosers I used to work with who did everything with Spring + jBoss seam + relational databases on some j2ee application server I want to stick with 
this paradigm. Now that I'm used to being able to replay events to achieve a full recovery of the State of the application I don't want anything else. It just makes 
sense, it fits great with DDD, which is picking up some steam right now and it fits with functional style of programming: the events are truly immutable and Scala
is just functional enough to not bother but provide me with FP advantages.
