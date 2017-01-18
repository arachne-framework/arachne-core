(ns arachne.core.module-test
  (:require [clojure.test :refer :all]
            [clojure.set :as set]
            [arachne.core.module :as m]))

(def sample-good
  '#{{:arachne/name         test.one
      :arachne/constructor  test.one/const
      :arachne/dependencies [test.two test.three]}
     {:arachne/name         test.two
      :arachne/constructor  test.two/const
      :arachne/dependencies [test.three]}
     {:arachne/name        test.three
      :arachne/constructor test.three/const}})

(def sample-dup
  '#{{:arachne/name         test.one
      :arachne/constructor  test.one/const
      :arachne/dependencies [test.two]}
     {:arachne/name         test.one
      :arachne/constructor  test.one/const2
      :arachne/dependencies [test.two]}})

(def sample-cycles
  '#{{:arachne/name         test.one
      :arachne/constructor  test.one/const
      :arachne/dependencies [test.two]}
     {:arachne/name         test.two
      :arachne/constructor  test.two/const
      :arachne/dependencies [test.three]}
     {:arachne/name         test.three
      :arachne/constructor  test.three/const
      :arachne/dependencies [test.one]}})

(def sample-missing
  '#{{:arachne/name         test.one
      :arachne/constructor  test.one/const
      :arachne/dependencies [test.two]}
     {:arachne/name         test.two
      :arachne/constructor  test.two/const
      :arachne/dependencies [test.three]}
     {:arachne/name         test.three
      :arachne/constructor  test.three/const
      :arachne/dependencies [test.four]}})


(deftest validate-missing-modules
  (is (thrown-with-msg? arachne.ArachneException #"not found"
        (@#'m/validate-dependencies sample-missing)))
  (is (thrown-with-msg? arachne.ArachneException #"Duplicate"
        (@#'m/validate-dependencies sample-dup)))
  (is (nil? (@#'m/validate-dependencies sample-good))))

(deftest validate-no-cycles
  (is (thrown-with-msg? arachne.ArachneException #"circular"
        (@#'m/topological-sort sample-cycles)))
  (is (sequential? (@#'m/topological-sort sample-good))))

(def sample-reachable
  (set/union sample-good
    '#{{:arachne/name test.ten
        :arachne/constructor test.ten/const
        :arachne/dependencies [test.eleven]}
       {:arachne/name test.eleven
        :arachne/constructor test.eleven/const}}))

(deftest validate-reachability
  (is (= (set (@#'m/reachable sample-reachable '{:arachne/name test.one}))
          (set sample-good))))


(deftest should-fail
  (is (= 4 (+ 2 2)) "it isn't lol"))

