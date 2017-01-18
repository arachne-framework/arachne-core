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
            [clojure.spec :as s]
            [arachne.core.util :as u])
  (:import (java.io FileNotFoundException)
           (clojure.lang Compiler$CompilerException)))

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
               :blank-cfg (s/? ::cfg-spec/config)
               :throw-validation-errors? (s/? boolean?)))

(defn- missing-implementation?
  "Determine if an exception indicates a missing implementation"
  [e]
  (and
    (instance? Compiler$CompilerException e)
    (instance? FileNotFoundException (.getCause e))))

(defn- default-blank-cfg
  "Get either a Datomic or a DataScript blank config, depending on what's in the classpath.
   DataScript if they both are."
  []
  (try
    (@(u/require-and-resolve 'arachne.core.config.impl.datascript/new))
    (catch Throwable ds-e
      (if (missing-implementation? ds-e)
        (@(u/require-and-resolve 'arachne.core.config.impl.datomic/new))
        (throw ds-e)))))

(def
  ^{:doc
    "Initialize a new Arachne configuration object for this application

    Arguments are:

      - module (mandatory): Either the name of an Arachne module defined in an `arachne.edn` file
        on the classpath, or a direct module definition map. If a module definition map is
        provided, it should have a unique `:arachne/name` that is *not* present in any
        `arachne.edn` file on the classpath, to avoid conflicts.

      - blank-cfg (optional): A blank, uninitialized configuration. To obtain such an
        implementation, call:
          - if using Datomic, `arachne.core.config.impl.datomic/new`
          - if using Datascript, `arachne.core.config.impl.datascript/new`
          - for module testing, `arachne.core.config.impl.multiplex/new`. It is important to use
            this when developing a module to ensure that the result is compatible with both Datomic
            and DataScript configurations.

        Defaults to whichever of Datomic and DataScript are present on the classpath. If both are,
        DataScript is used because it has a faster boot time.
v
      - throw-validation-errors? (optional): Set to false if a configuration should be returned
        even if it contains validation errors. Defaults to true. Validation errors will be logged
        in either case. Usually, this should be set to false only while debugging, since an invalid
        config can cause any number of difficult-to-debug issues if it is actually used to start an
        application."

    :arglists '([module <blank-cfg> <throw-validation-errors?>])}
  config
  (fn [& [module & more :as args]]
    (let [conformed (s/conform (:args (s/get-spec `config)) args)]
      (m/config module
                (or (:blank-cfg conformed) (default-blank-cfg))
                (if (contains? conformed :throw-validation-errors?)
                  (:throw-validation-errors? conformed)
                  true)))))

(defn ^:no-doc build-config
  "Build a new Arachne config, using an application/module that is defined on the fly.

   This is the same as the previous API, and is preserved as it is useful for testing.

   Because it is mostly used for testing, defaults to a multiplex config."
  ([deps initializer] (build-config deps initializer true))
  ([deps initializer throw-validation-errors?]
   (build-config deps initializer throw-validation-errors?
     (@(u/require-and-resolve 'arachne.core.config.impl.multiplex/new))))
  ([deps initializer throw-validation-errors? blank-cfg]
   (config {:arachne/name :arachne.core/app-module
            :arachne/dependencies deps
            :arachne/inits [initializer]}
           blank-cfg
           throw-validation-errors?)))

(s/fdef runtime
  :args (s/cat :config ::cfg-spec/config
               :arachne-id (s/and keyword? namespace)))

(defn runtime
  "Create a new Arachne runtime from the given configuration and the :arachne/id
  of the root runtime entity"
  [cfg arachne-id]
  (e/assert-args `runtime cfg arachne-id)
  (rt/init cfg [:arachne/id arachne-id]))