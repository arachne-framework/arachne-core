(ns arachne.core.config.specs
  (:require [clojure.spec.alpha :as s]
            [arachne.core.util :as u]))

(def config? (u/lazy-satisfies? arachne.core.config/Configuration))
(def tempid? (u/lazy-instance? arachne.core.config.Tempid))
(def date? (u/lazy-instance? java.util.Date))

(s/def ::config config?)

(s/def ::attribute (s/and keyword? namespace))

(s/def ::scalar (s/or :number number?
                      :string string?
                      :date date?
                      :boolean boolean?
                      :keyword keyword?
                      :uuid uuid?
                      :uri uri?
                      :bytes bytes?))

(s/def ::entity-id pos-int?)
(s/def ::lookup-ref (s/tuple ::attribute ::scalar))
(s/def ::tempid tempid?)


(s/def ::value (s/or :scalar ::scalar
                     :eid ::entity-id
                     :lookup-ref ::lookup-ref
                     :tempid ::tempid
                     :collection (s/coll-of ::value :min-count 1)))

(s/def ::list-txform (s/tuple
                       #{:db/add :db/retract}
                       (s/or :tempid ::tempid
                             :eid ::entity-id)
                       ::attribute
                       ::value))

(s/def ::fn-txform (s/cat :fn keyword?
                          :args (s/* any?)))

(s/def ::map-txform (s/map-of ::attribute (s/or :value ::value
                                                :nested ::map-txform)))

(s/def ::txdata (s/coll-of (s/or :list ::list-txform
                                 :fn ::fn-txform
                                 :map ::map-txform)
                                 :min-count 1))

(s/fdef arachne.core.config/init
  :args (s/cat :config ::config, :schema-txes (s/coll-of ::txdata :min-count 1))
  :ret ::config)

(s/fdef arachne.core.config/update
  :args (s/cat :config ::config, :txdata ::txdata)
  :ret ::config)


;; We could spec this much more strongly, but that would involve spec'ing the
;; entire query syntax. Task for another day.
(s/def ::find-expr (s/or :map map?
                         :vector vector?))

(s/fdef arachne.core.config/q
  :args (s/cat :config ::config,
               :find-expr ::find-expr,
               :sources (s/or :not-present nil?
                              :varargs coll?)))

;; We could spec this more strongly, but that would involve spec'ing the entire
;; pull syntax. Defer.
(s/def ::pull-expr vector?)

(s/fdef arachne.core.config/pull
  :args (s/cat :config ::config,
               :pull-expr ::pull-expr
               :entity-id (s/or :eid ::entity-id
                                :lookup-ref ::lookup-ref)))

(s/fdef arachne.core.config/resolve-tempid
  :args (s/cat :config ::config, :arachne-tempid tempid?)
  :ret pos-int?)

(s/def ::stack-filter-pred any?)

(s/fdef arachne.core.config/with-provenance
  :args (s/cat :source any?
               :function any?
               :options (s/keys* :opt-un [::stack-filter-pred])
               :body (s/* any?))
  :ret any?)
