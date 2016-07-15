(ns arachne.core.config.impl.datomic
  (:require [clojure.walk :as w]
            [datomic.api :as d]
            [arachne.core.config.impl.common :as common]
            [arachne.core.config :as cfg])
  (:import [java.util UUID]))

(defn- init
  [schema-txes]
  (let [uri (str "datomic:mem://arachne-cfg-" (str (UUID/randomUUID)))
        _ (d/create-database uri)
        conn (d/connect uri)
        db (d/db conn)]
    (reduce (fn [db tx]
              (:db-after (common/with db tx
                                      d/with d/tempid d/resolve-tempid
                                      :db.part/db)))
            db schema-txes)))

(defrecord DatomicConfig [db tempids]
  cfg/Configuration
  (init- [this schema-txes]
    (assoc this :db (init schema-txes)))
  (update- [this txdata]
    (let [result (common/with db txdata d/with d/tempid d/resolve-tempid)]
      (assoc this :db (:db-after result)
                  :tempids (:arachne-tempids result))))
  (query- [this query other-sources]
    (apply d/q query (:db this) other-sources))
  (pull- [this expr lookup-or-eid]
    (d/pull (:db this) expr lookup-or-eid)))

(defn ctor
  "Construct and return an uninitialized instance of DatascriptConfig"
  []
  (->DatomicConfig nil nil))
