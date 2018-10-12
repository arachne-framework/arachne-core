<?prefix :arachne.*=urn:arachne: ?>

<?about http://arachne-framework.org/name/arachne-core ?>

The Arachne Core module:

1. Provides APIs for creating and manipulating [descriptors](<?link :arachne.doc.topic/descriptor ?>).
2. Defines the foundational concepts upon which everything else is
built.
3. Provides implementations for basic low level modules and components,
   which can be built upon by other modules..
4. Implements the bootstrap sequence.

Some important concepts defined by the Arachne module include:

- [Modules](<?link :arachne/Module ?>)
- [Runtimes](<?link :arachne/Runtime ?>)
- [Components](<?link :arachne/Component ?>)

<?about :arachne.doc.topic/descriptor ?>

A descriptor is a RDF graph describing an application, its components
and their configuration in detail.

<?about :arachne/Module ?>

A Module is an Arachne-aware library. Modules are packaged as standard
Clojure/JVM artifacts, and can be delivered via Maven or Clojure git
deps. Typically, modules include Clojure code, data to add to the
descriptor, and hooks to manipulate the descriptor at configuration
time.

Modules are defined by an `arachne.edn` file on the root of the JVM
classpath. This file should be an RDF/EDN file and defines the
properties of the module, such as its
[dependencies](<?link :arachne.module/dependencies ?>),
[initializers](<?link :arachne.module/initializers ?>) and
[configure functions](<?link :arachne.module/configure ?>).

An Arachne application is itself a module, with a `arachne.edn`
file. For information on how modules are loaded, see the
[bootstrap process](?link :arachne.doc.topic/bootstrap ?>).

<?about :arachne.module/dependencies ?>

Modules upon which this module depends.

<?about :arachne.module/configure ?>

Configure functions associated with a module. Each configure function
takes a descriptor as its only argument, and may update the descriptor.

<?about :arachne.module/include ?>

Indicates additional RDF data to include during descriptor
initialization. Values can be one fo two types:

1. Strings are interpreted as classpath-relative paths of RDF files
   (in any RDF format that Aristotle supports.)
2. Vars refer to either a Clojure var containing RDF/EDN data, or a
   no-arg function that returns RDF/EDN data when invoked. In either
   case, the data will be added to the descriptor.

<?about :arachne.doc.topic/bootstrap ?>

Initializing an Arachne application has three steps:

1. Create a [descriptor](?link :arachne.doc.topic/descriptor ?>)
   based on a [module](<?link :arachne/Module ?>) and its dependencies.
2. Create a instance of an Arachne [runtime](<?link :arachne/Runtime ?>),
   from the descriptor.
3. Call the `com.stuartsierra.component/start` lifecycle function on
   the runtime to start the application.

### Creating a Descriptor

To initialize an application, call the `arachne.core/descriptor`
function, passing the IRI of the application module. This will
construct a descriptor out of defined modules, using the following
steps:

1. Creates an empty descriptor (RDF graph.)
2. Discovers all the `arachne.edn` files on the classpath, adding
   their contents to the descriptor. There are now RDF entities in
   the descriptor for each module and its properties.
3. Forms a directed acyclic graph of modules and their
   dependencies, starting from the module passed to the
   `descriptor` function.
4. For each module, add RDF data to the descriptor as specified by
   each module's [initializers](<?link :arachne.module/initializers ?>).
   This can include OWL Schema to define concepts introduced
   by the module.
5. In dependency order (that is, most basic dependencies first),
   call each module's [configure functions](<?link :arachne.module/configure ?>).
   These give each module a chance to alter the descriptor further,
   based on available data.
6. Validate the descriptor for internal consistency and adherence
   to all defined schema.

### Creating a Runtime

To create a runtime, pass a descriptor and the IRI of a
[runtime](<?link :arachne/Runtime ?>) to the `arachne.core/runtime`
function. This function will:

1. Find the corresponding Runtime entity in the descriptor.
2. Load a graph of the Runtime's [components](<?link :arachne/Component ?>)
   and their dependencies.
3. Instantiate each component, and wire them together with their
   dependencies under the specified keys.

The resulting runtime object satisfies the
`com.stuartsierra.component/Lifecycle` protocol and is ready to be
started.

<?about :arachne/Runtime ?>

