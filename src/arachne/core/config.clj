(ns arachne.core.config
  "Handles building and managing Arachne's central Config object"
  (:refer-clojure :exclude [new update])
  (:require [arachne.core.module :as m]
            [arachne.core.config.specs]
            [arachne.core.util :as u]
            [clojure.string :as str]
            [clojure.spec :as spec]
            [clojure.walk :as w]))

(def ^:dynamic *default-partition* :db.part/user)

(defprotocol Configuration
  "An abstraction over a configuration, with schema, queryable via Datalog"
  (init- [config schema-txes])
  (update- [config txdata])
  (query- [config find-expr other-sources])
  (pull- [config expr id]))

(deftype Tempid [partition id]
  Object
  (equals [this other]
    (when (instance? Tempid other)
      (if id
        (and (= id (.id other)) (= partition (.partition other)))
        (identical? this other))))
  (hashCode [this]
    (if id
      (hash-combine id
        (hash-combine (.hashCode Tempid) (.hashCode partition)))
      (System/identityHashCode this)))
  (toString [this]
    (str "#arachne/tempid["
      (str/join " " (filter identity [partition id]))
      "]")))

(defmethod print-method Tempid [tid writer]
  (.write writer (.toString tid)))

(defn tempid
  "Return a tempid representation which is agnostic to the actual underlying
  Datalog implementation."
  ([] (->Tempid *default-partition* nil))
  ([partition-or-id]
   (if (keyword? partition-or-id)
     (->Tempid partition-or-id nil)
     (->Tempid *default-partition* partition-or-id)))
  ([partition id] (->Tempid partition id)))

(defn- non-record-map?
  "Return true for maps which are not also records"
  [v]
  (and (map? v)
    (not (instance? clojure.lang.IRecord v))))

(defn- add-missing-tempids
  "Given arbitrary txdata, add an Arachne tempid to any map entity that is
  missing one"
  [txdata]
  (w/prewalk (fn [val]
               (if (and (non-record-map? val)(not (:db/id val)))
                 (assoc val :db/id (tempid))
                 val))
    txdata))

(defn init
  "Given a seq of txdatas containing Datomic-style schema, return a new empty
  configuration"
  [config schema-txes]
  (u/validate-args `init config schema-txes)
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
  (u/validate-args `update config txdata)
  (update- config (add-missing-tempids txdata)))

(defn q
  "Given a Datomic-style query expression and any number of additional data
  sources, query the configuration."
  [config find-expr & other-sources]
  (u/validate-args `q config find-expr other-sources)
  (query- config find-expr other-sources))

(defn pull
  "Given a Datomic-style pull expression and an identity (either a lookup ref
    or an entity ID, return the resulting data structure."
  [config expr id]
  (u/validate-args `pull config expr id)
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
  (u/validate-args `new modules)
  (let [ctor (find-impl)]
    (init (@ctor) (map m/schema modules))))

(defn tempid-literal
  "Build a tempid representation from a reader literal of the form
  `arachne/tempid []` or `arachne/tempid [-1]`"
  [form]
  (apply tempid form))