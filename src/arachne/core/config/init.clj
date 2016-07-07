(ns arachne.core.config.init
  "Initialziation & init script support for user configuration values."
  (:refer-clojure :exclude [update])
  (:require [arachne.core.config :as cfg]
            [clojure.edn :as edn]))

(def ^:private ^:dynamic *config*)

(defn update
  "Update the current configuration by applying a function which takes the
  existing config and returns a new one. Additional arguments are passed to the
  supplied function."
  [f & args]
  (if-not (bound? #'*config*)
    (throw (ex-info "Attemped to update Arachne config outside of a configuration script. You should only use the init-script configuration API during the configuration phase." {}))
    (apply swap! *config* f args)))

(defn initialize
  "Initialize a configuration with a script, form or literal txdata."
  [cfg initializer]
  (binding [*config* (atom cfg)]
    (cond
      (string? initializer) (load-file initializer)
      (list? initializer) (eval initializer)
      :else (update cfg/update initializer))
    @*config*))