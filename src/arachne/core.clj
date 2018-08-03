(ns arachne.core
  "The core Arachne module that bootstraps everything else"
  (:require [arachne.core.module :as m]
            [arachne.core.runtime :as rt]
            [arachne.core.descriptor :as d]
            [arachne.aristotle.graph :as g]
            [arachne.aristotle.registry :as reg]
            [arachne.core.util :as util]
            [arachne.core.schema :as schema]
            [arachne.error :as e]
            [clojure.spec.alpha :as s]
            [arachne.core.util :as u]))

(defn ^:no-doc instance-ctor
  "Component constructor that defines components by resolving a var"
  [component]
  @(util/require-and-resolve (:arachne.component/instance component)))

(defn ^:no-doc add-instance-constructors
  "Configure function: Implement :arachne.component/instance in terms
  of :arachne.component/constructor"
  [d]
  (let [components (d/query d '[?c]
                     '[:bgp [?c :arachne.component/instance ?i]])]
    (if (empty? components)
      d
      (d/with-provenance `add-instance-constructors
        (doseq [[c] components]
          (d/update! d [c :arachne.component/constructor 'arachne.core/instance-ctor]))))))

(defn ^:no-doc distinct-vars
  "Configure function: Assert that all :clojure/Var resources in the descriptor are mutually distinct"
  [d]
  (let [vars (d/query d '[?v]
               '[:bgp [?v :rdf/type :clojure/Var]])
        vars (set (apply concat vars))]
    (d/with-provenance `distinct-vars
      (let [data {:rdf/type :owl/AllDifferent
                  :owl/distinctMembers (g/rdf-list vars)}]
        (d/update! d data)))))

(s/def ::descriptor-args (s/cat :module ::g/iri
                                :data (s/? ::g/triples)
                                :validate? (s/? boolean?)))

(s/fdef descriptor :args ::descriptor-args)

(defn descriptor
  "Initialize a new Arachne descriptor for this application. Arguments
  are:

  - The IRI of the root Arachne module to load
  - A RDF/EDN data structure of data to add to the descriptor (optional)
  - A boolean indicator of whether or not to validate the descriptor
    before returning (optional)."
  [& args]
  (let [{:keys [module data validate?]} (s/conform ::descriptor-args args)]
    (m/descriptor (s/unform ::g/iri module) (when data (s/unform ::g/triples data)) validate?)))

(s/fdef runtime
  :args (s/cat :descriptor d/descriptor? :iri ::g/iri))

(defn runtime
  "Instantiate and return an unstarted Arachne runtime, for the runtime with the given IRI in the given descriptor."
  [descriptor iri]
  (e/assert-args `runtime descriptor iri)
  (rt/init descriptor iri))

(defn component
  "Given a runtime and a component IRI, return the component instance (if it exists.)"
  [runtime iri]
  (rt/lookup runtime iri))
