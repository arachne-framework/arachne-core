(set-env! :repositories
  #(conj % ["arachne-dev" {:url "http://maven.arachne-framework.org/artifactory/arachne-dev"}]))

(set-env!
  :dependencies
  `[[org.arachne-framework/arachne-buildtools "0.2.6-master-0035-f3c36a5" :scope "test"]])

(require '[arachne.buildtools :refer :all])

(read-project-edn!)