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
  [:rdf/about
   :arachne.component/constructor
   {:arachne.component/dependencies [:arachne.component.dependency/key
                                     :arachne.component.dependency/entity]}])

(defn components
  "Given a runtime IRI, return all components that are part of the
  runtime (as maps)."
  [d runtime]
  (let [roots (d/query d ['?c]
                '[:bgp [?rt :arachne.runtime/components ?c]]
                {'?rt runtime})
        root-components (map #(d/pull d (first %) component-pull) roots)
        with-deps (fn with-deps [c]
                    (let [deps (->> c :arachne.component/dependencies
                                 (map :arachne.component.dependency/entity)
                                 (map #(d/pull d % component-pull)))]
                      (conj (mapcat with-deps deps) c)))]
    (mapcat with-deps root-components)))

(deferror ::error-instantiating
  :message "Error instantiating component `:eid` (Arachne ID: `:aid`)"
  :explanation "An exception was thrown when trying to instantiate a component with entity ID `:eid` and Arachne ID `:aid`, while building the Arachne runtime. This component was configured to use a constructor function `:ctor`, but when it was invoked, instead of returning a component instance, the constructor function threw an exception of type `:ex-type`"
  :suggestions ["Investigate the cause below to determine the underlying reason for the failure."
                "Make sure `:ctor` is correct and error-free"]
  :ex-data-docs {:cfg "The configuration"
                 :eid "Entity ID of the component"
                 :aid "Arachne ID of the component"
                 :ctor "The name of the constructor that failed"
                 :ex-type "The type of the exception"})

(defn- instantiate
  "Invoke a Component definition's constructor function to return a runtime
  instance of the component. If the object returned by the constructor is a map,
  the values from a (cfg/pull '[*]) will be merged in."
  [cfg eid ctor]
  (let [instance (try
                   (let [ctor-fn (util/require-and-resolve ctor)]
                     (condp = (util/arity @ctor-fn)
                       0 (ctor-fn)
                       1 (ctor-fn (cfg/pull cfg '[*] eid))
                       (ctor-fn cfg eid)))
                   (catch Throwable t
                     (error ::error-instantiating
                       {:ex-type (.getName (class t))
                        :cfg cfg
                        :eid eid
                        :aid (cfg/attr cfg eid :arachne/id)
                        :ctor ctor} t)))]
    (if (map? instance)
      (merge instance
        (cfg/pull cfg '[*] eid)
        {:arachne/config cfg})
      instance)))

(defn- system-map
  "Given a descriptor and collection of component maps, instantiates the
  descriptor and return a Component system map to pass to component/system-map."
  [cfg components]
  (into {} (map (fn [component-map]
                  [(:db/id component-map)
                   (instantiate cfg
                     (:db/id component-map)
                     (:arachne.component/constructor component-map))])
             components)))

(defn- component-dependency-map
  "Given a component entity, return a Component dependency map"
  [component]
  (apply merge
    (map #(let [eid (:db/id (:arachne.component.dependency/entity %))
                key (:arachne.component.dependency/key %)]
           (if key
             {key eid, eid eid}
             {eid eid}))
      (:arachne.component/dependencies component))))

;;; TODO: FIXING THIS
(defn- dependency-map
  "Given a collection of component entities, return a Component
  dependency map to pass to component/system-using. Each component
  will have its dependencies associated under the key specified in the
  descriptor, as well as the IRI of each dependency."
  [components]
  (into {}
    (filter #(not-empty (second %))
      (map (fn [c]
             [(:db/id c) (component-dependency-map c)])
        components))))

(defn- system
  "Given a descriptor and the IRI of a runtime entity, return an (unstarted) Component system.
  (unstarted) Component system"
  [descriptor iri]
  (let [components (components descriptor iri)
        dep-map (dependency-map components)]
    (c/system-using
      (merge (c/system-map) (system-map descriptor components))
      dep-map)))

  (defn- update-system
  "Like Component's update-system, but invokes the update function passing [key
  component & args] instead of just [component & args]

  Utilizes some private functions in component, so possibly fragile across
  versions, however, Component seems fairly stable in componentpractice so it should be
  ok."
  [system component-keys f & args]
  (let [graph (c/dependency-graph system component-keys)]
    (reduce (fn [system key]
              (assoc system key
                     (-> (#'c/get-component system key)
                         (#'c/assoc-dependencies system)
                         (#'c/try-action system key f (cons key args)))))
            system
            (sort (dep/topo-comparator graph) component-keys))))

(defn- specs-for-component
  "Find all specs that should be run for the given component."
  [cfg eid]
  (cfg/q cfg '[:find ?spec ?class-ident
               :in $ ?entity %
               :where
               (type ?class ?entity)
               [?class :db/ident ?class-ident]
               [?class :arachne.type/component-specs ?spec]]
    eid
    cfg-model/rules))

(deferror ::component-failed-validation
  :message "Component `:eid` (Arachne ID: `:aid`) failed spec `:arachne.error/spec`"
  :explanation "When an Arachne runtime is started, each component is validated according to the specifications defined for components of its type. This occurs after its dependencies have been associated, immediately before its own `start` method is called.

  The component instance for :eid (Arachne ID: :aid) was expected to conform to `:spec`, because it is an instance of the class `:class`. However, it did not conform."
  :suggestions ["Make sure the component's defined constructor, `:ctor`, is building the component as it should."
                "Make sure that the spec `:arachne.error/spec` is defined correctly, and correct to apply to this component."]
  :ex-data-docs {:eid "The component's entity ID"
                 :aid "The component's Arachne ID"
                 :ctor "The component's constructor function"
                 :cfg "The configuration"
                 :class "The class that defined the spec"})

(defn- validate-component
  "Validate that a component satisfies the specficiations defined for it in the
  config. Throws or returns nil."
  [cfg eid obj]
  (doseq [[spec class] (specs-for-component cfg eid)]
    (e/assert spec obj ::component-failed-validation
      {:eid eid
       :aid (:arachne/id obj)
       :ctor (:arachne.component/constructor obj)
       :class class
       :cfg cfg})))

(defn- validate-and-start-component
  "Start a component, after validating it"
  [component key cfg]
  (validate-component cfg key component)
  (#'c/start component))

(defn- validate-and-start
  "Start a Component system, validating each component before it starts"
  [system cfg]
  (update-system system (keys system) validate-and-start-component cfg))

(defrecord ArachneRuntime [config system runtime]
  c/Lifecycle
  (start [rt]
    (log/info :msg "Starting Arachne runtime")
    (update rt :system validate-and-start config))
  (stop [rt]
    (log/info :msg "Stopping Arachne runtime")
    (update rt :system c/stop)))

(defn- find-runtimes
  "Get the Arachne IDs of all the runtimes currently found in the config"
  [config]
  (cfg/q config '[:find [?id ...]
                  :where
                  [?rt :arachne.runtime/components _]
                  [?rt :arachne/id ?id]]))

(deferror ::runtime-not-found
  :message "Runtime `:missing` was not found."
  :explanation "The Arachne runtime was instructed to start using a runtime entity identified by `:missing`. However, no such entity could be found in the config that was provided.

   The configuration did, however, contain the following runtime entities:

   :found-formatted"
  :suggestions ["Select a runtime that is actually present in the config"
                "Make sure there are no typos in the runtime ID"]
  :ex-data-docs {:missing "The runtime that could not be found"
                 :found "IDs of runtimes that were actually in the config"
                 :found-formatted "Bullet list of runtimes in the config"})

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
  (->ArachneRuntime descriptor (system descriptor iri) iri))

(s/fdef lookup
  :args (s/cat :runtime ::runtime  :iri ::g/iri))

(defn lookup
  "Given a runtime and an entity ID or lookup ref, return the associated
  component instance (if present.)"
  [runtime iri]
  (e/assert-args `lookup runtime iri)
  (get-in runtime [:system iri]))
