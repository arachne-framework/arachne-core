(ns arachne.core.dsl
  "User-facing DSL for use in init scripts"
  (:require [arachne.core.descriptor :as d]
            [arachne.core.descriptor.script :as script :refer [defdsl]]
            [arachne.aristotle.graph :as g]
            [arachne.core.util :as util]
            [clojure.spec.alpha :as s])
  (:import [java.util UUID])
  (:refer-clojure :exclude [ref def]))

(s/def ::id (s/or :iri ::g/iri, :blank ::g/named-blank))

(defn new-bnode
  "Construct a new blank node symbol"
  []
  (symbol (str '_ (UUID/randomUUID))))

(defn id-from-args
  "Given conformed arguments containing an :id, return the given ID or
  construct a new blank node if one is not found."
  [conformed-args]
  (if (:id conformed-args) (s/unform ::id (:id conformed-args)) (new-bnode)))

(defdsl transact
  "Update the context configuration with the given data. The data must
  be interpretable as RDF data using Aristotle."
  (s/cat :data ::g/triples)
  [data]
  (script/transact data))

(defdsl runtime
  "Defines an Arachne runtime containing the given root
  components. Returns the ID of the runtime, which may be provided. If
  none is provided, a blank node ID will be generated."
  (s/cat :id (s/? ::id) :roots (s/coll-of ::id :min-count 1))
  [<id> roots]
  (let [id (id-from-args &args)
        data {:rdf/about id
              :arachne.runtime/components (map #(s/unform ::id %) (:roots &args))}]
    (script/transact data)
    id))

(s/def ::constructor qualified-symbol?)
(s/def ::dependency-map (s/map-of keyword? ::id :min-count 1))

(defdsl component
  "Low-level form for defining a component. Takes the fully-qualified name of a component
   constructor function (mandatory), and a map of dependencies (optional).

   For example:

      (component :my.app/some-component 'my.app/ctor)

   Or:

      (component :my.app/some-component 'my.app/ctor {:foobar :my/some-component})

  Returns the id of the component."
  (s/cat :id (s/? ::id)
         :constructor ::constructor
         :dependencies (s/? ::dependency-map))
  [<id> constructor <dependency-map>]
  (let [id (id-from-args &args)
        data {:rdf/about id
              :arachne.component/constructor (:constructor &args)
              :arachne.component/dependencies (map (fn [[kw id]]
                                                     {:arachne.component.dependency/key (str kw)
                                                      :arachne.component.dependency/entity id})
                                                (s/unform ::dependency-map (:dependencies &args)))}]
    (script/transact data)))

(defn enable-debug!
  "Globally enable DSL script debugging. This allows you to evaluate DSL forms outside the context of a
   config script (for example, by themselves at the REPL.)

   However, instead of updating a configuration (since there is none to update), any transactions
   are simply pretty-printed to System/out instead. This is useful for debugging DSL forms and
   understanding how they map to data in the configuration.

   DSL forms which normally woudl return a resolved entity ID will instead return a random
   integer, allowing nested DSL forms to be debugged."
  []
  (alter-var-root #'script/*debug-dsl-txdata* (constantly true)))

(defn disable-debug!
  "Globally disable DSL script debugging."
  []
  (alter-var-root #'script/*debug-dsl-txdata* (constantly false)))
