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
  [ident supers & attrs]
  (let [update-domain (fn [attr-map] (update attr-map :arachne.attribute/domain
                                       (fnil conj #{}) (by-ident ident)))
        class-map {:db/id (cfg/tempid)
                   :db/ident ident}
        class-map (if (not-empty supers)
                    (assoc class-map :arachne.class/superclasses supers)
                    class-map)]
    (cons class-map (map update-domain attrs))))
