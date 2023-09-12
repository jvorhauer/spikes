# Spikes

Some spike stuff in preparation of the final solution to the Note product that will be the backend for
one or more Miruvor SaaS offerings.

[![CodeCov](https://codecov.io/gh/jvorhauer/spikes/branch/main/graph/badge.svg?token=YVnjWS1wc8)](https://codecov.io/gh/jvorhauer/spikes)
[![GitHub Actions](https://github.com/jvorhauer/spikes/actions/workflows/test.yaml/badge.svg)](https://github.com/jvorhauer/spikes/actions/workflows/test.yaml)

## Event Sourced

Is the way of working for Spikes. It's an extension of event driven:

```
Request -> Command -> Event -> Entity -> Response
```

1. Request is the instruction from the outside world to perform some task, but not yet a Command,
2. After a Request is validated as suitable for a Command, it is transformed into a Command and the callback actor is added,
3. An EventSourcedBehavior handles a Command and checks if the current State is suitable for the Command to be processed,
4. If 3 is okay, then the Command is transformed into an Event, persisted and the State is updated accordingly,
5. The Event and the State are used to update that State into a new State with the Entity from the Event,
6. The requester from step 1 is informed about the result of steps 2..5 in the form of a http status code and the Response from the Entity.

Requests are either a **Create**, **Update** or **Delete** instruction with the required data.
Queries are defined later and return a response of the required entity or a list of these responses.

### Architecture

From the above and with Akka Persistence we get an architecture that is basically a Handlers container with a Command Handler and 
an Event Handler:

```
request via akka-http-route -> 
  validator -> 
  command-handler ->
    event-handler &
    respond with updated state to requestor
```

to the outside world, this seems like the ubiquous http `request` -> `response` way of working.

Akka Persistence is responsible for persisting the events created by the command handler. And Akka persistence will replay the previously persisted events on
(re)startup of a Persistent Actor (actually Behaviour, but I still like Actor better).

The `validator` and the `reply to requestor` is the reason for the Request instead of the requestor just sending a Command. 

## Tech Stack

* [Akka Typed](https://doc.akka.io/docs/akka/current/typed/index.html)
* [Akka http](https://doc.akka.io/docs/akka-http/current/index.html)
  * [Circe](https://circe.github.io/circe/) for json processing via [Heiko's lib](https://github.com/hseeberger/akka-http-json)
* [Akka Typed persistence](https://doc.akka.io/docs/akka/current/typed/persistence.html) for Event Sourcing
  * [Kryo](https://github.com/altoo-ag/akka-kryo-serialization) for serialization
  * [Cassandra for Akka persistence](https://doc.akka.io/docs/akka-persistence-cassandra/current/index.html)
* [ULIDs](https://wvlet.org/airframe/docs/airframe-ulid) for unique, sortable IDs
* [TSID](https://github.com/vladmihalcea/hypersistence-tsid/tree/master): faster and less resource consuming replacement for ULID
* [Chimney](https://scalalandio.github.io/chimney/) for case class transformations
* [Scalactic](https://www.javadoc.io/doc/org.scalactic/scalactic_2.13/latest/org/scalactic/index.html) for triple equals with type safety
* [Scala URI](https://index.scala-lang.org/lemonlabsuk/scala-uri) to validate URLs

See [Implementing µ-services with Akka](https://developer.lightbend.com/docs/akka-guide/microservices-tutorial/index.html)

### Sources

* [Password regex (MKYong)](https://mkyong.com/regular-expressions/how-to-validate-password-with-regular-expression/)
* [Akka http Directives (ProgramCreek)](https://www.programcreek.com/scala/akka.http.scaladsl.server.Directive)
* [Akka http json (Heiko)](https://github.com/hseeberger/akka-http-json)
* [ScalaTest](https://www.scalatest.org/user_guide/selecting_a_style)
* [SBT jib](https://github.com/schmitch/sbt-jib)
* [Akka http tools (sbus labs)](https://github.com/sbuslab/akka-http-tools)
* [Akka http validation](https://github.com/Fruzenshtein/akka-http-validation)
* [Akka http metrics](https://index.scala-lang.org/rustedbones/akka-http-metrics)
* [Akka http OAuth2](https://www.jannikarndt.de/blog/2018/10/oauth2-akka-http/)
* [AirFrame ULID](https://wvlet.org/airframe/docs/airframe-ulid)
* [Circe and ULID](https://circe.github.io/circe/codecs/custom-codecs.html)
* [Complete Example](https://blog.rockthejvm.com/akka-cassandra-project/)
* [TSID](https://vladmihalcea.com/uuid-database-primary-key/); very interesting: both UUID and ULID are too long and slow, use TSID instead.

### Infrastructure

* [DataStax Astra](https://astra.datastax.com/bbf920a2-9480-43f0-bdfb-ae682405943d)
* [MicroK8s](https://microk8s.io/)
* [Monitoring with Kamon](https://apm.kamon.io/)

### Inspiration

The initial idea was to build a simple note-taking backend. Gradually some new features crept in, such as Tasks, Events and Log/Journal entries.
That scope-creep led to the idea of creating a simple CRM system, with Users logging their activities with Employees of Companies.
This backend will focus on storing the data created by the users in a traceable, retrievable and recoverable way. The integration with email and
other external systems is not included yet.

In the end I produced a simple note-taking backend. All aspirations for more functionality seem over the top at the moment. With the current implementation of Note all
the considered extra's can be implemented at the frontend.

* [etm](https://dagraham.github.io/etm-dgraham/)
* [UpBase](https://upbase.io/)
* [LinkDing](https://github.com/sissbruecker/linkding)
* [HighRise](https://highrisehq.com/)

### Development

* [GitHub project](https://github.com/jvorhauer/spikes)
* [sbt release](https://github.com/sbt/sbt-release)
* [sbt-jib](https://index.scala-lang.org/sbt-jib/sbt-jib) / [jib](https://github.com/GoogleContainerTools/jib/tree/master/jib-cli#supported-commands)
* [ScalaTest](https://www.scalatest.org/user_guide)
* [SCoverage](https://github.com/scoverage/sbt-scoverage) ==> [Report](target/scala-2.13/scoverage-report/index.html)

#### Test Coverage

![sunburst](https://codecov.io/gh/jvorhauer/spikes/branch/main/graphs/sunburst.svg?token=YVnjWS1wc8)


## k8s

### ConfigMap

```shell
kubectl create configmap name --from-literal=SECRET_KEY=$ENV_VAR --from-literal=OTHER_SECRET_KEY=$OTHER_ENV_VAR
```

see [k8s docs](https://kubernetes.io/docs/reference/generated/kubectl/kubectl-commands#-em-configmap-em-)

### Stuck namespaces

See [SO](https://stackoverflow.com/questions/52369247/namespace-stuck-as-terminating-how-i-removed-it)

### deploy to k8s

First, enable necessary k8s services: `dns`, [ingress](https://microk8s.io/docs/addon-ingress) and
[cert-manager](https://microk8s.io/docs/addon-cert-manager).

As I am using `microk8s` at the moment, this is as easy as

```shell
microk8s enable dns, ingress, cert-manager
```

on one of the k8s cluster nodes.

In folder `/k8s` in this project there are 5 YAML files.
Apply these as:
1. cluster-issuer.yaml
2. microbot-ingress.yaml
3. service.yaml
4. deployment.yaml
5. spikes-ingress.yaml


## Truth

The real reason I had to pick Cassandra as a database is that the Astra offering is extremely cool for small projects like this. it's been free for me due to very limited traffic can storage size. But Cassandra is not relational. Not that I like relational databases. Not at all. But that type of database is convenient as there is a lot of experience, including my own, with rdbms's and orms. I also do not like orms. So, what's a good solution that can use C*? Event Sourcing :-).

That is the real reason for choosing this rather exotic setup. But after a while I really love Event Sourcing!! It's unbeatable for growing a backend service. It's unbeatable for separating concerns and as a basis for growth.

Only thing I haven't figured out yet is scalability: I don't see how I can combine the advantages of ES with clustering. Clustering is a great method to guarantee uptime, for instance when a Kubernetes pod with one of the nodes of the app cluster goes down unexpectedly. Rolling updates is possible with one node, so that's covered by k8s.

Note that CQRS is never mentioned in this document. It is considered to be one of the pillars of Event Sourcing, but I don't see what's so great about it. So, no segregation yet. Maybe when there is an actual need. 
Also, I find a Single Place of Truth (SPoT) essential: having State of an actor at one place and the Read-side of CQRS at another is begging for disaster as soon as the programmer (me) forgets about updating logic in both places.
This is one of those instances where DRY is actually useful and good.

## ToDo

1. Comments, mainly in a Router
2. Tags (CRUD, tags cloud)
3. External QA and Performance tests

## Tips

### GitHub Container Repository (GHCR)

Retrieve list of tags for the Spikes image:

```
curl -H "Authorization: Bearer ${GHCR_TOKEN}" https://ghcr.io/v2/jvorhauer/spikes/tags/list
```

Use `sbt jibImageBuild` to create that image.

Use `git checkout tags/vM.M.P` to check out a specific tag before calling jib to create an image of a specific release tag.
