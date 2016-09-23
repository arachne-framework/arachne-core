(ns arachne.core.config
  "Handles building and managing Arachne's central Config object"
  (:refer-clojure :exclude [new update])
  (:require [arachne.core.module :as m]
            [arachne.core.config.specs]
            [arachne.core.util :as u]
            [clojure.string :as str]
            [clojure.spec :as s]
            [clojure.walk :as w]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [arachne.core.util :as util])
  (:import [java.util Date]))

(def ^:dynamic *default-partition* :db.part/user)

(defprotocol Configuration
  "An abstraction over a configuration, with schema, queryable via Datalog"
  (init- [config schema-txes])
  (update- [config txdata])
  (query- [config find-expr other-sources])
  (pull- [config expr id])
  (resolve-tempid- [config arachne-tempid]))

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

(def
  ^{:dynamic true
    :doc "Txdata regarding the provenance of config updates ocurring in this stack context"}
  *provenance-txdata*
  nil)

(defn stack-provenance-txdata
  "Build provenance txdata based on the current stack frame, using the provided
  source and function symbol.

  If a stack-filter-pred is provided, the system will use the top stack frame
  that passes the predicate, otherwise current stack frame is used.

   The returned txdata includes:

  - arachne.transaction/source
  - arachne.transaction/function
  - arachne.transaction/source-file
  - arachne.transaction/source-line"
  ([source function]
   (stack-provenance-txdata source function (constantly false)))
  ([source function stack-filter-pred]
   (let [stack (seq (.getStackTrace (Thread/currentThread)))
         ste (first (filter stack-filter-pred stack))
         txdata [{:db/id (tempid :db.part/tx)
                  :arachne.transaction/source source
                  :arachne.transaction/function (keyword
                                                  (namespace function)
                                                  (name function))}]]
     (if ste
       (concat txdata [{:db/id (tempid :db.part/tx)
                        :arachne.transaction/source-file (.getFileName ste)
                        :arachne.transaction/source-line (.getLineNumber ste)}])
       txdata))))

(defmacro with-provenance
  "Add provenance txdata based upon the current stack location to the binding of
  the *provenance-txdata* var inside the body."
  [& opts-and-body]
  (let [args (s/conform (:args (s/get-spec `with-provenance)) opts-and-body)
        source (:source args)
        function (:function args)
        stack-filter (-> args :options :stack-filter-pred)
        body (:body args)]
    `(let [txdata# ~(if stack-filter
                      `(stack-provenance-txdata ~source ~function ~stack-filter)
                      `(stack-provenance-txdata ~source ~function))]
       (binding [*provenance-txdata* txdata#]
         ~@body))))

(util/deferror ::transaction-exception
  "An error ocurred while updating the configuration")

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
  (let [txdata' (concat txdata *provenance-txdata*)
        txdata' (add-missing-tempids txdata')
        tx-tempid (tempid :db.part/tx)
        txdata' (conj txdata' {:db/id tx-tempid
                               :db/txInstant (Date.)})]
    (try
      (let [new-config (update- config txdata')
            tx (resolve-tempid- new-config tx-tempid)]
        (when-not (pull- new-config '[:arachne.transaction/source] tx)
          (log/warn "No provenance metadata found on transaction"
            (ex-info "" {:txdata txdata'})))
        new-config)
      (catch Throwable t
        (util/error ::transaction-exception {:explicit-txdata txdata
                                             :actual-txdata txdata'}
          t)))))

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

(u/deferror ::could-not-find-config
  "Could not find config implementation. You must include either Datomic or Datascript on your classpath.")

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
      :else (u/error ::could-not-find-config {}))))

(defn new
  "Returns an empty config, with schema installed, for the given sequence of
  modules."
  [modules]
  (u/validate-args `arachne.core.config/new modules)
  (let [ctor (find-impl)]
    (init (@ctor) (map m/schema modules))))

(defn resolve-tempid
  "Given a configuration and an Arachne tempid, return concrete entity ID to
  which the Arachne tempid was matched, in the most recent update to the
  config."
  [config arachne-tempid]
  (u/validate-args `resolve-tempid config arachne-tempid)
  (resolve-tempid- config arachne-tempid))

(defn tempid-literal
  "Build a tempid representation from a reader literal of the form
  `arachne/tempid []` or `arachne/tempid [-1]`"
  [form]
  (apply tempid form))

(defn attr
  "Convenience function to pull and return one or more nested attributes in one
  step"
  [cfg id & attrs]
  (let [[attr & more-attrs] (reverse attrs)
        expr (reduce (fn [inner attr]
                       {attr inner})
               [attr] more-attrs)
        result (pull cfg (if (map? expr) [expr] expr) id)]
    (get-in result attrs)))