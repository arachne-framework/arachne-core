(ns arachne.core.util.specs
  (:require [clojure.spec.alpha :as s]))

(s/fdef arachne.core.util/require-and-resolve
  :args (s/cat :symbol (s/or :string string?
                             :symbol qualified-symbol?
                             :keyword qualified-keyword?)))
