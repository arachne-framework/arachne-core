(ns arachne.core.runtime
  "Dependency Injection and lifecycle management"
  (:require [com.stuartsierra.component :as c]
            [com.stuartsierra.dependency :as dep]
            [arachne.core.config :as cfg]
            [arachne.core.config.model :as cfg-model]
            [arachne.core.util :as util]
            [arachne.error :as e :refer [deferror error]]
            [arachne.core.runtime.specs]
            [clojure.tools.logging :as log]
            [clojure.spec :as s]
            [clojure.set :as set]))

(def ^:private component-dependency-rules
  "Datalog rule for determininge transitive component dependencies"
  '[[(dependency ?component ?dep)
     [?component :arachne.component/dependencies ?mapping]
     [?mapping :arachne.component.dependency/entity ?dep]]
    [(dependency ?component ?dep)
     [?component :arachne.component/dependencies ?mapping]
     [?mapping :arachne.component.dependency/entity ?mid]
     (dependency ?mid ?dep)]])

(def ^:private component-pull
  "Pull expression for retrieving necessary data about a component"
  [:db/id
   :arachne.component/constructor
   {:arachne.component/dependencies [:arachne.component.dependency/key
                                     :arachne.component.dependency/entity]}])


(defn- components
  "Given a runtime entity ID, return all components that are part of the
  runtime. Components are returned as entity maps."
  [cfg runtime-eid]
  (let [roots (cfg/q cfg '[:find [?root ...]
                           :in $ ?rt
                           :where [?rt :arachne.runtime/components ?root]]
                runtime-eid)
        deps (cfg/q cfg '[:find [?e ...]
                          :in $ [?root ...] %
                          :where
                          (dependency ?root ?e)]
               roots
               component-dependency-rules)]
    (map #(cfg/pull cfg component-pull %)
      (set (concat roots deps)))))

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
  "Given a configuration and collection of component maps, instantiates the
  components and return a Component system map to pass to component/system-map."
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

(defn- dependency-map
  "Given a collection of component entities, return a Component dependency map to
  pass to component/system-using. Each component will have its dependencies
  associated under the key specified in the config, as well as the entity ID of each dependency."
  [components]
  (into {}
    (filter #(not-empty (second %))
      (map (fn [c]
             [(:db/id c) (component-dependency-map c)])
        components))))

(defn- system
  "Given a configuration and the entity ID of a Runtime entity, return an
  (unstarted) Component system"
  [cfg runtime-eid]
  (let [components (components cfg runtime-eid)
        dep-map (dependency-map components)]
    (c/system-using
      (merge (c/system-map) (system-map cfg components))
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
               [?class :arachne.component/spec ?spec]]
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

(defn validate-and-start
  "Start a Component system, validating each component before it starts"
  [system cfg]
  (update-system system (keys system) validate-and-start-component cfg))

(defrecord ArachneRuntime [config system runtime]
  c/Lifecycle
  (start [rt]
    (log/info "Starting Arachne runtime")
    (update rt :system validate-and-start config))
  (stop [rt]
    (log/info "Stopping Arachne runtime")
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

(defn init
  "Given a configuration and an entity ID or lookup ref representing a Runtime
  entity, return an instantiated (but unstarted) ArachneRuntime object."
  [config runtime-ref]
  (e/assert-args `init config runtime-ref)
  (let [runtime-eid (e/wrap-error (:db/id (cfg/pull config [:db/id] runtime-ref))
                      [::cfg/entity-not-found]
                      ::runtime-not-found (let [found (find-runtimes config)]
                                            {:missing runtime-ref
                                             :found found
                                             :found-formatted (e/bullet-list found)}))
        sys (system config runtime-eid)]
    (->ArachneRuntime config sys runtime-eid)))

(defn lookup
  "Given a runtime and an entity ID or lookup ref, return the associated
  component instance (if present.)"
  [rt entity-ref]
  (e/assert-args  `lookup rt entity-ref)
  (let [eid (:db/id (cfg/pull (:config rt) [:db/id] entity-ref))]
    (get-in rt [:system eid])))
