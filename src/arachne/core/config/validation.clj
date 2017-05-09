(ns arachne.core.config.validation
  (:require [arachne.core.config :as cfg]
            [arachne.core.config.model :as model]
            [arachne.core.util :as u]
            [arachne.core.util :as util]
            [arachne.error :as e :refer [deferror error]]
            [clojure.string :as str]
            [arachne.log :as log]))

(defn- run-validator
  "Run the given validator (given as a keyword) against the specified config. If
  the validator throws an error, *returns* the throwable, otherwise returns nil."
  [cfg validator]
  (let [f (util/require-and-resolve validator)]
    (try (@f cfg)
      (catch Throwable t [t]))))

(defn- throwable? [x] (instance? java.lang.Throwable x))

(deferror ::validation-errors
  :message "Found :count errors while validating the configuration"
  :explanation "After it was constructed, the configuration was found to be invalid. There were :count different errors: :messages"
  :suggestions ["Check the errors key in the ex-data to retrieve the individual validation exceptions. You can then inspect them individually using `arachne.error/explain`."]
  :ex-data-docs {:errors "The collection of validation exceptions"
                 :count "The number of validation exceptions"
                 :messages "A string representation of all the error messages."
                 :cfg "The invalid configuration"})

(defn validate
  "Given a config, validate according to all the validators present in the
  config. Logs each validation error, then optionally throws if any errors
  were present.

  Validators may either throw their error, or return a sequence of Throwable
  objects. They should return nil if there are no validation problems."
  [cfg throw?]
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
          (log/error :exception error))
        (if-not throw?
          cfg
          (error ::validation-errors
            {:count (count errors)
             :messages (apply str "\n" (str/join "\n - " (map #(.getMessage %) errors)))
             :cfg cfg
             :errors errors}))))))

(def ^:private core-validators
  [:arachne.core.validators/min-cardinality
   :arachne.core.validators/max-cardinality])

(defn add-validators
  "Add the given config validator functions to the config"
  [cfg validators]
  (let [cfg-eids (cfg/q cfg '[:find [?cfg ...]
                              :in $ %
                              :where
                              [?type :db/ident :arachne/Configuration]
                              (type ?type ?cfg)]
                   model/rules)]
    (cfg/with-provenance :module `add-validators
      (cfg/update cfg (for [v validators, c cfg-eids]
                        [:db/add c :arachne.configuration/validators v])))))

(defn add-core-validators
  "Add core config validator functions to the config"
  [cfg]
  (add-validators cfg core-validators))
