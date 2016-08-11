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

(util/deferror ::error-instantiating
  "An exception was thrown while attempting to instantiate component :eid (Arachne ID: :aid) using constructor :ctor")

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
                     (util/error ::error-instantiating
                       {:cfg cfg
                        :eid eid
                        :aid (or (cfg/attr cfg eid :arachne/id) "none")
                        :ctor ctor} t)))]
    (if (map? instance)
      (merge instance (cfg/pull cfg '[*] eid))
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

(defn- dependency-key
  "Determine the dependency key, given a dependency entity"
  [dep]
  (or
    (:arachne.component.dependency/key dep)
    (keyword (str (:db/id (:arachne.component.dependency/entity dep))))))

(defn- dependency-map
  "Given a collection of component maps, return a Component dependency map to
  pass to component/system-using."
  [components]
  (reduce (fn [acc component-map]
            (reduce (fn [acc dep]
                      (assoc-in acc [(:db/id component-map)
                                     (dependency-key dep)]
                        (:db/id (:arachne.component.dependency/entity dep))))
              acc (:arachne.component/dependencies component-map)))
        {} components))

(defn- system
  "Given a configuration and the entity ID of a Runtime entity, return an
  (unstarted) Component system"
  [cfg runtime-eid]
  (let [components (components cfg runtime-eid)]
    (component/system-using
      (merge (component/system-map) (system-map cfg components))
      (dependency-map components))))

(defrecord ArachneRuntime [config system runtime]
  component/Lifecycle
  (start [rt]
    (log/info "Starting Arachne runtime")
    (update rt :system component/start))
  (stop [rt]
    (log/info "Stopping Arachne runtime")
    (update rt :system component/stop)))

(defn init
  "Given a configuration and an entity ID or lookup ref representing a Runtime
  entity, return an instantiated (but unstarted) ArachneRuntime object."
  [config runtime-ref]
  (util/validate-args `init config runtime-ref)
  (let [runtime-eid (:db/id (cfg/pull config [:db/id] runtime-ref))
        sys (system config runtime-eid)]
    (->ArachneRuntime config sys runtime-eid)))

(defn lookup
  "Given a runtime and an entity ID or lookup ref, return the associated
  component instance (if present.)"
  [rt entity-ref]
  (util/validate-args `lookup rt entity-ref)
  (let [eid (:db/id (cfg/pull (:config rt) [:db/id] entity-ref))]
    (get-in rt [:system eid])))

(util/deferror ::missing-dependency
  "Runtime dependency :dependency-eid (Arachne id: :dependency-id) of :instance-eid (Arachne id: :instance-id) could not be found using key :key")

(util/deferror ::not-a-dependency
  "Could not resolve dependency instance :dependency-eid (Arachne id: :dependency-id) of :instance-eid (Arachne id: :instance-id) because that dependency is not declared in the configuration.")

(defn dependency-instance
  "Given a concrete component instance, the config, and the eid of a dependency,
  return the concrete runtime dependency instance (no matter what key it was
  associated under)"
  [instance cfg dependency-eid]
  (if-let [mapping-eid (cfg/q cfg '[:find ?m .
                                    :in $ ?entity ?dep
                                    :where
                                    [?entity :arachne.component/dependencies ?m]
                                    [?m :arachne.component.dependency/entity ?dep]]
                         (:db/id instance) dependency-eid)]
    (let [key (or (cfg/attr cfg mapping-eid :arachne.component.dependency/key)
                (keyword (str dependency-eid)))]
      (or (get instance key)
          (util/error ::missing-dependency
            {:dependency-eid dependency-eid
             :dependency-id (or (cfg/attr cfg dependency-eid :arachne/id) "none")
             :instance-eid (:db/id instance)
             :instance-id (or (:arachne/id instance) "none")
             :key key})))
    (util/error ::not-a-dependency
      {:dependency-eid dependency-eid
       :dependency-id (or (cfg/attr cfg dependency-eid :arachne/id) "none")
       :instance-eid (:db/id instance)
       :instance-id (or (:arachne/id instance) "none")})))
