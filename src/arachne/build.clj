(ns arachne.build
  "Application entry point for running an Arachne application"
  (:require [arachne.core :as a]
            [com.stuartsierra.component :as c]
            [clojure.string :as str]
            [arachne.log :as log]))

(defn -main
  [& [module runtime]]
  (if (or (str/blank? module) (str/blank? runtime))
    (println "usage: arachne <module> <runtime>

Run an Arachne application, terminating it as soon as it has successfully started.

This is useful for 'build'-like tasks where you want to perform some processing as defined by an
Arachne config, but not start a long-lived appliation. The tasks of building assets or deploying
an application often falls into this category, for example.

Arguments:

<module> - A Clojure keyword identifying an Arachne module or application. Usually this is
          defined in an `arachne.edn` file on the root of the classpath in the current project.

<runtime> - A Clojure keyword identifying the runtime that should be started. The runtime must
          have been defined in the Arachne configuration for the module specified by <module>.

The application will terminate gracefully when processing is complete.")
    (do
      (log/info :msg "Launching Arachne application" :name module :runtime runtime)
      (let [cfg (a/config (read-string module))
            _ (log/info :cfg "cfg")
            rt (a/runtime cfg (read-string runtime))]
        (c/stop (c/start rt))
        (System/exit 0)))))