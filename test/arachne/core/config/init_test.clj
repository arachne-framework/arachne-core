(ns arachne.core.config.init-test
  (:require [clojure.test :refer :all]
            [arachne.core :as core]
            [arachne.core.config :as cfg]
            [arachne.core.config.init :as init]))

(deftest basic-dsl
  (let [script '(do

                  (require '[arachne.core.config :as cfg])
                  (require '[arachne.core.config.init :as script])

                  (script/update
                    (fn [cfg]
                      (cfg/update cfg
                        [{:arachne/id :dsl-test-1
                          :arachne.runtime/components {:arachne/id :dsl-test-2}}
                         {:db/id (cfg/tempid :db.part/tx)
                          :arachne.transaction/source :test}])))
                  (script/update
                    (fn [cfg]
                      (cfg/update cfg
                        [{:arachne/id :dsl-test-2
                          :arachne.component/constructor :test/ctor}
                         {:db/id (cfg/tempid :db.part/tx)
                          :arachne.transaction/source :test}])))
                  (script/transact
                    [{:arachne/id :dsl-test-3}
                     {:db/id (cfg/tempid :db.part/tx)
                      :arachne.transaction/source :test}]))
        cfg (core/build-config '[:org.arachne-framework/arachne-core] script)]
    (is (not-empty
          (cfg/q cfg '[:find ?e
                       :where
                       [?e :arachne/id :dsl-test-1]])))
    (is (not-empty
          (cfg/q cfg '[:find ?e
                       :where
                       [?e :arachne/id :dsl-test-2]])))
    (is (not-empty
          (cfg/q cfg '[:find ?e
                       :where
                       [?e :arachne/id :dsl-test-3]])))))

(deftest throws-when-called-from-normal-code
  (is (thrown-with-msg? Throwable #"outside of a configuration"
        (init/update [{:db/id (arachne.core.config/tempid)
                      :arachne/id :dsl-test-1}]))))
