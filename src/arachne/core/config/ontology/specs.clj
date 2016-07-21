(ns arachne.core.config.ontology.specs
  (:require [clojure.spec :as s]
            [arachne.core.config.specs :as config-spec]))

(def shorthand-schema
  "Map of unqualified keywords to their long-form Datomic schema entries"
  {:keyword {:db/valueType :db.type/keyword}
   :string {:db/valueType :db.type/string}
   :boolean {:db/valueType :db.type/boolean}
   :long {:db/valueType :db.type/long}
   :bigint {:db/valueType :db.type/bigint}
   :float {:db/valueType :db.type/float}
   :double {:db/valueType :db.type/double}
   :bigdec {:db/valueType :db.type/bigdec}
   :ref {:db/valueType :db.type/ref}
   :instant {:db/valueType :db.type/instant}
   :uuid {:db/valueType :db.type/uuid}
   :uri {:db/valueType :db.type/uri}
   :bytes {:db/valueType :db.type/bytes}
   ;; Cardinality
   :many {:db/cardinality :db.cardinality/many}
   :one-or-more {:db/cardinality :db.cardinality/many
                 :arachne.attribute/min-cardinality 1}
   :one {:db/cardinality :db.cardinality/one
         :arachne.attribute/min-cardinality 1}
   :one-or-none {:db/cardinality :db.cardinality/one}
   ;; Uniqueness
   :unique {:db/unique :db.unique/value}
   :identity {:db/unique :db.unique/identity}
   ;; Indexing
   :index {:db/index true}
   :fulltext {:db/fulltext true}
   ;; History
   :no-history {:db/noHistory true}
   ;; Component
   :component {:db/isComponent true
               :db/valueType :db.type/ref}})

(s/def ::shorthand-schema (set (keys shorthand-schema)))

(s/def ::class-reference (s/and keyword? namespace))

(s/def ::cardinality-range (s/cat :min-value integer?
                                  :max (s/or :value integer?
                                             :unbounded keyword?)))

(s/fdef arachne.core.config.ontology/attr
  :args (s/cat :ident keyword?
               :attrs (s/+ (s/or :shorthand ::shorthand-schema
                                 :docstring string?
                                 :range ::class-reference
                                 :schema-fragment map?
                                 :cardinality-range ::cardinality-range)))
  :ret ::config-spec/map-txform)

(comment

  (s/conform ::cardinality-range [1 2])

  (s/conform (:args (s/spec 'arachne.core.config.ontology/attr))
             [:foo/bar :uri :fulltext "hello" :arachne/Component [1 3] {:foo :bar}]

             )

  (s/conform
    (:args (s/spec 'arachne.core.config.ontology/attr))
    [:foo/bar :arachne.config/Component [0 :many]])





  )