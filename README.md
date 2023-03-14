# Spikes

Some spike stuff in preparation of the final solution to the Note/Blog/Bookmark product that will be the backend for 
one or more Miruvor SaaS offerings.

## Event Sourced

Request -> Command -> Event -> Entity -> Response

1. Request is the instruction from the outside world to perform some task, but not yet a Command
2. After a Request us validated as suitable for a Command, it is transformed into a Command
3. An EventSourcedBehavior handles a Command and checks if the current State is suitable for the Command to be processed
4. If 3 is okay, then the Command is transformed into an Event, persisted and the State is updated accordingly
5. The Event and the State are used to update that State into a new State with the Entity from the Event
6. The requester from step 1 is informed about the result of steps 2..5 in the form of a http status code and the Response from the Entity.

Requests are either a **Create**, **Update** or **Delete** instruction with the required data.
Queries are defined later and return a response of the required entity or a list of these responses.

## Tech Stack

* Akka Typed
* [Akka http](https://doc.akka.io/docs/akka-http/current/index.html)
  * [Circe](https://circe.github.io/circe/) for http/json stuff via [Heiko's lib](https://github.com/hseeberger/akka-http-json)
* [Akka Typed persistence](https://doc.akka.io/docs/akka/current/typed/persistence.html) for Event Sourcing
  * [Kryo](https://github.com/altoo-ag/akka-kryo-serialization) for serialization
  * [Cassandra for Akka persistence](https://doc.akka.io/docs/akka-persistence-cassandra/current/index.html)
* [ULIDs](https://wvlet.org/airframe/docs/airframe-ulid) for unique, sortable IDs
* [Chimney](https://scalalandio.github.io/chimney/) for case class transformations
* [Scalactic](https://www.javadoc.io/doc/org.scalactic/scalactic_2.13/latest/org/scalactic/index.html) for triple equals with type safety  

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

### Infrastructure

* [DataStax Astra](https://astra.datastax.com/bbf920a2-9480-43f0-bdfb-ae682405943d)
* [MicroK8s](https://microk8s.io/)
* [Monitoring with Kamon](https://apm.kamon.io/)

### Inspiration

The initial idea was to build a simple note-taking backend. Gradually some new features crept in, such as Tasks, Events and Log/Journal entries. 
That scope-creep led to the idea of creating a simple CRM system, with Users logging their activities with Employees of Companies. 
This backend will focus on storing the data created by the users in a traceable, retrievable and recoverable way. The integration with email and
other external systems is not included yet.

* [etm](https://dagraham.github.io/etm-dgraham/)
* [UpBase](https://upbase.io/)
* [LinkDing](https://github.com/sissbruecker/linkding)
* [HighRise](https://highrisehq.com/)

### Development

* [GitHub project](https://github.com/jvorhauer/spikes)
* [sbt release](https://github.com/sbt/sbt-release)
* [sbt-jib](https://index.scala-lang.org/sbt-jib/sbt-jib) / [jib](https://github.com/GoogleContainerTools/jib/tree/master/jib-cli#supported-commands)
* [ScalaTest](https://www.scalatest.org/user_guide)

Advice is to create branch for each issue, work on that issue-branch and when finished PR. Some overhead, but more
focus and cleaner process. Also update this document when applicable.


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
