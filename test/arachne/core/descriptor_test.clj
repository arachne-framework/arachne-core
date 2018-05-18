(ns arachne.core.descriptor-test
  (:require [clojure.test :refer :all]
            [clojure.set :as set]
            [arachne.core.descriptor :as d]
            [arachne.aristotle.registry :as reg]))

(deftest adding-data-with-metadata
  (let [d (d/new)]

    (d/update! d {:rdf/about ::luke
                  ::name "Luke"
                  ::age 32
                  ::eyes "blue"}
               [{:rdf/about '_tx
                  :arachne.descriptor.tx/type "test1"}])
    (d/update! d {:rdf/about ::jim
                  ::name "Jim"}
               [{:rdf/about '_tx
                  :arachne.descriptor.tx/type "test2"}])

    (is (= #{["test1" 1 3]
             ["test2" 2 1]}
           (d/query d '[?type ?idx ?count]
                    '[:group [?tx ?type ?idx] [?count (count (distinct ?stmt))]
                      [:bgp
                       [?tx :arachne.descriptor.tx/type ?type]
                       [?tx :arachne.descriptor.tx/index ?idx]
                       [?stmt :arachne.descriptor/tx ?tx]]])))))

(deftest with-provenance-macro

  (let [d (d/new)]

    (d/with-provenance `foo
      :stack-filter-pred #(re-find #"core.clj" (.getFileName %))

      (d/with-provenance `foobar
        (d/update! d {:rdf/about ::luke
                      ::name "Luke"})))

    (let [[[p]] (seq (d/query d '[?p]
                         '[:bgp
                           [?s :rdf/subject ::luke]
                           [?s :arachne.descriptor/tx ?tx]
                           [?tx :arachne.descriptor.tx/provenance ?p]]))
          prov (d/pull d p '[* {:arachne.provenance/parent ...
                                :arachne.provenance/stack-frame [*]}])]
      (clojure.pprint/pprint prov)
      (is (-> prov :arachne.provenance/function (= `foobar)))
      (is (-> prov :arachne.provenance/parent :arachne.provenance/function (= `foo)))
      (is (-> prov :arachne.provenance/parent :arachne.provenance/stack-frame
            :arachne.stack-frame/source-file (= "core.clj"))))))

