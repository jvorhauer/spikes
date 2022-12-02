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

Requests are either a Create, Update or Delete instruction with the required data.
Queries are defined later and return a response of the required entity or a list of these responses.

## Tech Stack

* Akka Typed
* Akka http
* Akka cluster
* Akka Typed persistence
* Cassandra adapter for Akka persistence
* Akka projections

See [Implementing µ-services with Akka](https://developer.lightbend.com/docs/akka-guide/microservices-tutorial/index.html)

### Sources

* [Password regex (MKYong)](https://mkyong.com/regular-expressions/how-to-validate-password-with-regular-expression/)
* [Akka http Directives (ProgramCreek)](https://www.programcreek.com/scala/akka.http.scaladsl.server.Directive)
* [Akka http json (Heiko)](https://github.com/hseeberger/akka-http-json)
* [ScalaTest](https://www.scalatest.org/user_guide/selecting_a_style)
* [Architecture Decision Records](https://adr.github.io/madr/) 
* [SBT jib](https://github.com/schmitch/sbt-jib)
* [Akka http tools (sbus labs)](https://github.com/sbuslab/akka-http-tools)
* [Akka http validation](https://github.com/Fruzenshtein/akka-http-validation)

### Inspiration

* [etm](https://dagraham.github.io/etm-dgraham/)
* [UpBase](https://upbase.io/)

## Model

The Model is the blueprint for the elements in the Application State.

A User has 0 or more Entries that can be one of 5 things.

An Entry can have Comments.

### User

```scala User class
case class User(
  id: UUID,
  name: String,
  email: String,
  password: String,
  born: LocalDate,
  joined: LocalDateTime,
  entries: Map[UUID, Entry]
)
```

### Entry 

Entry is either Note, Task, Marker (bookmark, favorite), Blog, Journal or Event

```scala Entry class
case class Entry(
  id: UUID,
  owner: UUID,
  created: LocalDatetime,
  title: String,
  body: String,
  status: Status,
  url: Option[String] = None,
  due: Option[LocalDateTime] = None,
  starts: Option[LocalDateTime] = None,
  ends: Option[LocalDateTime] = None
) {
  lazy val isMarker: Boolean = url.isDefined && due.isEmpty && starts.isEmpty && ends.isEmpty
  lazy val isTask: Boolean = due.isDefined
  lazy val isEvent: Boolean = starts.isDefined && ends.isDefined
  lazy val isJournal: Boolean = starts.isDefined && ends.isEmpty
}
```

with attributes to indicate type: 

* no url and no due date/time: Note
* url, but no due date/time: Marker
* due date/time: Task
* starts and ends date/time: Event
* starts, no ends: Journal

Converting from and to different types is just filling/setting one of the two optional determinators. This is actually a lot more
flexible than have a hierarchy of case classes, that each have one or two more fields than the base entry. A note becomes a reminder 
by simply setting the due date; a note becomes an event by adding start and end date/time, etc.
A Marker can be a Reminder or an Event, a Note can be all and is the base class.

An Entry is always owned by one User, identified by their UUID. 
A User can have 0 (zero) or more entries in a (Hash)Map of UUID -> Entry, 
where the UUID is the id of the Entry. That should help in looking up entries really fast...


### Status

An Entry has a Status:

* Entry:
  * Active
  * Archived
  * Deleted
* Task:
  * ToDo
  * Doing
  * Done

## Commands & Events

Request -> Command + State -> Event(s) -> State'

### User

* Create / Register User
* Login
* Logout
* Update Password
* Update User
* Delete User

### Entry

* Create
* Plan (set start & end)
* Remind (set due)
* Delete
* ToDoing (only Tasks)
* ToDone (only Tasks)
* ToToDo (only Tasks)

### Comment

Any User can add Comments to any Entry.

## Application State

Consists of
1. A Map[UUID, User] of all Users
2. A Map[UUID, Entry] of all Entries

The Application State, or just State from now on, is only available in memory.
The State is constructed of the initial, empty State with all Events until now applied to it.
