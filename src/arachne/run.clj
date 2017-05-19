(ns arachne.run
  "Application entry point for running an Arachne application"
  (:require [arachne.core :as a]
            [com.stuartsierra.component :as c]
            [clojure.string :as str]
            [arachne.log :as log]))

(defn -main
  [& [module runtime]]
  (if (or (str/blank? module) (str/blank? runtime))
    (println "usage: arachne <module> <runtime>

Start an Arachne application.

Arguments:

<module> - A Clojure keyword identifying an Arachne module or application. Usually this is
          defined in an `arachne.edn` file on the root of the classpath in the current project.

<runtime> - A Clojure keyword identifying the runtime that should be started. The runtime must
          have been defined in the Arachne configuration for the module specified by <module>.


The application will not terminate automatically; it will start the runtime and keep running
indefinitely until you terminate it manually with a kill signal.")
    (do
      (log/info :msg "Launching Arachne application" :name module :runtime runtime)
      (let [cfg (a/config (read-string module))
            _ (log/info :cfg "cfg")
            rt (a/runtime cfg (read-string runtime))]
        (c/start rt)))))
