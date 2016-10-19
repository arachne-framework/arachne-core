(ns arachne.core.validators
  (:require [arachne.core.config :as cfg]
            [arachne.core.config.validation :as v]
            [arachne.error :as e :refer [error deferror]]
            [arachne.core.config.ontology :as ont]
            [arachne.core.util :as util]))

(defn- count-any
  "Counts the object, which might or might not be a collection"
  [obj]
  (cond
    (nil? obj) 0
    (and (coll? obj)
      (not (map? obj))) (count obj)
    :else 1))

(deferror ::min-cardinality-constraint
  :message "Min-cardinality constraint was violated"
  :explanation "The entity with eid `:eid` violated a min-cardinality constraint. It was supposed to have at least :expected values for `:attr`, but :actual were found."
  :ex-data-docs {:entity "The failed entity"
                 :eid "The eid of the failed entity"
                 :attr "The attribute with the constraint"
                 :expected "The expected min cardinality"
                 :actual "The actual cardinality"})

(deferror ::max-cardinality-constraint
  :message "Min-cardinality constraint was violated"
  :explanation "The entity with eid `:eid` violated a max-cardinality constraint. It was supposed to have no more than :expected values for `:attr`, but :actual were found."
  :ex-data-docs {:entity "The failed entity"
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
                              (class ?class ?entity)
                              [?attr :db/ident ?attr-ident]]
                   ont/rules)]
    (filter identity
      (for [[entity attr min] entities]
        (let [count (count-any (cfg/attr cfg entity attr))]
          (when (< count min)
            (e/arachne-ex ::min-cardinality-constraint
              {:entity entity
               :eid (:db/id entity)
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
                              [?attr :db/cardinality :db.cardinality/many]
                              [?attr :arachne.attribute/domain ?class]
                              (class ?class ?entity)
                              [?attr :db/ident ?attr-ident]]
                   ont/rules)]
    (filter identity
      (for [[entity attr max] entities]
        (let [count (count-any (cfg/attr cfg entity attr))]
          (when (< max count)
            (e/arachne-ex ::max-cardinality-constraint
              {:entity entity
               :eid (:db/id entity)
               :attr attr
               :expected max
               :actual count} nil)))))))
