(ns arachne.core.validators
  (:require [arachne.core.config :as cfg]
            [arachne.core.config.validation :as v]
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
            {::v/message "Min-cardinality constraint was violated"
             :entity entity
             :attr attr
             :expected min
             :actual count}))))))

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
            {::v/message "Max-cardinality constraint was violated"
             :entity entity
             :attr attr
             :expected max
             :actual count}))))))
