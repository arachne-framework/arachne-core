(ns arachne.core
  "The core Arachne module that bootstraps everything else"
  (:require [arachne.core.module :as m]
            [arachne.core.descriptor :as d]
            #_[arachne.core.runtime :as rt]
            #_[arachne.core.config.validation :as v]
            #_[arachne.core.config.script :as script]
            [arachne.aristotle.graph :as g]
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
  "Implement :arachne.component/instance in terms of :arachne.component/constructor"
  [d]
  (let [components (d/query d '[?c]
                            '[:bgp [?c :arachne.component/instance ?i]])]
    (if (empty? components)
      d
      (d/with-provenance `add-instance-constructors
        (doseq [[c] components]
          (d/update! d [c :arachne.component/constructor 'arachne.core/instance-ctor]))))))

(s/def ::descriptor-args (s/cat :module ::g/iri
                                :data (s/? ::g/triples)
                                :validate? (s/? boolean?)))
(s/fdef descriptor
  :args ::descriptor-args)

(defn descriptor
  "Initialize a new Arachne descriptor for this application. Arguments
  are:

  - The IRI of the root Arachne module to load
  - A RDF/EDN data structure of data to add to the descriptor (optional)
  - A boolean indicator of whether or not to validate the descriptor
    before returning."
  [& args]
  (let [{:keys [module data validate]} (s/conform ::descriptor-args args)]
    (m/descriptor module (s/unform ::g/triples data) validate)))

#_(s/fdef runtime
  :args (s/cat :config nil
               :resource nil))

#_(defn runtime
  "Create a new Arachne runtime from the given configuration and the :arachne/id
  of the root runtime entity"
  [cfg arachne-id]
  (e/assert-args `runtime cfg arachne-id)
  (rt/init cfg [:arachne/id arachne-id]))
