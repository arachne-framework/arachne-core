(ns arachne.core.config.ontology
  "Utilities for creating Configuration schema using Arachne's configuration
  ontology, in a terse way."
  (:refer-clojure :exclude [class])
  (:require [clojure.spec :as s]
            [arachne.core.config.ontology.specs :as os]
            [arachne.core.config :as cfg]
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
    :shorthand (os/shorthand-schema value)
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
  "Build an attribute definition map."
  [& args]
  (apply util/validate-args `attr args)
  (let [definition (s/conform (:args (s/get-spec `attr)) args)]
    (->> (:attrs definition)
      (map attr-map-fragment)
      (reduce merge {:db/id (cfg/tempid :db.part/db)
                     :db/ident (:ident definition)
                     :db.install/_attribute :db.part/db}))))



(defn class
  "Build a class definition map, returning a seq of txdata."
  [& args]
  (apply util/validate-args `class args)
  (let [{:keys [ident supers docstring specs attrs]}
        (s/conform (:args (s/get-spec `class)) args)

        update-domain (fn [attr-map] (update attr-map :arachne.attribute/domain
                                       (fnil conj #{}) (by-ident ident)))
        class-map (util/mkeep
                    {:db/id (cfg/tempid)
                     :db/ident ident
                     :db/doc docstring
                     :arachne.class/superclasses (map (fn [super]
                                                        {:db/ident super})
                                                   supers)
                     :arachne.class/component-spec specs})]
    (cons class-map (map update-domain attrs))))

(defn attr-eid
  "Given an attribute, return a lookup ref based on its ident. If given an
   entity ID, just returns that.

  This function is essentially a compatibility shim between Datomic and
  Datascript"
  [attr-or-eid]
  (when (number? attr-or-eid) attr-or-eid [:db/ident attr-or-eid]))

(def rules
  "Datalog rules to determine type relationships in an Arachne config"
  '[
    [(superclass ?superclass ?subclass)
     [?subclass :arachne.class/superclasses ?superclass]]
    [(superclass ?superclass ?subclass)
     [?subclass :arachne.class/superclasses ?mid]
     (superclass ?superclass ?mid)]

    [(class ?class ?entity)
     [?entity :arachne/instance-of ?class]]
    [(class ?class ?entity)
     [?attr-e :arachne.attribute/domain ?class]
     [?attr-e :db/ident ?attr-ident]
     [?entity ?attr-ident _]]
    [(class ?class ?entity)
     [?attr-e :arachne.attribute/range ?class]
     [?attr-e :db/ident ?attr-ident]
     [_ ?attr-ident ?entity]]
    [(class ?class ?entity)
     (superclass ?class ?subclass)
     (class ?subclass ?entity)]

    ])