A Runtime is an entity representing a process or application that can
be started and stopped. Such processes might be a web server, a data
processing task, or a devops deployment operation.

An Arachne descriptor can contain any number of Runtime entities, and
they may be instantiated, started or stopped independently via the
`arachne.core/runtime` and component lifecycle functions.

Each runtime is associated with one or more [components](<?link :arachne/Component ?>)
via the <?ref :arachne.runtime/components ?> property. These represent
the components comprising the runtime and they, along with their dependencies,
will be instantiated and have their dependencies injected
when the runtime is instantiated, and be started in the
appropriate order when the runtime is started.

<?about :arachne.runtime/components ?>

The root components associated with the runtime. These components (and
their dependencies) will be instantiated when the runtime is
instantiated, and started when it starts.

<?about :arachne/Component ?>

A stateful entity that can be started and stopped. In the descriptor,
may refer to other components as its dependencies and may in turn be
depended upon. After instantiation, dependent components will be
assoced under the specified keys.

An instantiated component may be any type of object -- components
which have dependencies are required to support Clojure `assoc` (i.e,
be maps or records.)

Component instances that satisfy the
`com.stuartsierra.component/Lifecycle` protocol will have their
`start` and `stop` methods invoked appropriately.

<?about :arachne.component/dependencies ?>

A Component's [dependencies](<?link :arachne.component/Dependency ?>).

A components dependencies will be injected to it under the specified
key, after it is instantiated, and will be started before it.

<?about :arachne.component/Dependency ?>

Entity representing an association between a component and the key
under which it will be associated to dependent components when the
runtime is instantiated.

<?about :arachne.component.dependency/entity ?>

Reference to the target component of this dependency.

<?about :arachne.component.dependency/key ?>

The key used to `assoc` the dependency to the component instance. If
the string value starts with a colon, it will be parsed and associated
using a keyword instead of a string.

<?about :arachne.component/constructor ?>

Indicates the Clojure function that will be invoked to return an
(unstarted) component instance. The return value will be used as the
component instance. The function may take 0, 1 or 2 arguments:

1. Functions with no arguments will be called with no arguments.
2. Functions with one argument will be passed a map of the component
   entity, with all its keys (i.e, pull '*).
3. Functions with two arguments will be passed the descriptor and the
   component IRI. Presumably, they will execute their own query of the
   descriptor to derive the data they need to create an opponent
   instance.

<?about :arachne.descriptor/Tx ?>

Every addition to an Arachne descriptor is logged as a `Tx` entity,
and associated with its constituent RDF triples (which are reified via RDF
reification), under the <?ref :arachne.descriptor/tx ?> key.

This means that for every triple, it is possible to look up the
corresponding `Tx` entity, and from there metadata on where and why
that particular triple was added to the descriptor.

<?about :arachne.descriptor/tx ?>

Associates a reified triple with the logical [Transaction](<?link :arachne.descriptor/Tx ?>) of which it is a part.

<?about :arachne.descriptor.tx/index ?>

Transactions to an Arachne descriptor are serialized, and this
property indicates the index of a particular transaction. For example,
the first-ever transaction to a descriptor has an index of 0, the second an
index of 1, and so on.

<?about :arachne.descriptor.tx/provenance ?>

Links a [Transaction](<?link :arachne.descriptor/Tx ?>) with a
[Provenance](<?link :arachne/Provenance ?>) entity, which aggregates
additional data about the context in which the transaction was
created.

<?about :arachne/Provenance ?>

Entity aggregating information about the source of particular RDF data, such
as the [function](<?link :arachne.provenance/function ?>) that created
it, the JVM [stack frame](<?link :arachne.provenance/stack-frame ?>)
that was executing at the time, etc.

Multiple provenance entries may pertain to the same transaction -- if
so, they are nested using the [parent](<?link :arachne.provenance/parent ?>)
properties. If a given Provenance entry pertains to a transaction,
that implies that all its parents do too.

<?about :arachne/StackFrame ?>

Entity representing a JVM stack frame, attached to a
[Provenance](<?link :arachne/Provenance ?>) entity for debugging
purposes.

<?about :arachne.descriptor/Validator ?>

A Validator entity represents a function that is passed a descriptor,
queries it, and returns a (possibly empty) set of validation
errors. When validated, a descriptor queries and uses all the
Validator entities that it contains for validation.

