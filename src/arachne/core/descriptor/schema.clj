(ns arachne.core.descriptor.schema
  "DSL for more fluently writing RDFS/OWL schema."
  (:require [arachne.aristotle.graph :as g]
            [arachne.error :as e]
            [clojure.spec.alpha :as s])
  (:refer-clojure :exclude [class]))

(s/def ::attr (s/cat
                :max #{:one :many}
                :min #{:required :optional}
                :range ::g/iri))

(s/def ::class-args
  (s/cat :class ::g/iri
         :supers (s/coll-of ::g/iri)
         :attrs (s/? (s/map-of ::g/iri ::attr :conform-keys true))))

(defn- cardinality-restriction
  [[name attr]]
  (let [cards (condp = [(:max attr) (:min attr)]
                [:many :optional] nil
                [:one :optional] {:owl/maxCardinality 1}
                [:one :required] {:owl/cardinality 1}
                [:many :required] {:owl/minCardinality 1})]
    (when cards
      (merge cards
             {:rdf/type :owl/Restriction
              :owl/onProperty (s/unform ::g/iri name)}))))

(defn class
  "Define a class using a more succinct data format. Returns a RDF/EDN map.

   Note that this format cannot express everything that OWL and should
  only be used if your class definition fits the '80% case'."
  [& args]
  (apply e/assert-args `class args)
  (let [args (s/conform ::class-args args)
        subclass (vec (concat (keep cardinality-restriction (:attrs args))
                              (mapv #(s/unform ::g/iri %) (:supers args))))
        mm {:rdf/about (s/unform ::g/iri (:class args))
            :rdfs/_domain (mapv (fn [[name attr]]
                                  (let [range (->> attr :range (s/unform ::g/iri) g/node g/data)
                                        type (if (and (keyword? range) (= "xsd" (namespace range)))
                                               :owl/DataTypeProperty
                                               :owl/ObjectProperty)]
                                    {:rdf/about (s/unform ::g/iri name)
                                     :rdfs/range range
                                     :rdf/type type}))
                            (:attrs args))
            :rdfs/subClassOf subclass}]
    mm))

(defn class-rdr [definition]
  (apply class definition))

(s/fdef class :args ::class-args)

