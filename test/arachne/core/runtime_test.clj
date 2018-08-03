(ns arachne.core.runtime-test
  (:require [arachne.core :as a]
            [arachne.core.descriptor :as d]
            [arachne.aristotle.registry :as reg]
            [clojure.test :refer :all]
            [arachne.core.runtime :as rt]
            [com.stuartsierra.component :as component]
            [arachne.core.util :as util]))

(reg/prefix :test "http://example.com/test/")

(def ^:dynamic *tracker* nil)
(defrecord SimpleComponent [id started]
  component/Lifecycle
  (start [this]
    (when *tracker* (swap! *tracker* conj [:start id]))
    (assoc this :started true))
  (stop [this]
    (when *tracker* (swap! *tracker* conj [:stop id]))
    (assoc this :started false)))

(defn c1 [descriptor iri] (->SimpleComponent :this-is-c1 false))
(defn c2 [] (->SimpleComponent :this-is-c2 false))
(defn c3 [entity-map] (->SimpleComponent :this-is-c3 false))
(defn c4 [] (->SimpleComponent :this-is-c4 false))

(arachne.error/explain-test-errors!)

(def basic-descriptor
  [{:rdf/about :test/rt
    :arachne.runtime/components {:rdf/about :test/c1
                                 :test/foo 32
                                 :arachne.component/constructor `c1}}])

(deftest basic-dependencies
  (let [d (a/descriptor :org.arachne-framework/arachne-core basic-descriptor)
        rt (a/runtime d :test/rt)
        instances (:system rt)]
    (is (= 1 (count instances)))
    ;; Note: It comes back as a set because we never said it was cardinality one
    (is (= #{32} (:test/foo (rt/lookup rt :test/c1))))
    (is (not (:started (rt/lookup rt :test/c1))))
    (is (= :this-is-c1 (:id (rt/lookup rt :test/c1))))
    (let [started (component/start rt)]
      (is (:started (rt/lookup started :test/c1)))
      (is (= :this-is-c1 (:id (rt/lookup started :test/c1)))))))

(def linear-descriptor
  [{:rdf/about :test/rt
    :arachne.runtime/components :test/c1}

   {:rdf/about :test/c1
    :arachne.component/constructor `c1
    :arachne.component/dependencies
    #{{:arachne.component.dependency/key ":c2"
       :arachne.component.dependency/entity :test/c2}}}

   {:rdf/about :test/c2
    :arachne.component/constructor `c2
    :arachne.component/dependencies
    #{{:arachne.component.dependency/key ":c3"
       :arachne.component.dependency/entity :test/c3}}}

   {:rdf/about :test/c3
    :arachne.component/constructor `c3}

   {:rdf/about :test/c4
    :arachne.component/constructor `c4}])

(deftest linear-dependencies
  (let [d (a/descriptor :org.arachne-framework/arachne-core linear-descriptor)
        rt (a/runtime d :test/rt)
        rt (component/start rt)
        instances (:system rt)]
    (is (= 3 (count instances)))
    (is (:test/c1 instances))
    (is (:test/c2 instances))
    (is (:test/c3 instances))
    (is (not (:test/c4 instances)))
    (is (= (:test/c2 instances) (:c2 (:test/c1 instances))))
    (is (= (:test/c3 instances) (:c3 (:test/c2 instances))))))

(def complex-descriptor
  [{:rdf/about :test/rt
    :arachne.runtime/components :test/c1}

   {:rdf/about :test/c1
    :arachne.component/constructor `c1
    :arachne.component/dependencies
    #{{:arachne.component.dependency/key ":c2"
       :arachne.component.dependency/entity :test/c2}
      {:arachne.component.dependency/key ":c3"
       :arachne.component.dependency/entity :test/c3}}}

   {:rdf/about :test/c2
    :arachne.component/constructor `c2
    :arachne.component/dependencies
    #{{:arachne.component.dependency/key ":c3"
       :arachne.component.dependency/entity :test/c3}
      {:arachne.component.dependency/key ":c4"
       :arachne.component.dependency/entity :test/c4}}}

   {:rdf/about :test/c3
    :arachne.component/constructor `c3
    :arachne.component/dependencies
    #{{:arachne.component.dependency/key ":c4"
       :arachne.component.dependency/entity :test/c4}}}

   {:rdf/about :test/c4
    :arachne.component/constructor `c4}])

(deftest complex-dependencies
  (let [d (a/descriptor :org.arachne-framework/arachne-core complex-descriptor)
        rt (a/runtime d :test/rt)
        instances (:system rt)]
    (is (= 4 (count instances)))
    (is (:test/c1 instances))
    (is (:test/c2 instances))
    (is (:test/c3 instances))
    (is (:test/c4 instances))
    (let [rt (component/start rt)]
      (is (-> rt :system :test/c1 :c2 :c4 :id (= :this-is-c4)))
      (is (-> rt :system :test/c1 :c2 :c3 :id (= :this-is-c3))))))

(deftest basic-lifecycle
  (let [d (a/descriptor :org.arachne-framework/arachne-core linear-descriptor)
        rt (a/runtime d :test/rt)
        instances (:system rt)]
    (is (= 3 (count instances)))
    (is (every? #(satisfies? component/Lifecycle %) (vals instances)))
    (is (every? #(false? (:started %)) (vals instances)))
    (binding [*tracker* (atom [])]
      (let [rt (component/start rt)
            instances (:system rt)]
        (is (every? #(true? (:started %)) (vals instances)))
        (is (= [[:start :this-is-c3],
                [:start :this-is-c2],
                [:start :this-is-c1]] @*tracker*))
        (let [rt (component/stop rt)
              instances (:system rt)]
          (is (every? #(false? (:started %)) (vals instances)))
          (is (= [[:start :this-is-c3],
                  [:start :this-is-c2],
                  [:start :this-is-c1]
                  [:stop :this-is-c1]
                  [:stop :this-is-c2]
                  [:stop :this-is-c3]]
                @*tracker*)))))))
