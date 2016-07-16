(ns arachne.core.config.impl.multiplex
  (:require [clojure.walk :as w]
            [clojure.data :as data]
            [arachne.core.config.impl.datomic :as datomic]
            [arachne.core.config.impl.datascript :as datascript]
            [arachne.core.config :as cfg]))

(defn- value-coll?
  "Is it a collection, but not a map?"
  [v]
  (and (instance? java.util.Collection v)
    (not (instance? java.util.Map v))))

(defn- normalize
  "Normalize the returned value to make it possible to compare equivalencies"
  [value]
  (w/postwalk
      (fn [form]
            (cond
              (number? form) 0
              (value-coll? form) (frequencies form)
              :else form))
    (w/prewalk
      (fn [form]
              (cond
                (instance? java.util.Map form) (into {} form)
                (instance? java.util.Set form) (into #{} form)
                (instance? java.util.List form) (into [] form)
                :else form))
      value)))

(defn- assert-equivalent!
  "Throw an exception if the datomic result is not equivalent to the datascript
  result.

  Equivalence is a relaxed definition of equality: all numbers are equal to all
  other numbers, and all sequences are compared without regard for order."
  [datomic-result datascript-result]
  (let [d (normalize datomic-result)
        ds (normalize datascript-result)]
    (when-not (= d ds)
      (throw (ex-info "Error when multiplexing an operation across DataScript and Datomic configs: the results were not equivalent"
               {:datomic-result datomic-result
                :datascript-result datascript-result
                :diff (data/diff d ds)})))))

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
      datomic-result)))

(defn ctor
  "Construct and return an uninitialized instance of a MultiplexedConfig"
  []
  (->MultiplexedConfig (datomic/ctor) (datascript/ctor) {}))
