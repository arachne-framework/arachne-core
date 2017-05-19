(ns arachne.core.runtime-test
  (:require [clojure.test :refer :all]
            [arachne.core :as core]
            [arachne.core.config :as cfg]
            [arachne.core.runtime :as rt]
            [com.stuartsierra.component :as component]
            [arachne.core.util :as util]))

(arachne.error/explain-test-errors!)

(def basic-config
  [{:db/id (cfg/tempid)
    :arachne/id :test-rt
    :arachne.runtime/components {:arachne.component/constructor :arachne.core.runtime-test/basic-dep-0
                                 :arachne/id :test-1}}

   {:db/id (cfg/tempid :db.part/tx)
    :arachne.transaction/source :test}])

(defn setup
  "Set up a test runtime using the given initial txdata and the ID of the
  runtime"
  [init rt-id]
  (rt/init
    (core/build-config '[:org.arachne-framework/arachne-core] init)
    [:arachne/id rt-id]))

(deftest basic-dependencies
  (let [rt (setup basic-config :test-rt)
        cfg (:config rt)
        instances (:system rt)]
    (is (= 1 (count instances)))
    (is (= (:db/id (cfg/pull cfg [:db/id] [:arachne/id :test-1]))
          (first (keys instances))))))

(defn basic-dep-0
  [config eid]
  {:this-is-dep-0 true})

(defn basic-dep-1
  [config eid]
  {:this-is-dep-1 true})

(defn basic-dep-2
  [config eid]
  {:this-is-dep-2 true})

(defn basic-dep-3
  [config eid]
  {:this-is-dep-3 true})

(def linear-config
  (let [root (cfg/tempid)
        dep-1 (cfg/tempid -1)
        dep-2 (cfg/tempid -2)]
    [
     {:db/id (cfg/tempid)
      :arachne/id :test-rt
      :arachne.runtime/components #{root}}

     {:db/id root
      :arachne/id :test-2
      :arachne.component/constructor :arachne.core.runtime-test/basic-dep-0
      :arachne.component/dependencies
      #{{:arachne.component.dependency/key :dep-1
         :arachne.component.dependency/entity dep-1}}}

     {:db/id dep-1
      :arachne.component/constructor :arachne.core.runtime-test/basic-dep-1
      :arachne.component/dependencies
      #{{:arachne.component.dependency/key :dep-2
         :arachne.component.dependency/entity dep-2}}}

     {:db/id dep-2
      :arachne.component/constructor :arachne.core.runtime-test/basic-dep-2}

     {:db/id (cfg/tempid -3)
      :arachne.component/constructor :arachne.core.runtime-test/basic-dep-3}

     {:db/id (cfg/tempid :db.part/tx)
      :arachne.transaction/source :test}

     ]))

(deftest linear-dependencies
  (let [rt (setup linear-config :test-rt)
        cfg (:config rt)
        instances (into {} (:system rt))
        [root-id d1-eid d2-eid]
        (cfg/q cfg '[:find [?root ?de1 ?de2]
                     :where
                     [?root :arachne/id :test-2]
                     [?root :arachne.component/dependencies ?d1]
                     [?d1 :arachne.component.dependency/entity ?de1]
                     [?de1 :arachne.component/dependencies ?d2]
                     [?d2 :arachne.component.dependency/entity ?de2]])]
    (is (= 3 (count instances)))
    (is (instances root-id))
    (let [started-rt (component/start rt)
          instances (into {} (:system started-rt))]
      (is (:this-is-dep-1 (instances d1-eid)))
      (is (:this-is-dep-1 (:dep-1 (instances root-id))))
      (is (:this-is-dep-2 (instances d2-eid)))
      (is (:this-is-dep-2 (:dep-2 (instances d1-eid)))))))

(def diamond-config
  (let [dep-1 (cfg/tempid -1)
        dep-2 (cfg/tempid -2)
        dep-3 (cfg/tempid -3)
        root (cfg/tempid)]
    [{:db/id (cfg/tempid)
      :arachne/id :test-rt
      :arachne.runtime/components #{root}}

     {:db/id root
      :arachne/id :test-3
      :arachne.component/constructor :arachne.core.runtime-test/basic-dep-0
      :arachne.component/dependencies
      #{{:arachne.component.dependency/key :dep-1
         :arachne.component.dependency/entity dep-1}
        {:arachne.component.dependency/key :dep-2
         :arachne.component.dependency/entity dep-2}}}

     {:db/id dep-1
      :arachne.component/constructor :arachne.core.runtime-test/basic-dep-1
      :arachne.component/dependencies
      #{{:arachne.component.dependency/key :dep-2
         :arachne.component.dependency/entity dep-2}
        {:arachne.component.dependency/key :dep-3
         :arachne.component.dependency/entity dep-3}}}

     {:db/id dep-2
      :arachne.component/constructor :arachne.core.runtime-test/basic-dep-2
      :arachne.component/dependencies
      #{{:arachne.component.dependency/key :dep-3
         :arachne.component.dependency/entity dep-3}}}

     {:db/id dep-3
      :arachne.component/constructor :arachne.core.runtime-test/basic-dep-3}

     {:db/id (cfg/tempid :db.part/tx)
      :arachne.transaction/source :test}]))

