(ns arachne.log
  "Common logging layer for all Arachne apps and modules.

  Currently delegates to Pedestal logging, as that does pretty much everything we need. However,
  we will not use that directly, just in case anything changes, or we want to add additional
  features in the future.

  All log macros take vararg sequence of `keyvals`. Keyvals should be an alternating sequence of keywords and values to log. `str` will be called on the values.

  Using keyvals makes it easier to parse logs as data. Use a single :msg key with a string to replicate more traditional logging behavior.

  "
  (:require [io.pedestal.log :as log]))

(def log-expr @#'log/log-expr)

(defmacro trace [& keyvals] (log-expr &form :trace keyvals))
(defmacro debug [& keyvals] (log-expr &form :debug keyvals))
(defmacro info [& keyvals] (log-expr &form :info keyvals))
(defmacro warn [& keyvals] (log-expr &form :warn keyvals))
(defmacro error [& keyvals] (log-expr &form :error keyvals))


;; TODO: Support Pedetal metrics here as well
