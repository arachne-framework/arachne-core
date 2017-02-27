;; Sample of what the new DSL could look like
(config
  (:require
   [arachne.core.dsl :as a]
   [arachne.http.dsl :as http]
   [arachne.pedestal.dsl :as pedestal]
   [arachne.assets.dsl :as asset]
   [arachne.pedestal-assets.dsl :as pedestal-asset]
   [arachne.cljs.dsl :as cljs]
   [arachne.figwheel.dsl :as fig]))

;; Make things shorter to type...
(alias 'app 'org.arachne-framework.template.enterprise-spa)

;; Always in dev mode, for now
(def dev? (constantly true))

;; Runtime setup
(a/id ::app/runtime (a/runtime [::app/figwheel ::app/server]))

;; HTTP Server setup
(a/id ::app/server
  (pedestal/server 8080

    (a/id ::app/asset-interceptor (pedestal-asset/interceptor :index? true))

    (http/endpoint :get "/healthcheck"
      (http/handler 'org.arachne-framework.template.enterprise-spa.web/healthcheck))

    ))

;; Asset Pipeline setup
(a/id ::app/public-dir (asset/input-dir "public" :classpath? true :watch? (dev?)))

(asset/pipeline [::app/public-dir ::app/asset-interceptor])

(def cljs-opts {:main 'org.arachne-framework.template.enterprise-spa
                :optimizations (if (dev?) :none :advanced)
                :asset-path "js/out"
                :output-to "js/app.js"
                :output-dir "js/out"
                :source-map-timestamp true})

(a/id ::app/src-dir (asset/input-dir  "src" :watch? (dev?)))

;; For prod mode, use a standard CLJS build pipeline
(a/id ::app/cljs (cljs/build cljs-opts))
(asset/pipeline [::app/src-dir ::app/cljs])

;; Figwheel ClojureScript setup (dynamic CLJS development)
(a/id ::app/figwheel (fig/server cljs-opts :port 8888))

(asset/pipeline
  [::app/src-dir ::app/figwheel #{:src}]
  [::app/public-dir ::app/figwheel #{:public}])

;; Always use Figwheel for builds in dev
(if (dev?)
  (asset/pipeline [::app/figwheel ::app/asset-interceptor])
  (asset/pipeline [::app/cljs ::app/asset-interceptor]))