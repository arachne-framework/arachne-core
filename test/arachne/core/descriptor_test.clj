(ns arachne.core.descriptor-test
  (:require [clojure.test :refer :all]
            [clojure.set :as set]
            [arachne.core.descriptor :as d]
            [arachne.aristotle.registry :as reg]))

(reg/prefix)

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

    (is (= #{["test1" 0 3]
             ["test2" 1 1]}
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


    (d/query d '[:bgp
                 [?tx :arachne.descriptor.tx/provenance ?p1]
                 [?p1 :arachne.provenance/parent ?p2]
                 [?p1 :arachne.provenance/module ?m1]
                 [?p2 :arachne.provenance/module ?m2]
                 ;; TODO: pause until I can get pull working :)
                 ]

             )


    )


  )

