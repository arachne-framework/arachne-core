(ns arachne.core.config.impl.multiplex
  (:require [clojure.walk :as w]
            [clojure.data :as data]
            [arachne.core.config.impl.datomic :as datomic]
            [arachne.core.config.impl.datascript :as datascript]
            [arachne.core.config :as cfg]
            [arachne.core.util :as u]
            [arachne.error :as e :refer [deferror error]]))

(defn- value-coll?
  "Is it a collection, but not a map?"
  [v]
  (and (instance? java.util.Collection v)
    (not (instance? java.util.Map v))))

(def ^:private incomparable-attrs
  #{:db/valueType
    :db/cardinality
    :db/unique})

(defn- normalize
  "Normalize the returned value to make it possible to compare equivalencies"
  [value]
  (->> value
    (w/prewalk
      (fn [form]
        (cond
          (instance? java.util.Map form) (into {} form)
          (instance? java.util.Set form) (into [] form)
          (instance? java.util.List form) (into [] form)
          :else form)))
    #_(w/prewalk
      (fn [form]
        (if (and (map-entry? form) (incomparable-attrs (key form)))
          nil
          form)))
    (w/postwalk
      (fn [form]
        (cond
          (number? form) 0
          (value-coll? form) (frequencies form)
          :else form)))))


(deferror ::multiplex-error
  :message "Multiplex error"
  :explanation "To ensure compatibility with both the Datomic and Datascript implementations of Datomic, Arachne runs operations against both, and compares the results (adjusting for the innate differences between the platforms.)

  In this case, the two implementations returned results that were not equivalent."
  :suggestions ["In Arachne modules, only use the subest of functionality that is common to both Datomic and DataScript"]
  :ex-data-docs {:datomic-result "The Datomic result"
                 :datascript-result "The Datascript result"
                 :diff "A clojure.data/diff"})

(defn- assert-equivalent!
  "Throw an exception if the datomic result is not equivalent to the datascript
  result.

  Equivalence is a relaxed definition of equality: all numbers are equal to all
  other numbers, and all sequences are compared without regard for order."
  [datomic-result datascript-result]
  (let [d (normalize datomic-result)
        ds (normalize datascript-result)]
    (when-not (= d ds)
      (error ::multiplex-error {:datomic-result datomic-result
                                :datascript-result datascript-result
                                :diff (data/diff d ds)}))))

(defn- swap-eids
  "Given a data structure, replace any concrete Datomic eids with the
  corresponding Datascript EIDs, if they exist in the given mapping."
  [data mapping]
  (w/prewalk (fn [val]
               (if (and (number? val) (mapping val))
                 (mapping val)
                 val))
             data))

(defrecord MultiplexedConfig [datomic datascript eids]
  cfg/Configuration
  (init- [this schema-txes]
    (assoc this
      :datomic (cfg/init- datomic schema-txes)
      :datascript (cfg/init- datascript schema-txes)))
  (update- [this txdata]
    (let [d (cfg/update- datomic txdata)
          ds (cfg/update- datascript (swap-eids txdata eids))]
      (assoc this
        :datomic d
        :datascript ds
        :eids (merge eids
                     (into {} (map (fn [tempid]
                                     [(get-in d [:tempids tempid])
                                      (get-in ds [:tempids tempid])])
                                   (set (concat (keys (:tempids d))
                                                (keys (:tempids ds))))))))))
  (query- [_ query other-sources]
    (let [datomic-result (cfg/query- datomic query other-sources)
          datascript-result (cfg/query- datascript
                                        (swap-eids query eids)
                                        (swap-eids other-sources eids))]
      (assert-equivalent! datomic-result datascript-result)
      datomic-result))
  (pull- [_ expr lookup-or-eid]
    (let [datomic-result (cfg/pull- datomic expr lookup-or-eid)
          datascript-result (cfg/pull- datascript expr
                                       (swap-eids lookup-or-eid eids))]
      (assert-equivalent! datomic-result datascript-result)
      datomic-result))
  (resolve-tempid- [this tempid]
    (let [datomic-result (cfg/resolve-tempid- datomic tempid)
          datascript-result (cfg/resolve-tempid datascript tempid)]
      (when-not (= datascript-result (get eids datomic-result))
        (error ::multiplex-error {:datomic-result datomic-result
                                  :datascript-result datascript-result
                                  :eid-mappings eids}))
      datomic-result))
  Object
  (toString [this]
    (str "#MultiplexedConfig[" (hash-combine (hash datomic)
                                 (hash-combine (hash datascript) (hash eids)))
      "]")))

(defmethod print-method MultiplexedConfig
  [cfg writer]
  (.write writer (str cfg)))

(defn ctor
  "Construct and return an uninitialized instance of a MultiplexedConfig"
  []
  (->MultiplexedConfig (datomic/ctor) (datascript/ctor) {}))
