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
        db (d/db conn)
        schema-txes (common/add-and-replace-tempids
                      :db.part/db schema-txes d/tempid)]
    (reduce (fn [db tx]
              (:db-after (d/with db tx))) db schema-txes)))

(defrecord DatomicConfig [db]
  cfg/Configuration
  (init [this schema-txes]
    (assoc this :db (init schema-txes)))
  (update [this txdata]
    (assoc this :db (:db-after (d/with db (common/add-and-replace-tempids
                                            :db.part/user txdata d/tempid)))))
  (query [this query other-sources]
    (apply d/q query (:db this) other-sources))
  (pull [this expr lookup-or-eid]
    (d/pull (:db this) expr lookup-or-eid)))

(defn ctor
  "Construct and return an uninitialized instance of DatascriptConfig"
  []
  (->DatomicConfig nil))
