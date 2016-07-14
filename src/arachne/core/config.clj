(ns arachne.core.config
  "Handles building and managing Arachne's central Config object"
  (:refer-clojure :exclude [new update])
  (:require [arachne.core.module :as m]
            [arachne.core.config.specs]
            [clojure.spec :as spec]
            [arachne.core.util :as u]))

(defprotocol Configuration
  "An abstraction over a configuration, with schema, queryable via Datalog"
  (init- [config schema-txes])
  (update- [config txdata])
  (query- [config find-expr other-sources])
  (pull- [config expr id]))

(defn init
  "Given a seq of txdatas containing Datomic-style schema, return a new empty
  configuration"
  [config schema-txes]
  (u/assert-args init config schema-txes)
  (init- config schema-txes))

(defn update
  "Return an updated configuration, given Datomic-style txdata. Differences
     from Datomic include:

    - Temporary IDs must be instances of arachne.core.config/Tempid instead of
      the format used by the underlying implementation
    - Temporary IDs are not necessary on most entity maps (a novel temp id will
      be supplied when one is missing)
    - Transactor functions are not supported"
  [config txdata]
  (u/assert-args update config txdata)
  (update- config txdata))

(defn q
  "Given a Datomic-style query expression and any number of additional data
  sources, query the configuration."
  [config find-expr & other-sources]
  (u/assert-args q config find-expr other-sources)
  (query- config find-expr other-sources))

(defn pull
  "Given a Datomic-style pull expression and an identity (either a lookup ref
    or an entity ID, return the resulting data structure."
  [config expr id]
  (u/assert-args pull config expr id)
  (pull- config expr id))

(def ^:private datomic-ctor 'arachne.core.config.impl.datomic/ctor)
(def ^:private datascript-ctor 'arachne.core.config.impl.datascript/ctor)
(def ^:private multiplex-ctor 'arachne.core.config.impl.multiplex/ctor)

(defn- find-impl
  "Return a config constructor, based on what is present in the classpath"
  []
  (let [maybe-resolve (fn [sym]
                        (try
                          (require (symbol (namespace sym)))
                          (resolve sym)
                          (catch Throwable t
                            nil)))
        datomic (maybe-resolve datomic-ctor)
        datascript (maybe-resolve datascript-ctor)]
    (cond
      (and datomic datascript) (maybe-resolve multiplex-ctor)
      datomic datomic
      datascript datascript
      :else (throw (ex-info "Could not find config implementation. You must include either Datomic or Datascript on your classpath." {})))))

(defn new
  "Returns an empty config, with schema installed, for the given sequence of
  modules."
  [modules]
  (u/assert-args new modules)
  (let [ctor (find-impl)]
    (init (@ctor) (map m/schema modules))))

(defrecord Tempid [id])

(defn tempid
  "Return a tempid representation which is agnostic to the actual underlying
  Datalog implementation."
  ([] (->Tempid nil))
  ([id] (->Tempid id)))

(defn tempid-literal
  "Build a tempid representation from a reader literal of the form
  `arachne/tempid []` or `arachne/tempid [-1]`"
  [form]
  (apply tempid form))