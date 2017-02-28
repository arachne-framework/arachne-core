(ns arachne.core.config.reified-ref
  (:require [arachne.core.config :as cfg]
            [arachne.error :as e :refer [error deferror]]
            [clj-fuzzy.levenshtein :as lev]))

(deferror ::unresolved-ref
  :message "Reference to `:value` not found in the config.:best-guess-msg-str"
  :explanation "The configuration referenced an entity that had a value of `:value` for its identity attribute `:attr`. However, no such identity could be found in the config.

  :candidates-str"
  :suggestions ["Fix any typos in the reference name"
                "Ensure that the referenced entity exists in your configuration"]
  :ex-data-docs {:value "The missing identity"
                 :attr "The identity attribute"
                 :best-guess-msg-str "Formatted string of the best guess"
                 :candidates "Possiblities by Levenshtein distance"
                 :similar-values-str "Formatted string list of candidates"
                 :cfg "The configuration"})

(defn- missing-reference-error
  "Throw a missing reference error if an entity with the selected attr and value cannot be found."
  [cfg rr-attr rr-value]
  (let [possible-values (cfg/q cfg '[:find [?v ...]
                                     :in $ ?attr
                                     :where [_ ?attr ?v]] rr-attr)
        candidates (->> possible-values
                     (map #(vector % (lev/distance (str rr-value) (str %))))
                     (filter (fn [[s dist]] (< dist (/ (count (str s)) 3))))
                     (sort-by second)
                     (take 3))
        [[best best-dist] & _] candidates]
    (error ::unresolved-ref {:attr rr-attr
                             :value rr-value
                             :cfg cfg
                             :candidates candidates
                             :candidates-str (if (seq candidates)
                                               (format "Some similar values in the config:\n\n%s\n\nDid you mean to type one of these instead?"
                                                 (e/bullet-list
                                                   (map (fn [[str dist]]
                                                          (format "`%s` (edit distance: %s)" str dist)) candidates)))
                                               "")
                             :best-guess-msg-str (when (and (number? best-dist) (< best-dist 5))
                                                   (format " Did you mean `%s`?" best))})))

(defn- original-tx-provenance
  "Return an entity map of the reproducible portion of the original transaction's provenance
   metadata"
  [cfg original-tx]
  (assoc
   (cfg/pull cfg '[:arachne.transaction/source-file
                   :arachne.transaction/source-line
                   :arachne.transaction/class
                   :arachne.transaction/function
                   :arachne.transaction/source]
     original-tx)
    :db/id (cfg/tempid :db.part/tx)))

#_(defn- resolve-reified-reference
    "Resolve a reified reference and return a config with the value replaced. Throw an exception if
     the value can't be found."
    [cfg [entity attr rr rr-attr rr-value tx]]
    (let [replacement (cfg/q cfg '[:find ?e .
                                   :in $ ?attr ?val
                                   :where [?e ?attr ?val]]
                        rr-attr rr-value)]
      (if replacement
        (cfg/update cfg
          (concat [[:db/retract entity attr rr]
                   [:db/add entity attr replacement]]
            (original-tx-provenance cfg tx)))
        (missing-reference-error cfg entity attr rr rr-attr rr-value tx))))

#_(defn- resolve-reified-references
    "Replace all ReifiedReference entities in the config with a direct entity reference. Throws an
     error if a reference cannot be found."
    [cfg]
    (let [rrefs (cfg/q cfg '[:find ?entity ?attr-ident ?rr ?rr-attr ?rr-value ?tx
                             :where
                             [?rr :arachne.reified-reference/attr ?rr-attr]
                             [?rr :arachne.reified-reference/value ?rr-value]
                             [?entity ?attr ?rr ?tx]
                             [?attr-eid :db/ident ?attr]
                             [?attr-eid :db/ident ?attr-ident]
                             ])]
      (reduce resolve-reified-reference cfg rrefs)))

(defn- resolve-rref
  "Find the target eid for an rref"
  [cfg attr value]
  (or (cfg/q cfg '[:find ?e .
                   :in $ ?a ?v
                   :where [?e ?a ?v]] attr value)
    (missing-reference-error cfg attr value)))

(defn- replace-rref
  "Given a config, and a tuple of a source rref eid and target eid, replace all occurences and attributes
   of the source with the target"
  [cfg [src target]]
  (let [attr-idents (vec (map vector (keys (dissoc (cfg/pull cfg '[*] src)
                            :db/id :arachne.reified-reference/attr :arachne.reified-reference/value))))
        attrs (cfg/q cfg '[:find ?tx ?attr-ident ?v
                           :in $ $attrs ?rr
                           :where
                           [$attrs ?attr-ident]
                           [$ ?rr ?attr-ident ?v ?tx]]
                attr-idents src)
        values (cfg/q cfg '[:find ?tx ?e ?a
                            :in $ ?rr
                            :where
                            [?e ?a ?rr ?tx]
                            [?ae :db/ident ?attr]
                            [?ae :db/ident ?a]]
                 src)
        attr-transactions (map (fn [[tx a v]]
                                 [[:db/add target a v]
                                  [:db/retract src a v]
                                  (original-tx-provenance cfg tx)])
                            attrs)
        value-transactions (map (fn [[tx e a]]
                                  [[:db/add e a target]
                                   [:db/retract e a src]
                                   (original-tx-provenance cfg tx)])
                             values)
        transactions (concat attr-transactions
                             value-transactions
                             [[[:db.fn/retractEntity src]
                               {:db/id (cfg/tempid :db.part/tx)
                                :arachne.transaction/source :core
                                :arachne.transaction/function (keyword `replace-rref)}
                               ]])]
    (reduce (fn [cfg t]
              (when-not (empty? t)
                (cfg/update cfg t))) cfg transactions)))

(defn resolve-reified-references
  "Replace all ReifiedReference entities in the config with a direct entity reference. Throws an
   error if a reference cannot be found."
  [cfg]
  (let [rrefs (cfg/q cfg '[:find ?rr ?attr ?value
                           :where
                           [?rr :arachne.reified-reference/attr ?attr]
                           [?rr :arachne.reified-reference/value ?value]])
        values (map (fn [[rr attr value]] [rr (resolve-rref cfg attr value)]) rrefs)]
    (reduce replace-rref cfg values)))