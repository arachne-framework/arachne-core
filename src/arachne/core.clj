(ns arachne.core
  "The core Arachne module that bootstraps everything else"
  (:require [arachne.core.module :as m]
            [arachne.core.config :as cfg]
            [arachne.core.config.model :as model]
            [arachne.core.config.specs :as cfg-spec]
            [arachne.core.runtime :as rt]
            [arachne.core.config.validation :as v]
            [arachne.core.config.script :as script]
            [arachne.core.util :as util]
            [arachne.core.schema :as schema]
            [arachne.error :as e]
            [clojure.spec :as s]))

(defn ^:no-doc instance-ctor
  "Component constructor that defines components by resolving a var"
  [component]
  @(util/require-and-resolve (:arachne.component/instance component)))

(defn- add-instance-constructors
  "Implement :arachne.component/instance in terms of :arachne.component/constructor"
  [cfg]
  (let [components (cfg/q cfg '[:find [?c ...]
                                :where
                                [?c :arachne.component/instance ?var]])]
    (if (empty? components)
      cfg
      (cfg/with-provenance :module `add-instance-constructors
        (cfg/update cfg
          (for [c components]
            {:db/id c
             :arachne.component/constructor :arachne.core/instance-ctor}))))))

(defn- add-default-config-roots
  "If the user hasn't built any other configuration entities, then assume that everything belongs to the default config."
  [cfg]
  (let [cfg-entities (cfg/q cfg '[:find [?cfg ...]
                                  :in $ %
                                  :where
                                  [?type :db/ident :arachne/Configuration]
                                  (type ?type ?cfg)]
                       model/rules)]
    (if (< 1 (count cfg-entities))
      cfg
      (let [runtimes (cfg/q cfg '[:find [?rt ...]
                                  :in $ %
                                  :where
                                  [?type :db/ident :arachne/Runtime]
                                  (type ?type ?rt)]
                       model/rules)]
        (if-not (seq runtimes)
          cfg
          (cfg/with-provenance :module `add-default-config-roots
            (cfg/update cfg [{:arachne/id :arachne.core/default-configuration
                              :arachne.configuration/roots runtimes}])))))))


(defn ^:no-doc schema
  "Return the schema for the core module"
  []
  schema/schema)

(defn ^:no-doc configure
  "Configure the core module"
  [cfg]
  (-> cfg
    (add-instance-constructors)
    (v/add-core-validators)
    (add-default-config-roots)))

(defn ^:no-doc initialize
  "Initialize function for the core module. Add a default config entity."
  [cfg]
  (cfg/with-provenance :module `initialize
    (cfg/update cfg [{:arachne/id :arachne.core/default-configuration
                      :arachne/instance-of {:db/ident :arachne/Configuration}}])))

(s/fdef config
  :args (s/cat :module (s/alt :name :arachne/name
                              :definition :arachne.core.module/definition)
               :throw-validation-errors? boolean?))

(defn config
  "Build and initialize a new Arachne configuration object.

  Takes the name of an Arachne module, which must be defined in an `arachne.edn` file on the root
  of the classpath.

  Alternatively, instead of an application name, you may pass a module definition map directly.
  The map must define a module that is *not* present in any `arachne.edn` file.

  The optional second argument specifies whether configuration validation errors will cause an
  exception to be thrown (they will be logged regardless.) Usually you should set this true only
  when debugging, since an invalid config can cause any number of difficult-to-debug issues if it
  is actually used."
  ([module] (config module true))
  ([module throw-validation-errors?]
   (e/assert-args `config module throw-validation-errors?)
   (m/config module throw-validation-errors?)))

(defn ^:no-doc build-config
  "Build a new Arachne config, using an application/module that is defined on the fly.

   This is the same as the previous API, and is preserved as it is useful for testing"
  ([deps initializer] (build-config deps initializer true))
  ([deps initializer throw-validation-errors?]
   (config {:arachne/name :arachne.core/app-module
            :arachne/dependencies deps
            :arachne/inits [initializer]} throw-validation-errors?)))

(s/fdef runtime
  :args (s/cat :config ::cfg-spec/config
               :arachne-id (s/and keyword? namespace)))

(defn runtime
  "Create a new Arachne runtime from the given configuration and the :arachne/id
  of the root runtime entity"
  [cfg arachne-id]
  (e/assert-args `runtime cfg arachne-id)
  (rt/init cfg [:arachne/id arachne-id]))