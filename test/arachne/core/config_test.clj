(ns arachne.core.config-test
  (:require [clojure.test :refer :all]
            [clojure.set :as set]
            [arachne.core.module :as m]
            [arachne.core.config :as cfg]
            [arachne.core.config.impl.multiplex :as impl]))

(def s1 [{:db/id (cfg/tempid :db.part/db)
                     :db/ident :test/basic
                     :db/cardinality :db.cardinality/one
                     :db/valueType :db.type/string
                     :db.install/_attribute :db.part/db}])


(def s2 [{:db/id (cfg/tempid :db.part/db)
                     :db/ident :test/card-many
                     :db/cardinality :db.cardinality/many
                     :db/valueType :db.type/string
                     :db.install/_attribute :db.part/db}])

(def s3 [{:db/id (cfg/tempid :db.part/db)
                     :db/ident :test/ref
                     :db/cardinality :db.cardinality/one
                     :db/valueType :db.type/ref
                     :db.install/_attribute :db.part/db}

                    {:db/id (cfg/tempid :db.part/db)
                     :db/ident :test/component-ref
                     :db/cardinality :db.cardinality/one
                     :db/valueType :db.type/ref
                     :db/isComponent true
                     :db.install/_attribute :db.part/db}
                    ])


(def s4 [{:db/id (cfg/tempid :db.part/db)
                     :db/ident :test/ref-card-many
                     :db/cardinality :db.cardinality/many
                     :db/valueType :db.type/ref
                     :db.install/_attribute :db.part/db}])

(def s5 [{:db/id (cfg/tempid :db.part/db)
                     :db/ident :test/identity
                     :db/cardinality :db.cardinality/one
                     :db/valueType :db.type/string
                     :db/unique :db.unique/identity
                     :db.install/_attribute :db.part/db}])

(def s6 [{:db/id (cfg/tempid :db.part/db)
                     :db/ident :test/unique
                     :db/cardinality :db.cardinality/one
                     :db/valueType :db.type/string
                     :db/unique :db.unique/value
                     :db.install/_attribute :db.part/db}])


(defn- setup
  []
  (cfg/init (impl/new) [(arachne.core/schema) s1 s2 s3 s4 s5 s6]))

(deftest schema-loads-correctly
  (let [cfg (setup)
        attrs #{:test/basic :test/card-many :test/ref
                :test/ref-card-many :test/identity :test/unique}]

    (is (= (set (cfg/q cfg '[:find [?attr ...]
                     :in $ [?attr ...]
                     :where [_ :db/ident ?attr]] attrs))
          attrs))))


;; Run some spot checks on datalog semantics...

