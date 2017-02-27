(ns arachne.core.config.impl.debug
  "Debugging implementation of an Arachne configuration. Doesn't actually implement a config,
   merely pretty-prints all the transaction data it recieves for debugging purposes."
  (:require [arachne.core.config :as cfg]
            [arachne.core.util :as util]
            [arachne.error :as e :refer [error]]
            [clojure.pprint :as pprint]
            [io.aviso.ansi :as ansi])
  (:import [java.util UUID]))

(defn- cfprint
  [f & more]
  (print (f (apply str (interpose " " more)))))

(defrecord DebugConfig []
  cfg/Configuration
  (init- [this _] this)
  (update- [this txdata]
    (cfprint ansi/blue "\nDebug Config: would have transacted config data:\n")
    (cfprint ansi/blue "▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼\n")
    (binding [*print-namespace-maps* false]
      (pprint/pprint txdata))
    (cfprint ansi/blue "▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲\n")
    this)
  (query- [this query other-sources] nil)
  (pull- [this expr lookup-or-eid]
    (when (= expr '[:arachne.transaction/source]) true))
  (resolve-tempid- [this tempid]
    (rand-int Integer/MAX_VALUE))
  Object
  (toString [this]
    (str "#DebugConfig[]")))

(defmethod print-method DebugConfig
  [cfg writer]
  (.write writer (str cfg)))
