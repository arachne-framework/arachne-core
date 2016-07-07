(ns arachne.core.modules-test
  (:require [clojure.test :refer :all]
            [clojure.set :as set]
            [arachne.core.modules :as m]))

(def sample-good
  '#{{:arachne.module/name         test.one
      :arachne.module/constructor  test.one/const
      :arachne.module/dependencies [test.two test.three]}
     {:arachne.module/name         test.two
      :arachne.module/constructor  test.two/const
      :arachne.module/dependencies [test.three]}
     {:arachne.module/name        test.three
      :arachne.module/constructor test.three/const}})

(def sample-dup
  '#{{:arachne.module/name         test.one
      :arachne.module/constructor  test.one/const
      :arachne.module/dependencies [test.two]}
     {:arachne.module/name         test.one
      :arachne.module/constructor  test.one/const2
      :arachne.module/dependencies [test.two]}})

(def sample-cycles
  '#{{:arachne.module/name         test.one
      :arachne.module/constructor  test.one/const
      :arachne.module/dependencies [test.two]}
     {:arachne.module/name         test.two
      :arachne.module/constructor  test.two/const
      :arachne.module/dependencies [test.three]}
     {:arachne.module/name         test.three
      :arachne.module/constructor  test.three/const
      :arachne.module/dependencies [test.one]}})

(def sample-missing
  '#{{:arachne.module/name         test.one
      :arachne.module/constructor  test.one/const
      :arachne.module/dependencies [test.two]}
     {:arachne.module/name         test.two
      :arachne.module/constructor  test.two/const
      :arachne.module/dependencies [test.three]}
     {:arachne.module/name         test.three
      :arachne.module/constructor  test.three/const
      :arachne.module/dependencies [test.four]}})


(deftest validate-missing-modules
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"not found"
        (@#'m/validate-dependencies sample-missing)))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"definitions"
        (@#'m/validate-dependencies sample-dup)))
  (is (nil? (@#'m/validate-dependencies sample-good))))

(deftest validate-no-cycles
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"circular"
        (@#'m/topological-sort sample-cycles)))
  (is (sequential? (@#'m/topological-sort sample-good))))

(deftest validate-missing-root
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"could not be found"
        (@#'m/reachable sample-good ['test-four]))))

(def sample-reachable
  (set/union sample-good
    '#{{:arachne.module/name test.ten
        :arachne.module/constructor test.ten/const
        :arachne.module/dependencies [test.eleven]}
       {:arachne.module/name test.eleven
        :arachne.module/constructor test.eleven/const}}))

(deftest validate-reachability
  (is (= (set (@#'m/reachable sample-reachable '[test.one]))
          (set sample-good)))
  (is (= (set (@#'m/reachable sample-reachable '[test.one test.ten]))
          (set sample-reachable))))


(deftest should-fail
  (is (= 4 (+ 2 2)) "it isn't lol"))

