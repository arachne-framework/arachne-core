(set-env!
  :dependencies
  '[[org.arachne-framework/arachne-buildtools "0.1.0" :scope "test"]])

(require '[arachne.buildtools :refer :all])

(read-project! "project.edn")

(require '[adzerk.boot-test :refer [test]])
