(ns arachne.core.validators
  (:require [arachne.core.config :as cfg]
            [arachne.core.config.validation :as v]
            [arachne.error :as e :refer [error deferror]]
            [arachne.core.config.model :as m]
            [arachne.core.util :as util]))

(defn- count-attr
  "Returns the actual cardinality of an attribute on an entity"
  [cfg eid attr]
  (count
    (cfg/q cfg '[:find [?v ...]
                 :in $ ?e ?a
                 :where [?e ?a ?v]]
      eid attr)))

(deferror ::min-cardinality-constraint
  :message "Min-cardinality constraint on `:eid` (Arachne ID: `:aid`) for attr `:attr` was violated"
  :explanation "The entity with eid `:eid` violated a min-cardinality constraint. It was supposed to have at least :expected values for `:attr`, but :actual were found."
  :ex-data-docs {:entity "The failed entity"
                 :aid "The Arachne ID of the failed entity"
                 :eid "The eid of the failed entity"
                 :attr "The attribute with the constraint"
                 :expected "The expected min cardinality"
                 :actual "The actual cardinality"})

(deferror ::max-cardinality-constraint
  :message "Min-cardinality constraint on `:eid` (Arachne ID: `:aid`) for attr `:attr` was violated"
  :explanation "The entity with eid `:eid` violated a max-cardinality constraint. It was supposed to have no more than :expected values for `:attr`, but :actual were found."
  :ex-data-docs {:entity "The failed entity"
                 :aid "The Arachne ID of the failed entity"
                 :eid "The eid of the failed entity"
                 :attr "The attribute with the constraint"
                 :expected "The expected max cardinality"
                 :actual "The actual cardinality"})


(defn min-cardinality
  "Ensure that all entities in the config have min-cardinality correct"
  [cfg]
  (let [entities (cfg/q cfg '[:find ?entity ?attr-ident ?min
                              :in $ %
                              :where
                              [?attr :arachne.attribute/min-cardinality ?min]
                              [?attr :arachne.attribute/domain ?class]
                              (type ?class ?entity)
                              [?attr :db/ident ?attr-ident]]
                   m/rules)]
    (filter identity
      (for [[eid attr min] entities]
        (let [count (count-attr cfg eid attr)]
          (when (< count min)
            (e/arachne-ex ::min-cardinality-constraint
              {:eid eid
               :aid (cfg/attr cfg eid :arachne/id)
               :attr attr
               :expected min
               :actual count} nil)))))))

(defn max-cardinality
  "Ensure that all entities in the config have min-cardinality correct"
  [cfg]
  (let [entities (cfg/q cfg '[:find ?entity ?attr-ident ?max
                              :in $ %
                              :where
                              [?attr :arachne.attribute/max-cardinality ?max]
                              [?attr :db/cardinality ?card]
                              [?card :db/ident :db.cardinality/many]
                              [?attr :arachne.attribute/domain ?class]
                              (type ?class ?entity)
                              [?attr :db/ident ?attr-ident]]
                   m/rules)]
    (filter identity
      (for [[eid attr max] entities]
        (let [count (count-attr cfg eid attr)]
          (when (< max count)
            (e/arachne-ex ::max-cardinality-constraint
              {:eid eid
               :aid (cfg/attr cfg eid :arachne/id)
               :attr attr
               :expected max
               :actual count} nil)))))))
