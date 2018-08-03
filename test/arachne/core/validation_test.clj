(ns arachne.core.validation-test
  (:require [clojure.test :refer :all]
            [clojure.set :as set]
            [arachne.core :as a]
            [arachne.core.descriptor :as d]
            [arachne.aristotle.graph :as g]
            [arachne.aristotle.registry :as reg]))

(reg/prefix :test "http://example.com/test/")

(arachne.error/explain-test-errors!)

(deftest inference-errors
  (testing "Min-card violation"
    (is (thrown-with-msg? arachne.ArachneException #"violation"
          (a/descriptor :org.arachne-framework/arachne-core
            [{:rdf/about :test/rt
              :arachne.runtime/components :test/c1}
             {:rdf/about :test/c1
              :test/some-attr 32}]
            true))))
  (testing "Max-card violation (reported as multiple)"
    (is (thrown-with-msg? arachne.ArachneException #"2 errors while validating"
          (a/descriptor :org.arachne-framework/arachne-core
            [{:rdf/about :test/rt
              :arachne.runtime/components :test/c1}
             {:rdf/about :test/c1
              :arachne.component/constructor #{'foo.bar/baz 'foo.biz/buz}
              :test/some-attr 32}] true)))))
