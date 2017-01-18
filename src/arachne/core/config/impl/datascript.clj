(ns arachne.core.config.impl.datascript
  (:require [clojure.walk :as w]
            [datascript.core :as d]
            [datascript.query :as dq]
            [arachne.core.config :as cfg]
            [arachne.core.config.impl.common :as common]
            [arachne.core.util :as util]
            [arachne.error :as e :refer [error deferror]]))

;; This is a schema *map*, which defines the schema for schema *entities*
(def ^:private base-schema {:db/ident {:db/unique :db.unique/identity}
                            :db.install/attribute {:db/valueType :db.type/ref}
                            :db/unique {:db/valueType :db.type/ref}
                            :db/cardinality {:db/valueType :db.type/ref}
                            :db/valueType {:db/valueType :db.type/ref}
                            })

(def ^:private schema-idents
    #{:db.unique/identity :db.unique/value :db.cardinality/many
      :db.cardinality/one :db.type/string :db.type/ref :db.type/boolean
      :db.type/long :db.type/bigint :db.type/float :db.type/double
      :db.type/bigdec :db.type/instant :db.type/uuid :db.type/uri
      :db.type/bytes :db.type/keyword})

(defn replace-idents
  "Replace ident references with entity references, for consistency between
  DataScript and Datomic. Note: will only work for map tx forms."
  [data]
  (w/postwalk (fn [val]
                (if (or (schema-idents val)
                        (and (keyword? val) (= "db.part" (namespace val))))
                  {:db/id (d/tempid :db.part/db)
                   :db/ident val}
                  val))
    data))

(def ^:private supported-schema-entries
  #{[:db/unique :db.unique/identity]
    [:db/unique :db.unique/value]
    [:db/valueType :db.type/ref]
    [:db/isComponent true]
    [:db/cardinality :db.cardinality/one]
    [:db/cardinality :db.cardinality/many]})

(defn- ds-schema-map
  "Given an attribute entity, return a datascript schema map"
  [attr]
  (->> attr
    (map (fn [[k v]]
           [k (if-let [ident (:db/ident v)]
                ident
                v)]))
    (filter supported-schema-entries)
    (into {})))

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
            (map ds-schema-map attrs))))

(defn- init
  [schema-txes]
  (let [schema-txes (replace-idents schema-txes)
        schema-map (merge base-schema (ds-schema schema-txes))]
    @(d/create-conn schema-map)))

(defrecord DatascriptConfig [db tempids]
  cfg/Configuration
  (init- [this schema-txes]
    (assoc this :db (init schema-txes)))
  (update- [this txdata]
    (let [txdata (replace-idents txdata)
          result (common/with db txdata d/with d/tempid d/resolve-tempid)]
      (assoc this :db (:db-after result)
                  :tempids (:arachne-tempids result))))
  (query- [this query other-sources]
    (apply d/q query (:db this) other-sources))
  (pull- [this expr lookup-or-eid]
    (when-not (d/entity (:db this) lookup-or-eid)
      (error ::cfg/nonexistent-pull-entity {:lookup lookup-or-eid
                                            :config this}))
    (d/pull (:db this) expr lookup-or-eid))
  (resolve-tempid- [this tempid]
    (get tempids tempid))
  Object
  (toString [this]
    (str "#DatascriptConfig[" (hash-combine (hash db) (hash tempids)) "]")))

(defmethod print-method DatascriptConfig
  [cfg writer]
  (.write writer (str cfg)))

(defn new
  "Construct and return a fresh, uninitialized instance of DatascriptConfig"
  []
  (->DatascriptConfig nil nil))