(defn test-update
  "Update with provenance metadata in place"
  [cfg txdata]
  (cfg/with-provenance :test `test-update
    (cfg/update cfg txdata)))

(deftest multiplex-schema-elements
  (let [cfg (setup)]
    (is (= {:db/ident :arachne/Component}
          (cfg/pull cfg '[:db/ident] [:db/ident :arachne/Component])))
    (is (cfg/pull cfg '[*] [:db/ident :arachne.component/constructor]))))

(deftest refs-work-correctly
  (let [cfg (setup)
        cfg (test-update cfg [{:test/basic "Hello"
                               :test/ref   {:test/basic "world"}}
                              {:test/basic "booleans"
                               :test/ref-card-many
                               #{{:test/basic "true"}
                                 {:test/basic "false"}}}])]
    (testing "cardinality-one refs"
          (is (= #{["Hello" "world"]}
                (cfg/q cfg '[:find ?parent-val ?child-val
                             :where
                             [?parent :test/basic ?parent-val]
                             [?parent :test/ref ?child]
                             [?child :test/basic ?child-val]]))))
    (testing "cardinality-many refs"
          (is (= #{["booleans" "true"]
                   ["booleans" "false"]}
                (cfg/q cfg '[:find ?parent-val ?child-val
                             :where
                             [?parent :test/basic ?parent-val]
                             [?parent :test/ref-card-many ?child]
                             [?child :test/basic ?child-val]]))))))

(deftest component-refs-work-correctly
  (let [cfg (setup)
        cfg (test-update cfg [{:test/identity "ident"
                               :test/basic "Hello"
                               :test/component-ref {:test/basic "World"}}])]
    (is (= "World"
          (-> (cfg/pull cfg '[*] [:test/identity "ident"])
            :test/component-ref
            :test/basic)))))


(deftest identity-works-correctly
  (let [cfg (setup)
        cfg (test-update cfg [{:test/identity  "ident"
                              :test/card-many "foo"}
                             {:test/identity  "ident"
                              :test/card-many "bar"}])]
    (is (= #{"foo" "bar"}
          (set (cfg/q  cfg '[:find [?val ...]
                             :where
                             [?e :test/identity "ident"]
                             [?e :test/card-many ?val]]))))))
(deftest uniqueness
  (let [cfg (setup)
        cfg (test-update cfg [{:test/unique "unique"}])]
    (is (thrown-with-msg? Throwable #"while updating"
          (test-update cfg [{:test/unique "unique"}])))))

(deftest pull
  (let [cfg (setup)
        cfg (test-update cfg [{:test/identity "my identity"
                              :test/basic "Hello"
                              :test/ref   {:test/basic "world"}}])]
      (is (= {:test/basic "Hello"}
            (cfg/pull cfg [:test/basic] [:test/identity "my identity"])))
      (is (= {:test/ref {:test/basic "world"}}
            (cfg/pull cfg [{:test/ref [:test/basic]}]
                          [:test/identity "my identity"])))))

(deftest lookup-refs-in-txdata
  (let [cfg (setup)
        cfg (test-update cfg [{:test/basic "a-value"
                              :test/identity "a"}])
        cfg (test-update cfg [{:test/identity "b"
                              :test/basic "b-value"
                              :test/ref [:test/identity "a"]}])]
    (is (= #{["a-value" "b-value"]}
          (cfg/q cfg '[:find ?a-value ?b-value
                       :where
                       [?a :test/identity "a"]
                       [?b :test/identity "b"]
                       [?b :test/ref ?a]
                       [?a :test/basic ?a-value]
                       [?b :test/basic ?b-value]])))))

(deftest explicit-tempids-unify
  (let [cfg (setup)
        cfg (test-update cfg [{:db/id (cfg/tempid -42)
                              :test/identity "a"
                              :test/card-many "a"}
                             {:db/id (cfg/tempid -42)
                              :test/card-many "b"}])]
    (is (= #{"a" "b"}
          (set (:test/card-many (cfg/pull cfg [:test/card-many]
                                              [:test/identity "a"])))))))

(deftest tempid-instances-are-consistent
  (let [cfg (setup)
        tempid (cfg/tempid)
        cfg (test-update cfg [{:db/id tempid
                              :test/identity "a"
                              :test/card-many "a"}
                             {:db/id tempid
                              :test/card-many "b"}])]
    (is (= #{"a" "b"}
          (set (:test/card-many (cfg/pull cfg [:test/card-many]
                                              [:test/identity "a"])))))))

(deftest tempid-resolution
  (let [cfg (setup)
        tempid-1 (cfg/tempid)
        tempid-2 (cfg/tempid)
        cfg (test-update cfg [{:db/id tempid-1
                              :test/card-many "a"}
                             {:db/id tempid-2
                              :test/card-many "b"}])
        eid-1 (cfg/resolve-tempid cfg tempid-1)
        eid-2 (cfg/resolve-tempid cfg tempid-2)]
    (is (= #{"a"} (set (:test/card-many
                         (cfg/pull cfg [:test/card-many] eid-1)))))
    (is (= #{"b"} (set (:test/card-many
                         (cfg/pull cfg [:test/card-many] eid-2)))))))