(deftest complex-dependencies
  (let [rt (setup diamond-config :test-rt)
        cfg (:config rt)
        instances (into {} (:system rt))
        [root-eid d1-eid d2-eid d3-eid]
        (cfg/q cfg '[:find [?root ?de1 ?de2 ?de3]
                     :in $ ?c1 ?c2 ?c3
                     :where
                     [?root :arachne/id :test-3]
                     [?root :arachne.component/dependencies ?d1]
                     [?root :arachne.component/dependencies ?d2]
                     [?d1 :arachne.component.dependency/entity ?de1]
                     [?d2 :arachne.component.dependency/entity ?de2]
                     [?de1 :arachne.component/dependencies ?d3a]
                     [?de2 :arachne.component/dependencies ?d3b]
                     [?d3a :arachne.component.dependency/entity ?de3]
                     [?d3b :arachne.component.dependency/entity ?de3]
                     [?de1 :arachne.component/constructor ?c1]
                     [?de2 :arachne.component/constructor ?c2]
                     [?de3 :arachne.component/constructor ?c3]]
          :arachne.core.runtime-test/basic-dep-1
          :arachne.core.runtime-test/basic-dep-2
          :arachne.core.runtime-test/basic-dep-3)]
    (is (= 4 (count instances)))
    (is (instances root-eid))
    (is (:this-is-dep-1 (instances d1-eid)))
    (is (instances d2-eid))
    (is (:this-is-dep-2 (instances d2-eid)))
    (is (:this-is-dep-3 (instances d3-eid)))
    (let [started-rt (component/start rt)
          instances (into {} (:system started-rt))]
      (is (-> root-eid (instances) :dep-1 :dep-2 :dep-3 :this-is-dep-3))
      (is (-> root-eid (instances) :dep-2 :dep-3 :this-is-dep-3))
      (testing "dep-instance utility function"
        (is (= (instances d1-eid)
              (get (instances root-eid) d1-eid)))
        (is (= (instances d3-eid)
              (get (instances d2-eid) d3-eid)))))))


(def ^:dynamic *tracker* nil)

(defrecord LifecycleRecord [id started]
  component/Lifecycle
  (start [this]
    (when *tracker* (swap! *tracker* conj [:start id]))
    (assoc this :started true))
  (stop [this]
    (when *tracker* (swap! *tracker* conj [:stop id]))
    (assoc this :started false)))

(defn construct-lifecycle
  [config eid]
  (assoc (->LifecycleRecord nil false)
    :id (:arachne/id (cfg/pull config [:arachne/id] eid))))

(def basic-lifecycle-cfg
  (let [a (cfg/tempid -1)
        b (cfg/tempid -2)
        c (cfg/tempid -3)]
    [{:db/id (cfg/tempid)
      :arachne/id :test-rt
      :arachne.runtime/components #{a}}
     {:db/id a
      :arachne/id :test/a
      :arachne.component/constructor
      :arachne.core.runtime-test/construct-lifecycle,
      :arachne.component/dependencies
      #{{:arachne.component.dependency/entity b}}},
     {:db/id b
      :arachne/id :test/b
      :arachne.component/constructor
      :arachne.core.runtime-test/construct-lifecycle}
     {:db/id c
      :arachne/id :test/c
      :arachne.component/constructor
      :arachne.core.runtime-test/construct-lifecycle}
     {:db/id (cfg/tempid :db.part/tx)
      :arachne.transaction/source :test}]))

(deftest basic-lifecycle
  (let [rt (setup basic-lifecycle-cfg :test-rt)
        instances (into {} (:system rt))]
    (is (= 2 (count instances)))
    (is (every? #(satisfies? component/Lifecycle %) (vals instances)))
    (is (every? #(false? (:started %)) (vals instances)))
    (binding [*tracker* (atom [])]
      (let [rt (component/start rt)
            instances (:instances rt)]
        (is (every? #(true? (:started %)) (vals instances)))
        (is (= [[:start :test/b], [:start :test/a]]
                      @*tracker*))
        (let [rt (component/stop rt)
              instances (:instances rt)]
          (is (every? #(false? (:started %)) (vals instances)))
          (is (= [[:start :test/b],
                  [:start :test/a],
                  [:stop :test/a],
                  [:stop :test/b]]
                @*tracker*)))))))
