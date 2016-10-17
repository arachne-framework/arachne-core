(ns arachne.core.config.impl.datascript
  (:require [clojure.walk :as w]
            [datascript.core :as d]
            [datascript.query :as dq]
            [arachne.core.config :as cfg]
            [arachne.core.config.impl.common :as common]
            [arachne.core.util :as util]
            [arachne.error :as e :refer [error deferror]]))

(def ^:private supported-schema-entries
  #{[:db/unique :db.unique/identity]
    [:db/unique :db.unique/value]
    [:db/valueType :db.type/ref]
    [:db/cardinality :db.cardinality/one]
    [:db/cardinality :db.cardinality/many]})

(def base-schema {:db/ident {:db/unique :db.unique/identity}
                  :db.install/attribute {:db/valueType :db.type/ref}})

(defn replace-partitions
  "Replace partition ident references with entity references"
  [data]
  (w/postwalk (fn [val]
                (if (and (keyword? val) (= "db.part" (namespace val)))
                  {:db/id (d/tempid :db.part/db)
                   :db/ident val}
                  val))
    data))


(defn- ds-schema
  "Convert a schema defined as Datomic-style txdata to a DataScript schema map"
  [schema-txes]
  (let [db @(d/create-conn base-schema)
        db (reduce (fn [db tx]
                     (:db-after (common/with db tx d/with
                                             d/tempid d/resolve-tempid)))
                   db schema-txes)
        attr-eids (d/q '[:find [?e ...]
                         :where [?e _ _]] db)
        attrs (map #(d/entity db %) attr-eids)]
    (zipmap (map :db/ident attrs)
            (map #(into {} (filter supported-schema-entries %)) attrs))))

(defn- init
  [schema-txes]
  (let [ont-schema (util/read-edn "arachne/core/config/ontology/schema.edn")
        schema-txes (concat ont-schema schema-txes)
        schema-txes (replace-partitions schema-txes)
        schema-map (merge base-schema (ds-schema schema-txes))
        db @(d/create-conn schema-map)]
    (reduce (fn [db tx]
              (:db-after (common/with db tx d/with d/tempid d/resolve-tempid)))
            db schema-txes)))

(deferror ::missing-lookup-ref
  :message "Could not resolve look up ref `:ref` in the given configuration"
  :explanation "Some code tried to use a lookup ref (`:ref`) in a Datascript configuration, but a value matching that lookup ref doesn't exist."
  :suggestions ["Make sure the entity represented by the lookup ref actually exists"
                "Make sure the lookup ref is correct, with no typos in the attribute name or value."]
  :ex-data-docs {:ref "The ref that could not be found."})

(defn- resolve-lookup-ref
  "Resolve a lookup ref with a query."
  [db [attr value :as ref]]
  (if-let [result (d/q '[:find ?e .
                         :in $ ?a ?v
                         :where
                         [?e ?a ?v]]
                    db attr value)]
    result
    (error ::missing-lookup-ref {:ref ref})))

(defrecord DatascriptConfig [db tempids]
  cfg/Configuration
  (init- [this schema-txes]
    (assoc this :db (init schema-txes)))
  (update- [this txdata]
    (let [result (common/with db txdata d/with d/tempid d/resolve-tempid)]
      (assoc this :db (:db-after result)
                  :tempids (:arachne-tempids result))))
  (query- [this query other-sources]
    (apply d/q query (:db this) other-sources))
  (pull- [this expr lookup-or-eid]
    (let [eid (if (dq/lookup-ref? lookup-or-eid)
               (resolve-lookup-ref (:db this) lookup-or-eid)
               lookup-or-eid)]
      (d/pull (:db this) expr eid)))
  (resolve-tempid- [this tempid]
    (get tempids tempid))
  Object
  (toString [this]
    (str "#DatascriptConfig[" (hash-combine (hash db) (hash tempids)) "]")))

(defmethod print-method DatascriptConfig
  [cfg writer]
  (.write writer (str cfg)))

(defn ctor
  "Construct and return an uninitialized instance of DatascriptConfig"
  []
  (->DatascriptConfig nil nil))
