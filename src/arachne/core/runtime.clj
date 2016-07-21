(ns arachne.core.runtime
  "Dependency Injection and lifecycle management"
  (:require [com.stuartsierra.component :as component]
            [arachne.core.config :as cfg]
            [arachne.core.util :as util]
            [arachne.core.runtime.specs]
            [clojure.tools.logging :as log]
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
  "Given a set of root entity ids return all components in the given
  configuration that are dependencies of one or more roots. Components are
  returned as entity maps."
  [cfg roots]
  (let [deps (cfg/q cfg '[:find [?e ...]
                          :in $ [?root ...] %
                          :where
                          (dependency ?root ?e)]
               roots
               component-dependency-rules)]
    (map #(cfg/pull cfg component-pull %)
      (set (concat roots deps)))))

(defn- instantiate
  "Invoke a Component definition's constructor function to return a runtime
  instance of the component."
  [cfg eid ctor]
  (let [ctor-fn (util/require-and-resolve ctor)]
    (try
      (ctor-fn cfg eid)
      (catch Throwable t
        (throw
          (ex-info
            (format "An exception was thrown while attempting to instantiate component %s using constructor %s"
              eid ctor)
                 {:cfg cfg
                  :eid eid
                  :ctor ctor
                  :ctor-fn ctor-fn} t))))))
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

(defn- dependency-map
  "Given a collection of component maps, return a Component dependency map to
  pass to component/system-using."
  [components]
  (reduce (fn [acc component-map]
            (reduce (fn [acc dep]
                      (assoc-in acc [(:db/id component-map)
                                     (:arachne.component.dependency/key dep)]
                        (:db/id (:arachne.component.dependency/entity dep))))
              acc (:arachne.component/dependencies component-map)))
        {} components))

(defn- system
  "Given a configuration and set of root Arachne IDs, return an (unstarted)
  Component system"
  [cfg roots]
  (let [components (components cfg roots)]
    (component/system-using
      (merge (component/system-map) (system-map cfg components))
      (dependency-map components))))

(defrecord ArachneRuntime [config system roots]
  component/Lifecycle
  (start [rt]
    (log/info "Starting Arachne runtime")
    (update rt :system component/start))
  (stop [rt]
    (log/info "Stopping Arachne runtime")
    (update rt :system component/stop)))

(defn init
  "Given a configuration and collection of root entity IDs or lookup refs,
  return an instantiated (but unstarted) ArachneRuntime object."
  [config roots]
  (util/validate-args `init config roots)
  (let [root-eids (map #(:db/id (cfg/pull config [:db/id] %)) roots)
        sys (system config root-eids)]
    (->ArachneRuntime config sys (set root-eids))))

(defn lookup
  "Given a runtime and an entity ID or lookup ref, return the associated
  component instance (if present.)"
  [rt entity-ref]
  (util/validate-args `lookup rt entity-ref)
  (let [eid (:db/id (cfg/pull (:config rt) [:db/id] entity-ref))]
    (get-in rt [:system eid])))
