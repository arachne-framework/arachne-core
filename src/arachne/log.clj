(ns arachne.log
  "Common logging layer for all Arachne apps and modules.

  Currently delegates to Pedestal logging, as that does pretty much everything we need. However,
  we will not use that directly, just in case anything changes, or we want to add additional
  features in the future."
  (:require [io.pedestal.log :as log]))

(defmacro trace [& keyvals] `(log/trace ~@keyvals))
(defmacro debug [& keyvals] `(log/debug ~@keyvals))
(defmacro info [& keyvals] `(log/info ~@keyvals))
(defmacro warn [& keyvals] `(log/warn ~@keyvals))
(defmacro error [& keyvals] `(log/error ~@keyvals))

;; TODO: Support Pedetal metrics here as well
