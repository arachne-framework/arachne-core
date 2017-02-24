(ns arachne.core.config
  "Handles building and managing Arachne's central Config object"
  (:refer-clojure :exclude [new update])
  (:require [arachne.core.config.specs]
            [arachne.core.util :as u]
            [arachne.error :as e :refer [deferror error]]
            [clojure.string :as str]
            [clojure.spec :as s]
            [clojure.walk :as w]
            [clojure.java.io :as io]
            [arachne.log :as log])
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
    (if (instance? Tempid other)
      (if id
        (and (= id (.id other)) (= partition (.partition other)))
        (identical? this other))
      false))
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

(def
  ^{:dynamic true
    :doc "Txdata regarding the provenance of config updates ocurring in this stack context"}
  *provenance-txdata*
  nil)

(def
  ^{:dynamic true
    :doc "The full qualified name of the current DSL function. Useful for error reporting in many contexts. Usually set by `with-provenance`."}
  *dsl-function*
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
       (binding [*provenance-txdata* txdata#
                 *dsl-function* ~(:function args)]
         ~@body))))

(declare update)

(defn init
  "Given a fresh blank config and a seq of txdatas containing Datomic-style schema, return a new
  empty configuration with schema installed."
  [blank-cfg schema-txes]
  (e/assert-args `init blank-cfg schema-txes)
  (let [schema-txes (concat
                      (u/read-edn "arachne/core/config/model/schema.edn")
                      schema-txes)
        cfg (init- blank-cfg schema-txes)]
    (reduce (fn [cfg txdata]
              (update cfg txdata false))
      cfg
      schema-txes)))

(deferror ::transaction-exception
  :message "An error ocurred while updating the configuration"
  :explanation "While attempting to transact some new data to the configuration, something went wrong. Most likely, there was somethign wrong with the txdata handed off to Datomic/DataScript."
  :suggestions ["Look at this exception's \"cause\" to get some information on specifically what kind of error it was."
                "Inspect the offending txdata (found in the data attached to this exception) for anything that looks wrong."]
  :ex-data-docs {:explicit-txdata "The txdata provided by the user"
                 :actual-txdata "The actual txdata, post processing"
                 :config "The previous configuration value"})

(defn update
  "Return an updated configuration, given Datomic-style txdata. Differences
     from Datomic include:

    - Temporary IDs must be instances of arachne.core.config/Tempid instead of
      the format used by the underlying implementation
    - Temporary IDs are not necessary on most entity maps (a novel temp id will
      be supplied when one is missing)
    - Transactor functions are not supported"
  ([config txdata] (update config txdata true))
  ([config txdata add-provenance-txdata?]
   (e/assert-args `update config txdata)
   (let [tx-tempid (tempid :db.part/tx)
         txdata' (if add-provenance-txdata?
                   (-> txdata
                     (concat *provenance-txdata*)
                     (conj {:db/id tx-tempid
                            :db/txInstant (Date.)}))
                   txdata)
         txdata' (add-missing-tempids txdata')]
     (try
       (let [new-config (update- config txdata')
             tx (resolve-tempid- new-config tx-tempid)]
         (when add-provenance-txdata?
           (when-not (pull- new-config '[:arachne.transaction/source] tx)
             (log/warn :exception (ex-info "No provenance metadata found on transaction" {:txdata txdata'}))))
         new-config)
       (catch Throwable t
         (error ::transaction-exception {:explicit-txdata txdata
                                         :actual-txdata txdata'
                                         :config config}
           t))))))

(defn q
  "Given a Datomic-style query expression and any number of additional data
  sources, query the configuration."
  [config find-expr & other-sources]
  (e/assert-args `q config find-expr other-sources)
  (query- config find-expr other-sources))

(defn pull
  "Given a Datomic-style pull expression and an identity (either a lookup ref
    or an entity ID, return the resulting data structure."
  [config expr id]
  (e/assert-args `pull config expr id)
  (pull- config expr id))

(defn resolve-tempid
  "Given a configuration and an Arachne tempid, return concrete entity ID to
  which the Arachne tempid was matched, in the most recent update to the
  config."
  [config arachne-tempid]
  (e/assert-args `resolve-tempid config arachne-tempid)
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

(deferror ::entity-not-found
  :message "Entity `:lookup` could not be found"
  :explanation "Some code attempted to use the Pull API to retrieve an entity from the configuration. However, no entity could be found using the specified identifier or lookup ref, `:lookup`.

  This is almost certainly a mistake, and so this exception was thrown rather than propagating a nil value. If you want to test for the existence of an entity, you can use the query API instead."
  :suggestions ["Ensure that the entity referenced by `:lookup` actually exists in the configuration"
                "Check that the entity ID or lookup ref is correct and typo-free"]
  :ex-data-docs {:lookup "The eid or lookup ref"
                 :config "The config"})

(deferror ::nonexistent-pull-entity
  :message "No such entity `:lookup` in configuration"
  :explanation "Some code tried to use a pull expression to retrieve data from the Arachne config.

  However, the entity identified (`:lookup`) did not actually exist in the config.

  Rather than returning nil, DataScript throws an error in this situation, and so for consistency Arachne replicates the same behavior for all configurations."
  :suggestions ["Ensure that the entity ID or lookup ref is correct"
                "Ensure that the desired entity actually exists in the configuration"]
  :ex-data-docs {:lookup "The lookup ref or entity ID"
                 :config "The configuration in question"}
  )


(def dependency-rules
  "Datalog rule to recursively determine the dependency relationship between components"
  '[[(depends ?component ?dependency)
     [?component :arachne.component/dependencies ?dep]
     [?dep :arachne.component.dependency/entity ?dependency]]

    [(depends ?component ?dependency)
     [?component :arachne.component/dependencies ?dep]
     [?dep :arachne.component.dependency/entity ?trans]
     (depends ?trans ?dependency)]])

(defn dependencies
  "Given the entity ID of a component, return all of its direct or transitive dependencies."
  [cfg component-eid]
  (q cfg '[:find [?entity ...]
           :in $ % ?component
           :where
           (depends ?component ?entity)]
    dependency-rules component-eid))