(ns arachne.core.config.model
  "Utilities for creating a configuration schema using a richer data model."
  (:refer-clojure :exclude [type])
  (:require [clojure.spec :as s]
            [arachne.core.config.model.specs :as ms]
            [arachne.core.config :as cfg]
            [arachne.error :as e]
            [arachne.core.util :as util]))

(defn- by-ident
  "Return a map that will unify with the entity of the given ident. If given
  ident is not an ident, return it unchanged."
  [ident]
  (if (and (keyword? ident) (namespace ident))
    {:db/id    (cfg/tempid)
     :db/ident ident}
    ident))

(defn- attr-map-fragment
  [[attr-def value]]
  (case attr-def
    :shorthand (ms/shorthand-schema value)
    :docstring {:db/doc value}
    :range {:arachne.attribute/range (by-ident value)
            :db/valueType :db.type/ref}
    :schema-fragment value
    :cardinality-range
    (let [min (:min-value value)
          max (when (integer? (-> value :max second))
                (-> value :max second))]
      (cond-> {:arachne.attribute/min-cardinality min}
              max (assoc :arachne.attribute/max-cardinality max)
              true (assoc :db/cardinality
                          (if (= 1 max)
                            :db.cardinality/one
                            :db.cardinality/many))))))
(defn attr
  "Build an attribute definition map using a sequence of 'shorthand' keywords.

   See the source for arachne/core/schema.clj for usage examples."
  [& args]
  (apply e/assert-args `attr args)
  (let [definition (s/conform (:args (s/get-spec `attr)) args)]
    (->> (:attrs definition)
      (map attr-map-fragment)
      (reduce merge {:db/id (cfg/tempid :db.part/db)
                     :db/ident (:ident definition)
                     :db.install/_attribute :db.part/db}))))



(defn type
  "Build a type definition map, returning a seq of txdata.

  See the source for arachne/core/schema.clj for usage examples.

  The `specs` arg should only be provided if the type being defined extends component."
  [& args]
  (apply e/assert-args `type args)
  (let [{:keys [ident supers docstring specs attrs]}
        (s/conform (:args (s/get-spec `type)) args)

        update-domain (fn [attr-map]
                        (assoc attr-map :arachne.attribute/domain (by-ident ident)))
        type-map (util/mkeep
                    {:db/id (cfg/tempid)
                     :db/ident ident
                     :db/doc docstring
                     :arachne.type/supertypes (map (fn [super]
                                                        {:db/ident super})
                                                   supers)
                     :arachne.type/component-specs specs})]
    (cons type-map (map update-domain attrs))))

(def rules
  "Datalog rules to determine type relationships in an Arachne config. Provided rules are:

  - (supertype ?supertype ?subtype) - determine supertype/subtype relationships (including transitively)
  - (type ?type ?entity) - determine what entities are instances of what types (if possible), based on its attributes.

  "
  '[
    [(supertype ?supertype ?subtype)
     [?subtype :arachne.type/supertypes ?supertype]]
    [(supertype ?supertype ?subtype)
     [?subtype :arachne.type/supertypes ?mid]
     (supertype ?supertype ?mid)]

    [(type ?type ?entity)
     [?attr-e :arachne.attribute/domain ?type]
     [?attr-e :db/ident ?attr-ident]
     [?entity ?attr-ident _]]
    [(type ?type ?entity)
     [?attr-e :arachne.attribute/range ?type]
     [?attr-e :db/ident ?attr-ident]
     [_ ?attr-ident ?entity]]
    [(type ?type ?entity)
     [?entity :arachne/instance-of ?type]]
    [(type ?type ?entity)
     (supertype ?type ?subtype)
     (type ?subtype ?entity)]])
