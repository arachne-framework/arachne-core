(set-env! :repositories
  #(conj % ["arachne-dev" {:url "http://maven.arachne-framework.org/artifactory/arachne-dev"}]))

(set-env!
  :dependencies
  `[[org.arachne-framework/arachne-buildtools "0.2.4-master-0028-7a76bc6" :scope "test"]])

(require '[arachne.buildtools :refer :all])

(read-project-edn!)