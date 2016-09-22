(ns arachne.core.config.validation
  (:require [arachne.core.config :as cfg]
            [arachne.core.util :as u]
            [arachne.core.util :as util]
            [clojure.tools.logging :as log]))

(defn- run-validator
  "Run the given validator (given as a keyword) against the specified config. If
  the validator throws an error, *returns* the throwable, otherwise returns nil."
  [cfg validator]
  (let [f (util/require-and-resolve validator)]
    (@f cfg)))

(defn- throwable? [x] (instance? java.lang.Throwable x))

(util/deferror ::validation-errors
  "Found :count errors while validating the configuration")

(defn validate
  "Given a config, validate according to all the validators present in the
  config. Logs each validation error, then throws if any errors
  were present."
  [cfg]
  (let [validators (cfg/q cfg '[:find [?v ...]
                                :where
                                [_ :arachne.configuration/validators ?v]])
        errors (->> validators
                 (pmap #(run-validator cfg %))
                 (apply concat)
                 (filter identity))]
    (if (empty? errors)
      cfg
      (do
        (doseq [error errors]
          (log/error "Config Validation Error" (::message error)
            (dissoc error ::message)))
        (util/error ::validation-errors {:count (count errors)
                                         :cfg cfg
                                         :errors errors})))))

(def ^:private core-validators
  [:arachne.core.validators/min-cardinality
   :arachne.core.validators/max-cardinality])

(defn add-core-validators
  "Add core config validator functions to the config"
  [cfg]
  (let [cfg-eids (cfg/q cfg '[:find [?cfg ...]
                              :where [?cfg :arachne.configuration/roots _]])]
    (cfg/with-provenance :module `add-core-validators
      (cfg/update cfg (for [v core-validators, c cfg-eids]
                        [:db/add c :arachne.configuration/validators v])))))