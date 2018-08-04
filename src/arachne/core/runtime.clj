(ns arachne.core.runtime
  "Dependency Injection and lifecycle management"
  (:require [arachne.core.descriptor :as d]
            [arachne.aristotle.graph :as g]
            [com.stuartsierra.component :as c]
            [com.stuartsierra.dependency :as dep]
            [arachne.core.util :as util]
            [arachne.error :as e :refer [deferror error]]
            [arachne.log :as log]
            [clojure.spec.alpha :as s]
            [clojure.set :as set]))

(def ^:private component-pull
  "Pull expression for retrieving necessary data about a component"
  ['*
   {:arachne.component/dependencies [:arachne.component.dependency/key
                                     :arachne.component.dependency/entity]}])

(deferror ::missing-runtime
  :message "No runtime found for `:rt`"
  :explanation "There was no runtime with the with the specified IRI found in the descriptor, or it was invalid because it was not associated with any components. Valid runtimes in the descriptor are: \n\n :runtimes-str"
  :suggestions ["Make sure that `:rt` is correct and exists in the descriptor."
                "Make sure `:rt` is associated with one or more root components."]
  :ex-data-docs {:descriptor "The descriptor"
                 :rt "The IRI of the missing runtime"
                 :runtimes "Valid runtimes in the descriptor"})

(defn components
  "Given a runtime IRI, return all components that are part of the
  runtime (as maps). The descriptor itself is included under
  the :arachne.component/descriptor key."
  [d runtime]
  (let [roots (d/query d ['?c]
                '[:bgp [?rt :arachne.runtime/components ?c]]
                {'?rt runtime})
        _ (when (empty? roots)
            (let [valid-runtimes (map first
                                   (d/query d '[?rt]
                                     '[:bgp [?rt :arachne.runtime/components _]]))]
              (error ::missing-runtime {:descriptor d
                                        :rt runtime
                                        :runtimes valid-runtimes
                                        :runtimes-str (e/bullet-list valid-runtimes)})))
        root-components (map #(d/pull d (first %) component-pull) roots)
        with-deps (fn with-deps [c]
                    (let [deps (->> c :arachne.component/dependencies
                                 (map :arachne.component.dependency/entity)
                                 (map #(d/pull d % component-pull))
                                 (map #(assoc % :arachne.component/descriptor d)))]
                      (conj (mapcat with-deps deps) c)))]
    (mapcat with-deps root-components)))

(deferror ::error-instantiating
  :message "Error instantiating component `:iri`"
  :explanation "An exception was thrown when trying to instantiate a component with IRI `:iri` while building the Arachne runtime. This component was configured to use a constructor function `:ctor`, but when it was invoked, instead of returning a component instance, the constructor function threw an exception of type `:ex-type`"
  :suggestions ["Make sure `:ctor` is a valid constructor function, correct and error-free"
                "Investigate the cause below to determine the underlying reason for the failure."]
  :ex-data-docs {:descriptor "The descriptor"
                 :iri "IRI of the component"
                 :ctor "The name of the constructor that failed"
                 :ex-type "The type of the exception"})

(defn- instantiate
  "Invoke a Component definition's constructor function to return a runtime
  instance of the component. If the object returned by the constructor is a map,
  the component definition itself will be merged in."
  [{:keys [:arachne.component/constructor
           :arachne.component/descriptor]
    :as component-map}]
  (let [instance (try
                   (let [ctor-fn (util/require-and-resolve constructor)]
                     (condp = (util/arity @ctor-fn)
                       0 (ctor-fn)
                       1 (ctor-fn component-map)
                       2 (ctor-fn descriptor (:rdf/about component-map))))
                   (catch Throwable t
                     (error ::error-instantiating
                       {:ex-type (.getName (class t))
                        :descriptor descriptor
                        :iri (:rdf/about component-map)
                        :ctor constructor} t)))]
    (if (map? instance)
      (reduce (fn [i [k v]] (if (contains? i k) i (assoc i k v)))
        instance component-map)
      instance)))

(defn- system-map
  "Given a descriptor and collection of component maps, instantiates the
  descriptor and return a Component system map to pass to component/system-map."
  [components]
  (zipmap (map :rdf/about components)
          (map instantiate components)))

(defn- component-dependency-map
  "Given a component entity, return a Component dependency map"
  [component]
  (->> component
    :arachne.component/dependencies
    (map (fn [{:keys [:arachne.component.dependency/entity
                      :arachne.component.dependency/key]}]
           (if key {(read-string key) entity, entity entity} {entity entity})))
    (apply merge)))

(defn- dependency-map
  "Given a collection of component maps, return a Component
  dependency map to pass to component/system-using. Each component
  will have its dependencies associated under the key specified in the
  descriptor, as well as the IRI of each dependency."
  [components]
  (->> components
    (map (fn [c] [(:rdf/about c) (component-dependency-map c)]))
    (filter #(not-empty (second %)))
    (into {})))

(defn- system
  "Given a descriptor and the IRI of a runtime entity, return an (unstarted) Component system.
  (unstarted) Component system"
  [descriptor iri]
  (let [components (components descriptor iri)]
    (c/system-using
      (merge (c/system-map) (system-map components))
      (dependency-map components))))

(defrecord ArachneRuntime [descriptor iri system]
  c/Lifecycle
  (start [this]
    (log/info :msg "Starting Arachne runtime" :iri iri)
    (update this :system c/start))
  (stop [this]
    (log/info :msg "Stopping Arachne runtime" :iri iri)
    (update this :system c/stop)))

(defn- find-runtimes
  "Get the IRIs of all the runtimes currently found in the descriptor"
  [descriptor]
  (map first
    (d/query descriptor
      '[:bgp [?rt :arachne.runtime/components _]])))

(deferror ::runtime-not-found
  :message "Runtime `:missing` was not found."
  :explanation "The Arachne runtime was instructed to start using a runtime entity identified by `:missing`. However, no such IRI could be found in the descriptor that was provided.

   The descriptor did, however, contain the following runtime entities:

   :found-formatted"
  :suggestions ["Select a runtime that is actually present in the descriptor"
                "Make sure there are no typos in the runtime IRI"]
  :ex-data-docs {:missing "The runtime that could not be found"
                 :found "IDs of runtimes that were actually in the descriptor"
                 :found-formatted "Bullet list of runtimes in the descriptor"})

(s/def ::runtime #(instance? ArachneRuntime %))

(s/fdef init
  :args (s/cat :descriptor d/descriptor? :iri ::g/iri)
  :ret ::runtime)

(defn init
  "Given a descriptor and an IRI representing a Runtime resource
  entity, return an instantiated (but unstarted) ArachneRuntime
  object."
  [descriptor iri]
  (e/assert-args `init descriptor iri)
  (->ArachneRuntime descriptor iri (system descriptor iri)))

(s/fdef lookup
  :args (s/cat :runtime ::runtime  :iri ::g/iri))

(defn lookup
  "Given a runtime and an IRI, return the associated component
  instance (if present.)"
  [runtime iri]
  (e/assert-args `lookup runtime iri)
  (get-in runtime [:system iri]))
