(set-env! :repositories
  #(conj % ["arachne-dev" {:url "http://maven.arachne-framework.org/artifactory/arachne-dev"}]))

(set-env!
  :dependencies
  `[[org.arachne-framework/arachne-buildtools "0.3.2-master-0040-428be64" :scope "test"]])

(require '[arachne.buildtools :refer :all])

(read-project-edn!)